package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.channel.RuleBundlePoller;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.residency.EdgeConfigPoller;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.residency.LegalHoldService;
import com.hokeka.edge.residency.RetentionPurger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/**
 * Wires the Edge Tunnel (EDGE_DATA_RESIDENCY_CONTRACT.md §7): the op handlers over the ledger
 * (always present, so they can be exercised without a connection) and, when the control-plane
 * channel is enabled, the client that connects after the application is ready and closes with 1000
 * on shutdown.
 */
@Configuration
@EnableConfigurationProperties(TunnelProperties.class)
public class TunnelConfig {

    private static final Logger log = LoggerFactory.getLogger(TunnelConfig.class);

    /** Frame codec bound to this node's id, sharing the channel's replay guard (one nonce LRU per node). */
    @Bean
    public TunnelFrameCodec tunnelFrameCodec(ObjectMapper mapper, ReplayGuard replayGuard, ContractSchemas schemas,
                                             ControlPlaneProperties controlPlane) {
        return new TunnelFrameCodec(mapper, replayGuard, schemas, controlPlane.getEdgeId());
    }

    /** The Edge side of the tunnel audit. */
    @Bean
    public AccessLogWriter accessLogWriter(EdgeLedger ledger) {
        return new AccessLogWriter(ledger);
    }

    /** ledger.health over the 24 h write-failure window of the evaluate path. */
    @Bean
    public LedgerHealth ledgerHealth(EdgeLedger ledger, LegalHoldService holds, RetentionPurger purger,
                                     EdgeConfigStore config, LedgerProperties ledgerProperties,
                                     EvaluationRecorder recorder, ControlPlaneProperties controlPlane,
                                     ObjectMapper mapper) {
        Clock clock = Clock.systemUTC();
        Path identity = Path.of(controlPlane.getIdentityFile()).toAbsolutePath();
        Path dataDir = identity.getParent() == null ? identity : identity.getParent();
        return new LedgerHealth(ledger, holds, purger, config, ledgerProperties,
                new RollingCount(recorder::writeFailures, clock), recorder::retryBacklog, dataDir,
                ledgerProperties.effectiveSiteId(controlPlane.getEdgeId()), mapper, clock);
    }

    /** The op allowlist and handlers. */
    @Bean
    public TunnelOps tunnelOps(TunnelFrameCodec codec, ContractSchemas schemas, EdgeLedger ledger,
                               LedgerHealth health, EdgeConfigStore config, LedgerProperties ledgerProperties,
                               ControlPlaneProperties controlPlane, ObjectMapper mapper) {
        return new TunnelOps(codec, schemas, new LedgerReads(ledger), new TunnelItems(mapper),
                new TunnelCursor(mapper, schemas),
                new BackfillImporter(ledger, mapper, ledgerProperties.effectiveSiteId(controlPlane.getEdgeId())),
                new CtrAggregator(mapper), health, config, ledgerProperties, mapper, Clock.systemUTC());
    }

    /** Samples the write-failure window so writeFailures24h is accurate between health calls. */
    @Configuration
    @EnableScheduling
    static class HealthSampling implements SchedulingConfigurer {

        private final LedgerHealth health;

        HealthSampling(LedgerHealth health) {
            this.health = health;
        }

        /** Registers the sampler. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(() -> health.writeFailures().sample(), Duration.ofMinutes(10));
        }
    }

    /** The client; it starts once the application is ready (activation has loaded the identity by then). */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public EdgeTunnelClient edgeTunnelClient(TunnelProperties properties, ControlPlaneProperties controlPlane,
                                             LedgerProperties ledgerProperties, SecureChannel channel,
                                             ActivationService activation, TunnelFrameCodec codec, TunnelOps ops,
                                             AccessLogWriter accessLog, EdgeConfigStore config, LegalHoldService holds,
                                             EdgeConfigPoller configPoller, RuleBundlePoller bundlePoller,
                                             EdgeEngine engine, EdgeLedger ledger, ObjectMapper mapper) {
        URI uri = EdgeTunnelClient.tunnelUri(properties.getUrl(), controlPlane.getBaseUrl());
        if (uri == null && properties.isEnabled()) {
            log.error("Edge tunnel endpoint unknown: EDGE_TUNNEL_URL must be wss://… or CONTROLPLANE_URL https://…");
        }
        if (properties.getAgentVersion() == null || properties.getAgentVersion().isBlank()) {
            String v = TunnelConfig.class.getPackage().getImplementationVersion();
            properties.setAgentVersion(v == null ? "0.0.0" : v);
        }
        EdgeTunnelClient.Identity identity = new EdgeTunnelClient.Identity(controlPlane.getEdgeId(),
                ledgerProperties.effectiveSiteId(controlPlane.getEdgeId()), config.pspId());
        EdgeTunnelClient.Signals signals = new EdgeTunnelClient.Signals(configPoller::pollSoon, bundlePoller::pollSoon,
                bundlePoller::lastSuccess, engine::activeVersion, ledger::schemaVersion);
        return new EdgeTunnelClient(properties, uri, identity, () -> keys(controlPlane, activation),
                channel::mutualTlsHttpClient, activation, codec, ops, accessLog, config, holds, signals, mapper,
                Clock.systemUTC());
    }

    /** The pinned Control Plane keys and this node's private keys; missing ones are named by variable. */
    static EdgeTunnelClient.Keys keys(ControlPlaneProperties cp, ActivationService activation) {
        if (cp.getEncryptionPublicKey() == null || cp.getEncryptionPublicKey().isBlank()) {
            throw new IllegalStateException("CONTROLPLANE_X25519_PUBLIC_KEY is not set");
        }
        if (cp.getSigningPublicKey() == null || cp.getSigningPublicKey().isBlank()) {
            throw new IllegalStateException("CONTROLPLANE_ED25519_PUBLIC_KEY is not set");
        }
        return new EdgeTunnelClient.Keys(EdgeKeys.decodeX25519PublicRaw(cp.getEncryptionPublicKey()),
                EdgeKeys.decodeEd25519Public(cp.getSigningPublicKey()), activation.x25519Private(),
                activation.ed25519Private());
    }

    /** Starts the client when the application is ready. */
    @Bean
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ApplicationListener<ApplicationReadyEvent> edgeTunnelStarter(EdgeTunnelClient client) {
        return event -> client.start();
    }
}
