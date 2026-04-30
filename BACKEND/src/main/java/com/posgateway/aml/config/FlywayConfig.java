package com.posgateway.aml.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to ensure Flyway migrations run before Hibernate validation
 * This prevents schema validation errors when migrations haven't been applied yet
 */
@Configuration
public class FlywayConfig {

    /**
     * Ensure Flyway runs before JPA/Hibernate initialization
     * This bean will be initialized before EntityManagerFactory
     */
    @Bean
    @Primary
    public FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway, null);
    }
}