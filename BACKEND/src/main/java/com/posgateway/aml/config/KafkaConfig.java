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

/** Defines the Kafka topics that have active producers and consumers. */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    public static final String TOPIC_CASE_LIFECYCLE = "aml.case.lifecycle";
    public static final String TOPIC_CASE_DECISION = "aml.case.decision";
    public static final String TOPIC_TRANSACTIONS_RAW = "transactions.raw";
    public static final String TOPIC_TRANSACTIONS_AUDIT = "transactions.audit";
    public static final String TOPIC_ALERTS_GENERATED = "alerts.generated";

    private static final String MS_7_DAY = String.valueOf(7 * 24L * 60 * 60 * 1000);
    private static final String MS_30_DAY = String.valueOf(30 * 24L * 60 * 60 * 1000);

    @Bean
    public NewTopic caseLifecycleTopic() {
        return TopicBuilder.name(TOPIC_CASE_LIFECYCLE).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic caseDecisionTopic() {
        return TopicBuilder.name(TOPIC_CASE_DECISION).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_RAW)
                .partitions(12)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_7_DAY)
                .build();
    }

    @Bean
    public NewTopic transactionsAuditTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS_AUDIT)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_30_DAY)
                .build();
    }

    @Bean
    public NewTopic alertsGeneratedTopic() {
        return TopicBuilder.name(TOPIC_ALERTS_GENERATED)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, MS_7_DAY)
                .build();
    }

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
