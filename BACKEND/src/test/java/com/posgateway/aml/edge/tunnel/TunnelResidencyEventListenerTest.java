package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.service.residency.EdgeConfigUpdatedEvent;
import com.posgateway.aml.service.residency.PspDataSettingsChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R3 events → tunnel without Redis: {@code notify config.updated} body shape and fan-out, the
 * {@code CONFIG_CHANGED} broadcast, support-access withdrawal cancelling platform pulls, and the
 * after-commit timing (nothing happens while the publishing transaction is open or after it
 * rolls back).
 */
class TunnelResidencyEventListenerTest {

    private TunnelNotifier notifier;
    private TunnelMessaging messaging;
    private EdgeDataGateway gateway;
    private SupportAccessPolicy supportAccess;
    private AnnotationConfigApplicationContext ctx;

    /** Real Spring event infrastructure (incl. the transactional listener factory) over mocks. */
    @BeforeEach
    void setUp() {
        notifier = mock(TunnelNotifier.class);
        messaging = mock(TunnelMessaging.class);
        gateway = mock(EdgeDataGateway.class);
        supportAccess = mock(SupportAccessPolicy.class);
        when(notifier.notifyPsp(anyLong(), any())).thenReturn(List.of());
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(TransactionalEventListenerFactory.class);
        ctx.registerBean(TunnelResidencyEventListener.class,
                () -> new TunnelResidencyEventListener(notifier, messaging, gateway, supportAccess));
        ctx.refresh();
    }

    /** Closes the context and clears any transaction synchronisation a test opened. */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        ctx.close();
    }

    /** Verifies: a new config version broadcasts CONFIG_CHANGED and notifies with the schema body. */
    @Test
    void configUpdateBroadcastsAndNotifiesWithTheContractBody() {
        ctx.publishEvent(new EdgeConfigUpdatedEvent(42L, 7L));

        ArgumentCaptor<RelayMessage> relay = ArgumentCaptor.forClass(RelayMessage.class);
        verify(messaging).broadcast(relay.capture());
        assertThat(relay.getValue().kind()).isEqualTo(RelayMessage.Kind.CONFIG_CHANGED);
        assertThat(relay.getValue().pspId()).isEqualTo(42L);

        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(notifier).notifyPsp(eq(42L), body.capture());
        assertThat(body.getValue()).isEqualTo(ContractFixtures.MAPPER.createObjectNode()
                .put("type", "config.updated").put("configVersion", 7L));
        TunnelSchemas schemas = new TunnelSchemas();
        assertThat(schemas.validate("tunnel/notify.schema.json", body.getValue())).isEmpty();
        assertThat(schemas.validate("tunnel/notify/config.updated.schema.json", body.getValue())).isEmpty();
    }

    /** Verifies: a relay failure does not stop the notify (and vice versa nothing escapes). */
    @Test
    void relayFailureStillNotifies() {
        doThrow(new IllegalStateException("redis down")).when(messaging).broadcast(any());
        ctx.publishEvent(new EdgeConfigUpdatedEvent(42L, 8L));
        verify(notifier).notifyPsp(eq(42L), any());
    }

    /** Verifies: an event without a PSP or with an impossible version is ignored. */
    @Test
    void malformedConfigEventIsIgnored() {
        ctx.publishEvent(new EdgeConfigUpdatedEvent(null, 3L));
        ctx.publishEvent(new EdgeConfigUpdatedEvent(42L, 0L));
        verifyNoInteractions(notifier, messaging);
    }

    /** Verifies: withdrawing support access revokes in-flight platform pulls on every instance. */
    @Test
    void supportAccessWithdrawalRevokesPlatformPulls() {
        when(supportAccess.isSupportAccessEnabled(42L)).thenReturn(false);
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "SUPPORT_ACCESS"));
        verify(gateway).revokeSupportAccess(42L);
    }

    /** Verifies: enabling support access, or another setting changing, revokes nothing. */
    @Test
    void enablingOrOtherSettingsDoNotRevoke() {
        when(supportAccess.isSupportAccessEnabled(42L)).thenReturn(true);
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "SUPPORT_ACCESS"));
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "RETENTION_YEARS"));
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "LIMITS"));
        verify(gateway, never()).revokeSupportAccess(anyLong());
        verify(supportAccess).isSupportAccessEnabled(42L);
    }

    /** Verifies: an unreadable flag fails safe (revoke), and an unreachable relay cancels locally. */
    @Test
    void failuresFailSafe() {
        when(supportAccess.isSupportAccessEnabled(42L)).thenThrow(new IllegalStateException("db down"));
        doThrow(new IllegalStateException("redis down")).when(gateway).revokeSupportAccess(42L);
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "SUPPORT_ACCESS"));
        ArgumentCaptor<RelayMessage> local = ArgumentCaptor.forClass(RelayMessage.class);
        verify(gateway).onBroadcast(local.capture());
        assertThat(local.getValue().kind()).isEqualTo(RelayMessage.Kind.SUPPORT_REVOKED);
        assertThat(local.getValue().pspId()).isEqualTo(42L);
    }

    /** Verifies: inside a transaction both listeners wait for the commit. */
    @Test
    void listenersRunOnlyAfterCommit() {
        when(supportAccess.isSupportAccessEnabled(42L)).thenReturn(false);
        beginTransaction();
        ctx.publishEvent(new EdgeConfigUpdatedEvent(42L, 9L));
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "SUPPORT_ACCESS"));
        verifyNoInteractions(notifier, messaging, gateway, supportAccess);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(notifier).notifyPsp(eq(42L), any());
        verify(messaging).broadcast(any());
        verify(gateway).revokeSupportAccess(42L);
    }

    /** Verifies: a rolled-back change neither notifies nor revokes. */
    @Test
    void rolledBackChangeDoesNothing() {
        beginTransaction();
        ctx.publishEvent(new EdgeConfigUpdatedEvent(42L, 10L));
        ctx.publishEvent(new PspDataSettingsChangedEvent(42L, "SUPPORT_ACCESS"));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(notifier, messaging, gateway, supportAccess);
    }

    /** Opens a synchronised "actual" transaction the way a transaction manager does. */
    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    /** Ends the transaction: runs the registered synchronisations with {@code status}. */
    private static void complete(int status) {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        for (TransactionSynchronization s : syncs) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                s.afterCommit();
            }
            s.afterCompletion(status);
        }
    }
}
