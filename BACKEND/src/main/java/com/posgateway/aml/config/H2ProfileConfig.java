package com.posgateway.aml.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Local in-memory profile configuration ({@code -Dspring.profiles.active=dev,h2}).
 *
 * Several services declare {@code KafkaTemplate<String, String>} as a mandatory
 * constructor parameter (AuditLogService, DecisionEngine, etc). When the
 * {@code h2} profile is active we exclude {@code KafkaAutoConfiguration}, so we
 * have to provide a stand-in bean that satisfies the dependency without
 * actually talking to a broker.
 *
 * The producer factory below is configured with a bogus broker address. The
 * Kafka client lazily resolves that address only when {@code send()} is
 * called; in this profile the resulting send simply fails fast and the
 * exception is ignored by the callers (they all use it as a fire-and-forget
 * event publisher).
 */
@Configuration
@Profile("h2")
public class H2ProfileConfig {

    @Bean
    @Primary
    public ProducerFactory<String, String> kafkaProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        // Unroutable address — the broker is never reached in this profile.
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Fail fast rather than block UI requests.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 100);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 200);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}
