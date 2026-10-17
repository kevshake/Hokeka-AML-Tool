package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires local sanctions screening (R10b, contract 6.4 / 6.5): the list in force (restored from the
 * site ledger in the background, so it works with the control-plane channel off), the evaluate-path
 * screener, and — with the channel on — the list fetcher, polled on the config cycle and early after
 * each newly accepted config document.
 */
@Configuration
public class SanctionsConfig {

    /** The list in force and its ledger copy. */
    @Bean
    public SanctionsListStore sanctionsListStore(EdgeLedger ledger, ObjectMapper mapper, EdgeConfigStore config,
                                                 @Value("${hokeka.sanctions.similarity-threshold:0.8}") double threshold) {
        return new SanctionsListStore(ledger, mapper, threshold,
                () -> config.current().filter(EdgeConfigDocument::sanctionsListPublished)
                        .map(EdgeConfigDocument::sanctionsListHash));
    }

    /** The evaluate-path screener. */
    @Bean
    public SanctionsScreener sanctionsScreener(SanctionsListStore lists, EdgeConfigStore config,
                                               @Value("${hokeka.sanctions.hold-when-list-missing:true}") boolean hold) {
        return new SanctionsScreener(lists, config, hold);
    }

    /** The list fetcher; registered as a config listener so a new list is fetched promptly. */
    @Bean
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SanctionsListPoller sanctionsListPoller(ControlPlaneProperties properties, SecureChannel channel,
                                                   SealedEnvelopeCodec codec, ActivationService activation,
                                                   EdgeConfigStore config, SanctionsListStore store,
                                                   ContractSchemas schemas, ObjectMapper mapper) {
        SanctionsListPoller poller = new SanctionsListPoller(properties, channel, codec, activation, config, store,
                schemas, mapper, Clock.systemUTC());
        config.addListener(doc -> poller.pollSoon());
        return poller;
    }

    /** Background restore of the list from the ledger (retried until the ledger is ready). */
    @Configuration
    @EnableScheduling
    static class RestoreScheduling implements SchedulingConfigurer {

        private final SanctionsListStore store;

        RestoreScheduling(SanctionsListStore store) {
            this.store = store;
        }

        /** Registers the restore check. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(store::restoreIfNeeded, Duration.ofSeconds(5));
        }
    }

    /** Polls the list on the bundle/config cycle. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class PollScheduling implements SchedulingConfigurer {

        private final SanctionsListPoller poller;
        private final ControlPlaneProperties properties;

        PollScheduling(SanctionsListPoller poller, ControlPlaneProperties properties) {
            this.poller = poller;
            this.properties = properties;
        }

        /** Registers the list poll. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(poller::poll, properties.getBundlePollInterval());
        }
    }
}
