package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.contract.ContractSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.util.List;

/**
 * Wires the Edge Ledger: the {@link EdgeLedger} pool + migrations, the {@link EvaluationRecorder}
 * used by {@code /edge/evaluate}, and the ledger's own background loops (initialisation retry,
 * partition maintenance, in-memory retry drain). These loops run whether or not the control-plane
 * channel is enabled — recording evaluations is not optional.
 *
 * <p>Missing configuration is flagged once at startup by <i>variable name</i>; no value (and in
 * particular no password) is ever logged.
 */
@Configuration
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerConfig {

    private static final Logger log = LoggerFactory.getLogger(LedgerConfig.class);

    /** Contract schemas packaged into the jar from {@code docs/contracts}. */
    @Bean
    public ContractSchemas contractSchemas() {
        return ContractSchemas.fromClasspath();
    }

    /**
     * The site ledger. Construction never touches the network; the first initialisation runs right
     * here so a healthy database is ready before the first request, and a down one is retried by
     * {@link LedgerScheduling}.
     */
    @Bean(destroyMethod = "close")
    public EdgeLedger edgeLedger(LedgerProperties properties, ControlPlaneProperties controlPlane,
                                 ObjectMapper mapper) {
        List<String> missing = properties.missingSettings();
        if (controlPlane.getEdgeId() == null || controlPlane.getEdgeId().isBlank()) {
            log.error("EDGE_EDGEID is not set: decision records cannot name their node, so none will be "
                    + "shipped until it is configured");
        }
        if (!missing.isEmpty()) {
            log.error("Edge Ledger is NOT configured — missing {}. {}", String.join(", ", missing),
                    properties.isFailClosed()
                            ? "Every evaluation will answer HOLD (LEDGER_UNAVAILABLE) until it is."
                            : "ledger.fail-closed=false: evaluations proceed but are NOT recorded or shipped.");
        } else if (!properties.isFailClosed()) {
            log.warn("ledger.fail-closed=false: a failed ledger write no longer turns the decision into HOLD. "
                    + "Never run a PSP site like this.");
        }
        EdgeLedger ledger = new EdgeLedger(properties, mapper, controlPlane.getEdgeId());
        ledger.initialize();
        return ledger;
    }

    /** The evaluate path's recorder. */
    @Bean
    public EvaluationRecorder evaluationRecorder(EdgeLedger ledger, ContractSchemas schemas, ObjectMapper mapper) {
        return new EvaluationRecorder(ledger, schemas, mapper);
    }

    /** Ledger background loops, registered on the same fixed-delay scheduler as the channel loops. */
    @Configuration
    @EnableScheduling
    static class LedgerScheduling implements SchedulingConfigurer {

        private final LedgerProperties properties;
        private final EdgeLedger ledger;
        private final EvaluationRecorder recorder;

        LedgerScheduling(LedgerProperties properties, EdgeLedger ledger, EvaluationRecorder recorder) {
            this.properties = properties;
            this.ledger = ledger;
            this.recorder = recorder;
        }

        /** Registers initialisation retry, partition maintenance and the retry-buffer drain. */
        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(this::initializeIfDown, properties.getInitRetryInterval());
            registrar.addFixedDelayTask(this::maintainPartitions, properties.getMaintenanceInterval());
            registrar.addFixedDelayTask(recorder::drainRetries, properties.getRetryInterval());
        }

        /** Re-runs migrations for a ledger that was unreachable at boot (or since). */
        void initializeIfDown() {
            if (ledger.state() == EdgeLedger.State.DOWN) {
                ledger.initialize();
            }
        }

        /** Keeps the partition window rolling ahead of the clock. */
        void maintainPartitions() {
            if (!ledger.ready()) {
                return;
            }
            try {
                ledger.ensurePartitions(Instant.now());
            } catch (Exception e) {
                log.warn("Edge Ledger partition maintenance failed ({}); the write path creates a missing "
                        + "month on demand", EdgeLedger.safeMessage(e));
            }
        }
    }
}
