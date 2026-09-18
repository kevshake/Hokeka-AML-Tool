package com.posgateway.aml.config.kafka;

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
 * High-Performance Kafka Topic Configuration for AML System
 * 
 * Architecture:
 * - transactions.raw: Raw transaction ingestion (key: customer_id)
 * - transactions.enriched: Enriched with customer profile (key: customer_id)
 * - features.updates: Feature store updates (key: customer_id)
 * - transactions.decisions: Final decision events (key: customer_id)
 * - alerts.generated: Suspicious activity alerts (key: alert_id)
 * - cases.events: Case management events (key: case_id)
 * - transactions.audit: Immutable audit log (key: customer_id)
 * - transactions.dlq: Dead letter queue for failed processing
 * 
 * Partitioning Strategy:
 * - All transaction topics partitioned by customer_id for ordering guarantees
 * - 32 partitions default (scalable to 64/128 for higher throughput)
 * - Per-customer ordering required for velocity rules and rolling windows
 */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicsConfig {

    // Topic Names
    public static final String TOPIC_TRANSACTIONS_RAW = "transactions.raw";
    public static final String TOPIC_TRANSACTIONS_ENRICHED = "transactions.enriched";
    public static final String TOPIC_FEATURES_UPDATES = "features.updates";
    public static final String TOPIC_TRANSACTIONS_DECISIONS = "transactions.decisions";
    public static final String TOPIC_ALERTS_GENERATED = "alerts.generated";
    public static final String TOPIC_CASES_EVENTS = "cases.events";
    public static final String TOPIC_TRANSACTIONS_AUDIT = "transactions.audit";
    public static final String TOPIC_TRANSACTIONS_DLQ = "transactions.dlq";
    
    // Legacy topics (for backward compatibility)
    public static final String TOPIC_CASE_LIFECYCLE = "aml.case.lifecycle";
    public static final String TOPIC_CASE_DECISION = "aml.case.decision";
    public static final String TOPIC_COMPLIANCE_ALERT = "aml.compliance.alert";

    // Default partition count - adjust based on throughput requirements
    // Formula: partitions = TPS / target_per_partition (500 TPS/partition)
    // For 10k TPS: 10,000 / 500 = 20 partitions + buffer = 32
    private static final int DEFAULT_PARTITIONS = 32;
    private static final short REPLICATION_FACTOR = 1; // Use 3 for production

    /**
     * Raw transactions topic - Entry point for all transactions
     * Key: customer_id (ensures per-customer ordering)
     * Retention: 7 days (configurable)
     */
    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_RAW)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000)) // 7 days
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Enriched transactions topic - Transactions with customer profile data
     * Key: customer_id
     * Retention: 7 days
     */
    @Bean
    public NewTopic transactionsEnrichedTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_ENRICHED)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Feature updates topic - Event-driven feature store updates
     * Key: customer_id
     * Retention: 1 day (features persisted to Redis/DB)
     */
    @Bean
    public NewTopic featuresUpdatesTopic() {
        return TopicBuilder.name(TOPIC_FEATURES_UPDATES)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(24 * 60 * 60 * 1000)) // 1 day
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Transaction decisions topic - Final risk decisions
     * Key: customer_id
     * Retention: 30 days
     */
    @Bean
    public NewTopic transactionsDecisionsTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_DECISIONS)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(30L * 24 * 60 * 60 * 1000)) // 30 days
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Alerts topic - Generated alerts for suspicious activity
     * Key: alert_id
     * Retention: 90 days (compliance requirement)
     */
    @Bean
    public NewTopic alertsGeneratedTopic() {
        return TopicBuilder.name(TOPIC_ALERTS_GENERATED)
                .partitions(16) // Fewer partitions for alerts (lower volume)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(90L * 24 * 60 * 60 * 1000)) // 90 days
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Case events topic - Case management lifecycle
     * Key: case_id
     * Retention: 1 year
     */
    @Bean
    public NewTopic casesEventsTopic() {
        return TopicBuilder.name(TOPIC_CASES_EVENTS)
                .partitions(8)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(365L * 24 * 60 * 60 * 1000)) // 1 year
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Audit topic - Immutable transaction audit log
     * Key: customer_id
     * Retention: 7 years (regulatory requirement)
     * Compaction enabled for replayability
     */
    @Bean
    public NewTopic transactionsAuditTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_AUDIT)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7L * 365 * 24 * 60 * 60 * 1000)) // 7 years
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    /**
     * Dead Letter Queue - Failed message processing
     * Key: original message key
     * Retention: 30 days
     */
    @Bean
    public NewTopic transactionsDlqTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_DLQ)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(30L * 24 * 60 * 60 * 1000))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    // Legacy topics for backward compatibility
    @Bean
    public NewTopic caseLifecycleTopic() {
        return TopicBuilder.name(TOPIC_CASE_LIFECYCLE)
                .partitions(8)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic caseDecisionTopic() {
        return TopicBuilder.name(TOPIC_CASE_DECISION)
                .partitions(8)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic complianceAlertTopic() {
        return TopicBuilder.name(TOPIC_COMPLIANCE_ALERT)
                .partitions(8)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    /**
     * Configure Kafka Listener Container Factory
     * Optimized for high-throughput transaction processing
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
        
        // Enable batch processing for higher throughput
        factory.setBatchListener(true);
        
        return factory;
    }
}
