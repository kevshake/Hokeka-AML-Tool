package com.posgateway.aml.config.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Configuration for High-Performance Feature/Score/Decision Caching.
 *
 * Architecture:
 * - Redis stores pre-computed customer features for O(1) access
 * - Hot-path XGBoost score cache (5 min TTL, namespaced "graph:")
 * - Risk decision audit cache (24h TTL)
 * - Feature updates are async (from Kafka)
 *
 * Key Patterns:
 * - aml:customer:{customerId}:features - Main feature object
 * - aml:customer:{customerId}:tx:1h     - Rolling 1h counter (sorted set)
 * - aml:customer:{customerId}:velocity  - Velocity tracking
 * - aml:blacklist:{type}                - Blacklist entries (no expiry)
 * - graph:* / kyc_*: / screening_*: ... - per-feature namespaces (see callers)
 *
 * Performance Targets:
 * - Read latency:  &lt; 1ms
 * - Write latency: &lt; 2ms
 * - Throughput:    100k+ ops/sec
 *
 * NOTE: Spring Boot 3.2 standardized property names under {@code spring.data.redis.*}.
 * The matching {@link org.springframework.boot.autoconfigure.data.redis.RedisProperties}
 * is honored by the auto-configuration; we read the same keys explicitly here so the
 * {@link LettuceClientConfiguration} command timeout matches what's in
 * {@code application.properties}.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration timeout;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure JSON serializer (handles java.time.* via JavaTimeModule)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

}
