package com.posgateway.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for AML and Fraud Detection System
 * 
 * This application provides Anti-Money Laundering (AML) and Fraud Detection
 * capabilities for payment gateway transactions.
 */
@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class})
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(
    basePackages = "com.posgateway.aml.repository",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = "com\\.posgateway\\.aml\\.repository\\.graph\\..*"
    )
)
@org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories(
    basePackages = "com.posgateway.aml.repository.graph"
)
public class AmlFraudDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlFraudDetectorApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.filter.CommonsRequestLoggingFilter logFilter() {
        org.springframework.web.filter.CommonsRequestLoggingFilter filter
            = new org.springframework.web.filter.CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(false); // Headers can contain sensitive info like tokens
        filter.setAfterMessagePrefix("REQUEST DATA: ");
        return filter;
    }
}

