package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Aerospike Connection Manager
 * Background service that monitors and maintains Aerospike connections
 * Ensures connections stay alive and automatically reconnects if needed
 */
@Service
public class AerospikeConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeConnectionManager.class);

    private final AerospikeConnectionService connectionService;

    @Value("${aerospike.enabled:false}")
    private boolean aerospikeEnabled;

    @Value("${aerospike.connection.health.check.interval.seconds:30}")
    private int healthCheckIntervalSeconds;

    @Value("${aerospike.connection.keepalive.enabled:true}")
    private boolean keepAliveEnabled;

    @Autowired
    public AerospikeConnectionManager(AerospikeConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /**
     * Initialize connection manager
     */
    @PostConstruct
    public void initialize() {
        if (!aerospikeEnabled) {
            logger.debug("Aerospike is disabled - connection manager not started");
            return;
        }

        logger.info("Aerospike Connection Manager initialized");
        logger.info("Health check interval: {} seconds", healthCheckIntervalSeconds);
        logger.info("Keep-alive enabled: {}", keepAliveEnabled);
    }

    /**
     * Periodic health check and connection maintenance
     * Runs every configured interval (default: 30 seconds)
     */
    @Scheduled(fixedRateString = "${aerospike.connection.health.check.interval.seconds:30}000")
    public void healthCheck() {
        if (!aerospikeEnabled || !keepAliveEnabled) {
            return;
        }

        try {
            // Check connection status
            boolean isConnected = connectionService.isConnected();

            if (!isConnected) {
                logger.warn("Aerospike connection is down - attempting to reconnect...");
                
                // Try to get client which will trigger reconnection
                try {
                    connectionService.getClient();
                    logger.info("Aerospike connection restored");
                } catch (Exception e) {
                    logger.warn("Failed to restore Aerospike connection: {}", e.getMessage());
                }
            } else {
                // Connection is alive - verify with a simple operation
                verifyConnection();
            }

        } catch (Exception e) {
            logger.error("Error during Aerospike health check: {}", e.getMessage(), e);
        }
    }

    /**
     * Verify connection with a lightweight operation
     */
    private void verifyConnection() {
        try {
            // Just check if client is accessible
            // Actual operations will be done when needed
            connectionService.getClient();
        } catch (Exception e) {
            logger.debug("Connection verification check: {}", e.getMessage());
        }
    }

    /**
     * Force connection check and reconnect if needed
     * Can be called manually or by other services
     */
    public void ensureConnection() {
        if (!aerospikeEnabled) {
            return;
        }

        try {
            if (!connectionService.isConnected()) {
                logger.info("Ensuring Aerospike connection is active...");
                connectionService.getClient(); // This will trigger reconnection if needed
            }
        } catch (Exception e) {
            logger.warn("Failed to ensure Aerospike connection: {}", e.getMessage());
        }
    }

    /**
     * Get connection status
     * 
     * @return Connection status information
     */
    public AerospikeConnectionService.ConnectionStatus getConnectionStatus() {
        return connectionService.getConnectionStatus();
    }

    /**
     * Check if connection manager is active
     * 
     * @return true if active
     */
    public boolean isActive() {
        return aerospikeEnabled && keepAliveEnabled;
    }
}

