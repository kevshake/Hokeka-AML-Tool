package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Clock;

/**
 * Wires the data-residency components that run whether or not the control-plane channel is enabled
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §6.3, §8): the config document in force (restored from the
 * ledger after a restart), legal holds, and the daily retention purge. Without the channel no new
 * config arrives, so a node that never accepted one never purges — exactly the §8.2 rule.
 */
@Configuration
public class ResidencyConfig {

    private static final Logger log = LoggerFactory.getLogger(ResidencyConfig.class);

    /** The config document in force; {@code EDGE_PSPID} must be a positive integer to accept any. */
    @Bean
    public EdgeConfigStore edgeConfigStore(EdgeLedger ledger, ContractSchemas schemas, ObjectMapper mapper,
                                           ControlPlaneProperties controlPlane) {
        Long pspId = EdgeConfigStore.parsePspId(controlPlane.getPspId());
        if (pspId == null) {
            log.error("EDGE_PSPID is not set to a positive integer: no Edge config document can be accepted, so the "
                    + "retention purge stays off and the tunnel cannot connect");
        }
        return new EdgeConfigStore(ledger, schemas, mapper, pspId, controlPlane.getEdgeId());
    }

    /** Legal holds (placed and released over the tunnel). */
    @Bean
    public LegalHoldService legalHoldService(EdgeLedger ledger) {
        return new LegalHoldService(ledger);
    }

    /** The daily retention purge. */
    @Bean
    public RetentionPurger retentionPurger(EdgeLedger ledger, LedgerProperties properties, EdgeConfigStore store) {
        if (properties.getRetentionMinYears() < RetentionPolicy.FLOOR_YEARS) {
            log.warn("EDGE_RETENTION_MIN_YEARS is below the {}-year floor and is ignored", RetentionPolicy.FLOOR_YEARS);
        }
        return new RetentionPurger(ledger, properties, store, Clock.systemUTC());
    }

    /** Checks every {@code purge-check-interval} whether today's purge is due. */
    @Configuration
    @EnableScheduling
    static class PurgeScheduling implements SchedulingConfigurer {

        private final RetentionPurger purger;
        private final LedgerProperties properties;

        PurgeScheduling(RetentionPurger purger, LedgerProperties properties) {
            this.purger = purger;
            this.properties = properties;
        }

        /** Registers the purge check. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(purger::runIfDue, properties.getPurgeCheckInterval());
        }
    }
}
