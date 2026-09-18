package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * REST Client Service using REST Assured
 * Handles all outbound RESTful messaging
 * Optimized for 30,000+ simultaneous connections with proper cleanup
 */
@Service
public class RestClientService {

    private static final Logger logger = LoggerFactory.getLogger(RestClientService.class);

    private final ObjectMapper objectMapper;
    private final Http2FailoverService failoverService;
    private final Http2HealthMonitorService healthMonitorService;

    @Value("${http.connection.timeout:5000}")
    private int connectionTimeout;

    @Value("${http.socket.timeout:10000}")
    private int socketTimeout;

    @Value("${http.connection.request.timeout:5000}")
    private int connectionRequestTimeout;

    public RestClientService(ObjectMapper objectMapper,
                             Http2FailoverService failoverService,
                             Http2HealthMonitorService healthMonitorService) {
        this.objectMapper = objectMapper;
        this.failoverService = failoverService;
        this.healthMonitorService = healthMonitorService;
        // Connection pooling is configured globally in HttpConnectionPoolConfig
    }

    /**
     * POST request to external service
     * Optimized with connection pooling and automatic cleanup
     * 
     * @param url Service URL
     * @param payload Request payload
     * @return Response as Map
     */
    public Map<String, Object> postRequest(String url, Map<String, Object> payload) {
        logger.debug("POST request to {} with payload size: {}", url, payload != null ? payload.size() : 0);

        long startTime = System.currentTimeMillis();
        Response response = null;
        boolean useHttp2 = failoverService.shouldUseHttp2();
        
        try {
            RequestSpecification requestSpec = RestAssured.given()
                .config(RestAssured.config)
                .contentType("application/json")
                .accept("application/json");
            
            // Add HTTP/2 protocol header if enabled
            if (useHttp2) {
                requestSpec.header("Upgrade", "h2c"); // HTTP/2 clear text
            }
            
            requestSpec.body(payload);
            response = requestSpec.post(url);
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Record metrics if using HTTP/2
            if (useHttp2) {
                healthMonitorService.recordRequest(latency);
            }

            // Check status code
            int statusCode = response.getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException("POST request failed with status: " + statusCode);
            }

            // Extract and parse response
            String responseBody = response.getBody().asString();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            
            logger.debug("POST request successful to {}", url);
            return result;

        } catch (Exception e) {
            logger.error("POST request failed to {}: {}", url, e.getMessage());
            
            // Record error if using HTTP/2
            if (useHttp2) {
                healthMonitorService.recordError("REQUEST_FAILED");
                
                // Check if connection was dropped
                String errorMsg = e.getMessage().toLowerCase();
                if (errorMsg.contains("connection") || errorMsg.contains("reset") || 
                    errorMsg.contains("timeout")) {
                    healthMonitorService.recordDrop();
                }
                
                // Trigger failover if needed
                if (healthMonitorService.shouldFailover()) {
                    logger.warn("HTTP/2 health issues detected, may trigger failover");
                    failoverService.performFailover();
                }
            }
            
            throw new RuntimeException("REST request failed: " + e.getMessage(), e);
        } finally {
            // Ensure response is properly closed to release connection
            if (response != null) {
                try {
                    // Response auto-closes connection back to pool
                    response.getBody().asString(); // Ensure body is consumed
                } catch (Exception e) {
                    logger.warn("Error closing response for {}: {}", url, e.getMessage());
                }
            }
        }
    }

    /**
     * GET request to external service
     * Optimized with connection pooling and automatic cleanup
     * 
     * @param url Service URL
     * @return Response as Map
     */
    public Map<String, Object> getRequest(String url) {
        logger.debug("GET request to {}", url);

        Response response = null;
        try {
            RequestSpecification requestSpec = RestAssured.given()
                .config(RestAssured.config)
                .accept("application/json");

            response = requestSpec.get(url);

            // Check status code
            int statusCode = response.getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException("GET request failed with status: " + statusCode);
            }

            // Extract and parse response
            String responseBody = response.getBody().asString();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            
            logger.debug("GET request successful to {}", url);
            return result;

        } catch (Exception e) {
            logger.error("GET request failed to {}: {}", url, e.getMessage());
            throw new RuntimeException("REST request failed: " + e.getMessage(), e);
        } finally {
            // Ensure response is properly closed to release connection
            if (response != null) {
                try {
                    // Response auto-closes connection back to pool
                    response.getBody().asString(); // Ensure body is consumed
                } catch (Exception e) {
                    logger.warn("Error closing response for {}: {}", url, e.getMessage());
                }
            }
        }
    }

    /**
     * POST request with retry logic
     * 
     * @param url Service URL
     * @param payload Request payload
     * @param maxRetries Maximum retry attempts
     * @return Response as Map
     */
    public Map<String, Object> postRequestWithRetry(String url, Map<String, Object> payload, int maxRetries) {
        Exception lastException = null;
        
        // Early return for single attempt
        if (maxRetries <= 1) {
            return postRequest(url, payload);
        }
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return postRequest(url, payload);
            } catch (Exception e) {
                lastException = e;
                logger.warn("POST request attempt {} failed to {}: {}", attempt, url, e.getMessage());
                // Only sleep if not the last attempt
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100L * attempt); // Exponential backoff - use long literal
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Request interrupted", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("POST request failed after " + maxRetries + " attempts", lastException);
    }
}
