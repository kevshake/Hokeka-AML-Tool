package com.posgateway.aml.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka Configuration
 * Defines topics and producer/consumer settings for AML events.
 *
 * Topics (8 total):
 *   Legacy (3): aml.case.lifecycle, aml.case.decision, aml.compliance.alert
 *   Pipeline (5): transactions.raw, transactions.enriched, features.updates,
 *                 transactions.audit, alerts.generated
 */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    // ── Legacy topics (kept intact) ──────────────────────────────────────────

    public static final String TOPIC_CASE_LIFECYCLE   = "aml.case.lifecycle";
    public static final String TOPIC_CASE_DECISION    = "aml.case.decision";
    public static final String TOPIC_COMPLIANCE_ALERT = "aml.compliance.alert";

    // ── Architecture-v2 pipeline topics ──────────────────────────────────────

    /** Raw transaction events published immediately after DB persist. */
    public static final String TOPIC_TRANSACTIONS_RAW      = "transactions.raw";

    /** Enriched transaction events (features attached) published by FeatureEngineService. */
    public static final String TOPIC_TRANSACTIONS_ENRICHED = "transactions.enriched";

    /** Customer feature-update events consumed by downstream rule evaluators. */
    public static final String TOPIC_FEATURES_UPDATES      = "features.updates";

    /** Immutable transaction audit trail — long retention for compliance. */
    public static final String TOPIC_TRANSACTIONS_AUDIT    = "transactions.audit";

    /** Alert events published after an alert is persisted in the DB. */
    public static final String TOPIC_ALERTS_GENERATED      = "alerts.generated";

    // ── Retention helpers ─────────────────────────────────────────────────────

    private static final String MS_1_DAY  = String.valueOf(                 24L * 60 * 60 * 1000);
    private static final String MS_3_DAY  = String.valueOf(             3 * 24L * 60 * 60 * 1000);
    private static final String MS_7_DAY  = String.valueOf(             7 * 24L * 60 * 60 * 1000);
    private static final String MS_30_DAY = String.valueOf(            30 * 24L * 60 * 60 * 1000);

    // ── Legacy topic beans ────────────────────────────────────────────────────

    /** Topic for Case creation, status changes, and assignment events. */
    @Bean
    public NewTopic caseLifecycleTopic() {
        return TopicBuilder.name(TOPIC_CASE_LIFECYCLE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** Topic for final regulatory decisions (SAR filed, Case Closed). */
    @Bean
    public NewTopic caseDecisionTopic() {
        return TopicBuilder.name(TOPIC_CASE_DECISION)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** Topic for raw alerts that trigger risk scores or cases. */
    @Bean
    public NewTopic complianceAlertTopic() {
        return TopicBuilder.name(TOPIC_COMPLIANCE_ALERT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Pipeline topic beans ──────────────────────────────────────────────────

    /**
     * High-throughput raw transaction ingest — partitioned by pspId.
     * Replicas=1 for dev; set to 3 on production brokers.
     */
    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_RAW)
                .partitions(12)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_7_DAY)
                .build();
    }

    /** Enriched transaction events (velocity + feature snapshot attached). */
    @Bean
    public NewTopic transactionsEnrichedTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_ENRICHED)
                .partitions(12)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_3_DAY)
                .build();
    }

    /** Customer feature-delta events; short retention — downstream caches are the source of truth. */
    @Bean
    public NewTopic featuresUpdatesTopic() {
        return TopicBuilder.name(TOPIC_FEATURES_UPDATES)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_1_DAY)
                .build();
    }

    /** Immutable transaction audit trail; 30-day retention for regulatory compliance. */
    @Bean
    public NewTopic transactionsAuditTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_AUDIT)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_30_DAY)
                .build();
    }

    /** Alert-generated events consumed by downstream case-creation and notification services. */
    @Bean
    public NewTopic alertsGeneratedTopic() {
        return TopicBuilder.name(TOPIC_ALERTS_GENERATED)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_7_DAY)
                .build();
    }

    // ── Listener container factory ────────────────────────────────────────────

    /**
     * Configure Kafka Listener Container Factory with graceful handling of unavailable brokers.
     * This reduces the frequency of connection retry warnings when the broker is temporarily unavailable.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setMissingTopicsFatal(false);
        factory.setAutoStartup(true);

        return factory;
    }
}
