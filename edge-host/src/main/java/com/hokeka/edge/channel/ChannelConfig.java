package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** Wiring for the control-plane channel: shared replay/envelope codec plus the two outbound loops. */
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

        ChannelSchedulingConfig(ControlPlaneProperties properties, RuleBundlePoller poller, MetricsShipper shipper) {
            this.properties = properties;
            this.poller = poller;
            this.shipper = shipper;
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(poller::poll, properties.getBundlePollInterval());
            registrar.addFixedDelayTask(shipper::ship, properties.getMetricsInterval());
        }
    }
}
