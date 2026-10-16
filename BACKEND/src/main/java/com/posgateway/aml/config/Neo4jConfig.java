package com.posgateway.aml.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Neo4j Configuration for Graph Database.
 * Uses Neo4j Community Edition.
 * 
 * Enable with: neo4j.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "neo4j.enabled", havingValue = "true", matchIfMissing = false)
@EnableNeo4jRepositories(basePackages = "com.posgateway.aml.repository.graph")
public class Neo4jConfig {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);

    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String neo4jUsername;

    @Value("${spring.neo4j.authentication.password:password}")
    private String neo4jPassword;

    @Bean
    public Driver neo4jDriver() {
        logger.info("Connecting to Neo4j Community Edition at: {}", neo4jUri);
        return GraphDatabase.driver(
                neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword));
    }

    /**
     * Declaring {@link #neo4jTransactionManager} switches off Spring Boot's auto-configured JPA
     * {@code transactionManager} (it is {@code @ConditionalOnMissingBean(TransactionManager)}), which
     * makes the whole context fail with "required a bean named 'transactionManager'" whenever
     * {@code neo4j.enabled=true} (the production compose setting). Re-declare the JPA manager as the
     * primary one so plain {@code @Transactional} keeps meaning Postgres; graph writes opt in with
     * {@code @Transactional("neo4jTransactionManager")}.
     */
    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}
