package com.posgateway.aml.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Defines and provisions the Kafka topics that have active producers and consumers.
 *
 * <p>Every topic produced or consumed by the Control Plane is declared here as a {@link NewTopic}
 * bean ({@link #ALL_TOPICS}). They were previously never created: {@code spring.kafka.admin.auto-create}
 * was {@code false} and the production broker runs with {@code KAFKA_AUTO_CREATE_TOPICS_ENABLE=false},
 * so producers failed and listeners idled on missing topics. Provisioning is now on
 * ({@code spring.kafka.admin.auto-create=${KAFKA_TOPICS_AUTO_CREATE:true}}) with partition counts and
 * the replication factor taken from configuration ({@code kafka.topics.*}). Because Spring's
 * {@link KafkaAdmin} only tries once at startup, {@link #retryTopicProvisioning()} re-runs it until it
 * succeeds when the broker was not reachable at boot.
 */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    public static final String TOPIC_CASE_LIFECYCLE = "aml.case.lifecycle";
    public static final String TOPIC_CASE_DECISION = "aml.case.decision";
    public static final String TOPIC_TRANSACTIONS_RAW = "transactions.raw";
    public static final String TOPIC_TRANSACTIONS_AUDIT = "transactions.audit";
    public static final String TOPIC_ALERTS_GENERATED = "alerts.generated";

    /** Every topic the Control Plane produces to or consumes from. */
    public static final List<String> ALL_TOPICS = List.of(TOPIC_CASE_LIFECYCLE, TOPIC_CASE_DECISION,
            TOPIC_TRANSACTIONS_RAW, TOPIC_TRANSACTIONS_AUDIT, TOPIC_ALERTS_GENERATED);

    private static final String MS_7_DAY = String.valueOf(7 * 24L * 60 * 60 * 1000);
    private static final String MS_30_DAY = String.valueOf(30 * 24L * 60 * 60 * 1000);

    @Value("${kafka.topics.replication-factor:1}")
    private short replicationFactor = 1;
    @Value("${kafka.topics.case-lifecycle.partitions:3}")
    private int caseLifecyclePartitions = 3;
    @Value("${kafka.topics.case-decision.partitions:3}")
    private int caseDecisionPartitions = 3;
    @Value("${kafka.topics.transactions-raw.partitions:12}")
    private int transactionsRawPartitions = 12;
    @Value("${kafka.topics.transactions-audit.partitions:6}")
    private int transactionsAuditPartitions = 6;
    @Value("${kafka.topics.alerts-generated.partitions:6}")
    private int alertsGeneratedPartitions = 6;

    private final AtomicBoolean provisioned = new AtomicBoolean(false);

    /** Builds a topic with the configured replication factor (never below 1). */
    private NewTopic topic(String name, int partitions, String retentionMs) {
        TopicBuilder builder = TopicBuilder.name(name)
                .partitions(Math.max(1, partitions))
                .replicas(Math.max((short) 1, replicationFactor));
        if (retentionMs != null) {
            builder.config(TopicConfig.RETENTION_MS_CONFIG, retentionMs);
        }
        return builder.build();
    }

    /** Case lifecycle events (outbox -> notification consumer). */
    @Bean
    public NewTopic caseLifecycleTopic() {
        return topic(TOPIC_CASE_LIFECYCLE, caseLifecyclePartitions, null);
    }

    /** Case decisions (outbox -> notification and reporting consumers). */
    @Bean
    public NewTopic caseDecisionTopic() {
        return topic(TOPIC_CASE_DECISION, caseDecisionPartitions, null);
    }

    /** Raw ingested transactions (feature engine + Neo4j projection consumers). */
    @Bean
    public NewTopic transactionsRawTopic() {
        return topic(TOPIC_TRANSACTIONS_RAW, transactionsRawPartitions, MS_7_DAY);
    }

    /** Transaction audit trail (produced by audit log / result reporting). */
    @Bean
    public NewTopic transactionsAuditTopic() {
        return topic(TOPIC_TRANSACTIONS_AUDIT, transactionsAuditPartitions, MS_30_DAY);
    }

    /** Generated alerts (decision engine -> reporting consumer). */
    @Bean
    public NewTopic alertsGeneratedTopic() {
        return topic(TOPIC_ALERTS_GENERATED, alertsGeneratedPartitions, MS_7_DAY);
    }

    /** Listener factory; missing topics are not fatal so a late broker does not stop the app. */
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

    /** Holder so the scheduled retry can reach the auto-configured admin without a hard dependency. */
    @Bean
    public KafkaTopicProvisioningRetry kafkaTopicProvisioningRetry(
            org.springframework.beans.factory.ObjectProvider<KafkaAdmin> kafkaAdmin,
            @Value("${spring.kafka.admin.auto-create:true}") boolean autoCreate) {
        return new KafkaTopicProvisioningRetry(kafkaAdmin, autoCreate, provisioned);
    }

    /**
     * Re-runs {@link KafkaAdmin#initialize()} (create missing {@link NewTopic}s) every minute until it
     * succeeds once. A no-op when auto-create is disabled or after success.
     */
    public static class KafkaTopicProvisioningRetry {
        private final org.springframework.beans.factory.ObjectProvider<KafkaAdmin> kafkaAdmin;
        private final boolean autoCreate;
        private final AtomicBoolean provisioned;

        KafkaTopicProvisioningRetry(org.springframework.beans.factory.ObjectProvider<KafkaAdmin> kafkaAdmin,
                                    boolean autoCreate, AtomicBoolean provisioned) {
            this.kafkaAdmin = kafkaAdmin;
            this.autoCreate = autoCreate;
            this.provisioned = provisioned;
        }

        /** One provisioning attempt; returns true once all declared topics exist. */
        @Scheduled(initialDelayString = "${kafka.topics.provision-retry-initial-delay-ms:30000}",
                fixedDelayString = "${kafka.topics.provision-retry-delay-ms:60000}")
        public boolean retryTopicProvisioning() {
            if (!autoCreate || provisioned.get()) {
                return provisioned.get();
            }
            KafkaAdmin admin = kafkaAdmin.getIfAvailable();
            if (admin == null) {
                return false;
            }
            try {
                if (admin.initialize()) {
                    provisioned.set(true);
                    log.info("Kafka topics provisioned: {}", ALL_TOPICS);
                }
            } catch (RuntimeException e) {
                log.warn("Kafka topic provisioning failed (will retry): {}", e.getMessage());
            }
            return provisioned.get();
        }
    }
}
