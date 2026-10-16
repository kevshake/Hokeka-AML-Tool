package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspDataSettingChange;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.PspDataSettingChangeRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.residency.EdgeConfigDocumentBuilder;
import com.posgateway.aml.service.residency.EdgeConfigService;
import com.posgateway.aml.service.residency.EdgeConfigVersionStore;
import com.posgateway.aml.service.residency.EdgeTunnelSettingsService;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real R3 → R4 chain in a Spring context with transaction management and transactional event
 * listeners (repositories, keys and the tunnel transport are test doubles):
 *
 * <pre>
 * PspDataSettingsService.proposeSupportAccess(false)          (@Transactional, R3)
 *   → psp.support_access_enabled = false, PspDataSettingsChangedEvent(SUPPORT_ACCESS)
 *   → after commit: TunnelResidencyEventListener.onSettingsChanged → EdgeDataGateway.revokeSupportAccess
 *   → after commit: EdgeConfigService.onSettingsChanged → mints configVersion 2 → EdgeConfigUpdatedEvent
 *     → after that commit: TunnelResidencyEventListener.onConfigUpdated
 *       → broadcast CONFIG_CHANGED + notify config.updated {configVersion: 2}
 * </pre>
 *
 * and the tunnel's settings provider then answers from config version 2.
 */
class SupportAccessWithdrawalChainTest {

    static final TunnelNotifier NOTIFIER = mock(TunnelNotifier.class);
    static final TunnelMessaging MESSAGING = mock(TunnelMessaging.class);
    static final EdgeDataGateway GATEWAY = mock(EdgeDataGateway.class);
    static final Psp PSP = new Psp();
    static final List<PspDataSettingChange> CHANGES = new ArrayList<>();

    private AnnotationConfigApplicationContext ctx;

    /**
     * Transaction manager without a resource: begins, commits and rolls back only the transaction
     * synchronisation, which is all the event timing depends on. Deliberate test double. intentional-stub
     */
    static final class SynchronizationOnlyTransactionManager extends AbstractPlatformTransactionManager {

        /** A fresh transaction object per begin (no resource to hold). */
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        /** Nothing to bind: the test double has no connection. intentional-stub */
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource; synchronisation is managed by AbstractPlatformTransactionManager.
        }

        /** Nothing to flush: the repositories are in-memory doubles. intentional-stub */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No resource; afterCommit/afterCompletion callbacks run in the superclass.
        }

        /** Nothing to undo: the repositories are in-memory doubles. intentional-stub */
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No resource; afterCompletion(ROLLED_BACK) runs in the superclass.
        }
    }

    /** R3 services (real) + tunnel adapter and listener (real) over test doubles. */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class ChainConfig {

        /** Synchronisation-only transactions. */
        @Bean
        PlatformTransactionManager transactionManager() {
            return new SynchronizationOnlyTransactionManager();
        }

        /** R3 data settings over an in-memory PSP row and change log. */
        @Bean
        PspDataSettingsService pspDataSettingsService(ApplicationEventPublisher events) {
            PspRepository psps = mock(PspRepository.class);
            when(psps.findById(12L)).thenReturn(Optional.of(PSP));
            when(psps.save(any(Psp.class))).thenAnswer(i -> i.getArgument(0));
            PspDataSettingChangeRepository changes = mock(PspDataSettingChangeRepository.class);
            AtomicLong ids = new AtomicLong();
            when(changes.save(any(PspDataSettingChange.class))).thenAnswer(i -> {
                PspDataSettingChange c = i.getArgument(0);
                if (c.getId() == null) {
                    c.setId(ids.incrementAndGet());
                    CHANGES.add(c);
                }
                return c;
            });
            when(changes.findFirstByPspIdAndSettingAndStatus(any(), any(), any())).thenReturn(Optional.empty());
            return new PspDataSettingsService(psps, changes, mock(AuditLogService.class), events);
        }

        /** R3 config service: content follows the PSP row; a new hash mints the next version. */
        @Bean
        EdgeConfigService edgeConfigService(ApplicationEventPublisher events) {
            EdgeConfigDocumentBuilder builder = mock(EdgeConfigDocumentBuilder.class);
            when(builder.buildContent(any(), any())).thenAnswer(i -> {
                ObjectNode doc = (ObjectNode) com.posgateway.aml.contracts.ContractFixtures
                        .fixture("edge-config--default.json").deepCopy();
                doc.remove(Set.of("v", "configVersion", "issuedAt"));
                doc.put("pspId", 12L);
                ((ObjectNode) doc.get("supportAccess")).put("enabled", Boolean.TRUE.equals(PSP.getSupportAccessEnabled()));
                return doc;
            });
            EdgeConfigVersionStore versions = mock(EdgeConfigVersionStore.class);
            AtomicReference<String> lastHash = new AtomicReference<>();
            AtomicLong version = new AtomicLong();
            when(versions.ensureVersion(anyLong(), anyString())).thenAnswer(i -> {
                String hash = i.getArgument(1);
                boolean minted = !hash.equals(lastHash.getAndSet(hash));
                long v = minted ? version.incrementAndGet() : version.get();
                return new EdgeConfigVersionStore.VersionResult(v, minted);
            });
            return new EdgeConfigService(builder, versions, mock(EdgeControlPlaneKeys.class),
                    mock(HokekaSecureEnvelope.class), events);
        }

        /** R3 tunnel settings (real). */
        @Bean
        EdgeTunnelSettingsService edgeTunnelSettingsService(EdgeConfigService configService) {
            return new EdgeTunnelSettingsService(configService);
        }

        /** The production adapter. */
        @Bean
        TunnelPspSettingsProvider residencyTunnelPspSettings(EdgeTunnelSettingsService service) {
            return new ResidencyTunnelPspSettings(service);
        }

        /** The production listener over the transport doubles; the flag is read from the PSP row. */
        @Bean
        TunnelResidencyEventListener tunnelResidencyEventListener() {
            return new TunnelResidencyEventListener(NOTIFIER, MESSAGING, GATEWAY,
                    pspId -> pspId == 12L && Boolean.TRUE.equals(PSP.getSupportAccessEnabled()));
        }
    }

    /** PSP 12 with support access on; config version 1 already issued. */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(NOTIFIER, MESSAGING, GATEWAY);
        when(NOTIFIER.notifyPsp(anyLong(), any())).thenReturn(List.of());
        CHANGES.clear();
        PSP.setPspId(12L);
        PSP.setRetentionYears(5);
        PSP.setSupportAccessEnabled(true);
        PSP.setCrossPspOptIn(false);
        ctx = new AnnotationConfigApplicationContext(ChainConfig.class);
        assertThat(ctx.getBean(TunnelPspSettingsProvider.class).settings(12L).configVersion()).isEqualTo(1L);
        org.mockito.Mockito.reset(NOTIFIER, MESSAGING);
        when(NOTIFIER.notifyPsp(anyLong(), any())).thenReturn(List.of());
    }

    /** Closes the context. */
    @AfterEach
    void tearDown() {
        ctx.close();
    }

    /** Verifies: withdrawal revokes platform pulls and notifies config.updated with the new version. */
    @Test
    void withdrawalCutsPlatformPullsAndNotifiesTheNewConfigVersion() {
        User maker = new User();
        maker.setId(1L);
        maker.setUsername("alice");
        Role role = new Role();
        role.setName("PSP_ADMIN");
        maker.setRole(role);

        PspDataSettingsService.ChangeView change = ctx.getBean(PspDataSettingsService.class)
                .proposeSupportAccess(12L, false, "Ticket closed, access no longer needed", maker);

        assertThat(change.status()).isEqualTo(PspDataSettingChange.Status.APPLIED.name());
        assertThat(PSP.getSupportAccessEnabled()).isFalse();
        verify(GATEWAY).revokeSupportAccess(12L);

        ArgumentCaptor<RelayMessage> relay = ArgumentCaptor.forClass(RelayMessage.class);
        verify(MESSAGING).broadcast(relay.capture());
        assertThat(relay.getValue().kind()).isEqualTo(RelayMessage.Kind.CONFIG_CHANGED);
        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(NOTIFIER).notifyPsp(eq(12L), body.capture());
        assertThat(body.getValue().get("type").asText()).isEqualTo("config.updated");
        assertThat(body.getValue().get("configVersion").asLong()).isEqualTo(2L);
        assertThat(new TunnelSchemas().validate("tunnel/notify.schema.json", body.getValue())).isEmpty();

        TunnelPspSettingsProvider.TunnelPspSettings now = ctx.getBean(TunnelPspSettingsProvider.class).settings(12L);
        assertThat(now.configVersion()).isEqualTo(2L);
        assertThat(now.tunnelEnabled()).isTrue();
    }

    /** Verifies: asking to enable creates a pending request only: nothing is revoked or re-issued. */
    @Test
    void enableRequestRevokesNothing() {
        PSP.setSupportAccessEnabled(false);
        User maker = new User();
        maker.setId(1L);
        maker.setUsername("alice");
        Role role = new Role();
        role.setName("PSP_ADMIN");
        maker.setRole(role);

        ctx.getBean(PspDataSettingsService.class).proposeSupportAccess(12L, true, "Ticket 4411 needs a look", maker);

        verify(GATEWAY, never()).revokeSupportAccess(anyLong());
        verify(NOTIFIER, never()).notifyPsp(anyLong(), any());
    }
}
