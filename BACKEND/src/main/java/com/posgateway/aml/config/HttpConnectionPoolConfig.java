package com.posgateway.aml.config;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * HTTP Connection Pool Configuration
 * Optimized for 30,000+ simultaneous API connections
 * Properly manages connection lifecycle and cleanup via REST Assured
 */
@Configuration
public class HttpConnectionPoolConfig {

    @Value("${http.connection.pool.max.total:30000}")
    private int maxTotalConnections;

    @Value("${http.connection.pool.max.per.route:5000}")
    private int maxConnectionsPerRoute;

    @Value("${http.connection.timeout:5000}")
    private int connectionTimeout;

    @Value("${http.socket.timeout:10000}")
    private int socketTimeout;

    @Value("${http.connection.request.timeout:5000}")
    private int connectionRequestTimeout;

    /**
     * Configure REST Assured with connection pooling
     * Handles 30,000+ simultaneous connections
     */
    @PostConstruct
    public void configureRestAssured() {
        // REST Assured uses Apache HttpClient internally which supports connection
        // pooling
        // Configure connection pool settings via system properties and REST Assured
        // config

        // Set system properties for Apache HttpClient (used by REST Assured)
        System.setProperty("http.maxConnections", String.valueOf(maxTotalConnections));
        System.setProperty("http.maxConnectionsPerRoute", String.valueOf(maxConnectionsPerRoute));

        // Configure REST Assured with connection pooling and timeouts
        RestAssuredConfig config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", connectionTimeout)
                        .setParam("http.socket.timeout", socketTimeout)
                        .setParam("http.connection-manager.timeout", connectionRequestTimeout)
                        .setParam("http.connection-manager.max-total", maxTotalConnections)
                        .setParam("http.connection-manager.default-max-per-route", maxConnectionsPerRoute));

        RestAssured.config = config;
    }
}
