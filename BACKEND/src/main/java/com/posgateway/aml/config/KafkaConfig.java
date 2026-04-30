package com.posgateway.aml.config;

import org.apache.kafka.clients.admin.NewTopic;
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
 */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    public static final String TOPIC_CASE_LIFECYCLE = "aml.case.lifecycle";
    public static final String TOPIC_CASE_DECISION = "aml.case.decision";
    public static final String TOPIC_COMPLIANCE_ALERT = "aml.compliance.alert";

    /**
     * Topic for Case creation, status changes, and assignment events.
     */
    @Bean
    public NewTopic caseLifecycleTopic() {
        return TopicBuilder.name(TOPIC_CASE_LIFECYCLE)
                .partitions(3)
                .replicas(1) // Adjust based on broker setup (1 for localhost dev)
                .build();
    }

    /**
     * Topic for final regulatory decisions (SAR filed, Case Closed).
     */
    @Bean
    public NewTopic caseDecisionTopic() {
        return TopicBuilder.name(TOPIC_CASE_DECISION)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic for raw alerts that trigger risk scores or cases.
     */
    @Bean
    public NewTopic complianceAlertTopic() {
        return TopicBuilder.name(TOPIC_COMPLIANCE_ALERT)
                .partitions(3)
                .replicas(1)
                .build();
    }

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
        
        // Configure container properties for graceful broker unavailability handling
        ContainerProperties containerProps = factory.getContainerProperties();
        
        // Don't fail fatally if topics are missing - allows app to start even if broker is down
        // The consumer will retry connecting and will start consuming once broker is available
        containerProps.setMissingTopicsFatal(false);
        
        // Set phase to start listeners after other beans are initialized
        factory.setAutoStartup(true);
        
        return factory;
    }
}
