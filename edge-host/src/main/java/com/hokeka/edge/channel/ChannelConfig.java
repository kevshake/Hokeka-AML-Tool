package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;

/** Wiring for the control-plane channel: shared replay/envelope codec plus the outbound loops. */
@Configuration
@EnableConfigurationProperties(ControlPlaneProperties.class)
public class ChannelConfig {

    @Bean
    public ReplayGuard replayGuard(ControlPlaneProperties properties) {
        return new ReplayGuard(properties.getNonceCacheSize(), properties.getMaxClockSkew());
    }

    @Bean
    public SealedEnvelopeCodec sealedEnvelopeCodec(ObjectMapper mapper, ReplayGuard replayGuard) {
        return new SealedEnvelopeCodec(mapper, replayGuard);
    }

    /**
     * The durable decision-record shipper (Edge Ledger outbox → {@code POST /api/v1/edge/assessments}).
     * Only exists when the control-plane channel is enabled; with the channel off, records simply
     * accumulate in the outbox until it is.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public OutboxShipper outboxShipper(ControlPlaneProperties properties, LedgerProperties ledgerProperties,
                                       SecureChannel channel, SealedEnvelopeCodec codec,
                                       ActivationService activation, EdgeLedger ledger,
                                       ContractSchemas schemas, ObjectMapper mapper) {
        return new OutboxShipper(properties, ledgerProperties, channel, codec, activation, ledger, schemas,
                mapper, Clock.systemUTC());
    }

    /**
     * Fixed-delay loops driven by the configured {@link java.time.Duration}s. Registered
     * programmatically so the intervals bind once, on {@link ControlPlaneProperties}.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class ChannelSchedulingConfig implements SchedulingConfigurer {

        private final ControlPlaneProperties properties;
        private final RuleBundlePoller poller;
        private final MetricsShipper shipper;
        private final OutboxShipper outboxShipper;

        ChannelSchedulingConfig(ControlPlaneProperties properties, RuleBundlePoller poller, MetricsShipper shipper,
                                OutboxShipper outboxShipper) {
            this.properties = properties;
            this.poller = poller;
            this.shipper = shipper;
            this.outboxShipper = outboxShipper;
        }

        /** Registers the bundle poll, the metrics window and the outbox drain. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(poller::poll, properties.getBundlePollInterval());
            registrar.addFixedDelayTask(shipper::ship, properties.getMetricsInterval());
            registrar.addFixedDelayTask(outboxShipper::ship, properties.getAssessmentShipInterval());
        }
    }
}
