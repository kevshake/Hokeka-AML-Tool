package com.posgateway.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Aerospike Connection Service
 * Singleton service that provides application-wide access to Aerospike client
 * Manages connection lifecycle and ensures connections stay alive
 */
@Service
public class AerospikeConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeConnectionService.class);

    @Value("${aerospike.enabled:false}")
    private boolean aerospikeEnabled;

    @Value("${aerospike.hosts:localhost:3000}")
    private String aerospikeHosts;

    @Value("${aerospike.namespace:sanctions}")
    private String namespace;

    @Value("${aerospike.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${aerospike.username:}")
    private String username;

    @Value("${aerospike.password:}")
    private String password;

    @Value("${aerospike.connection.timeout.ms:${aerospike.connection.timeout:5000}}")
    private int connectionTimeout;

    @Value("${aerospike.max.socket.idle:55}")
    private int maxSocketIdle;

    @Value("${aerospike.max.connections:300}")
    private int maxConnections;

    @Value("${aerospike.tend.interval:1000}")
    private int tendInterval;

    private AerospikeClient aerospikeClient;
    @SuppressWarnings("unused")
    private volatile boolean isConnected = false;

    /**
     * Initialize Aerospike client connection
     * Called automatically after bean construction
     */
    @PostConstruct
    public void initialize() {
        if (!aerospikeEnabled) {
            logger.info("Aerospike is disabled - connection service not initialized");
            return;
        }

        try {
            logger.info("Initializing Aerospike connection service...");
            logger.info("Aerospike Configuration: hosts={}, namespace={}, timeout={}ms, maxConnections={}", 
                aerospikeHosts, namespace, connectionTimeout, maxConnections);

            // Configure client policy
            ClientPolicy clientPolicy = new ClientPolicy();
            clientPolicy.timeout = connectionTimeout;
            clientPolicy.maxSocketIdle = maxSocketIdle;
            clientPolicy.tendInterval = tendInterval;
            clientPolicy.maxConnsPerNode = maxConnections;
            clientPolicy.failIfNotConnected = false; // Don't fail on startup if server is down

            // Set authentication only if security is enabled AND credentials are provided
            if (securityEnabled) {
                if (username != null && !username.trim().isEmpty() &&
                        password != null && !password.trim().isEmpty()) {
                    clientPolicy.user = username.trim();
                    clientPolicy.password = password.trim();
                    logger.info("Aerospike security enabled - authentication configured for user: {}", username);
                } else {
                    logger.warn("Aerospike security is enabled but username/password are not provided - connection may fail");
                }
            } else {
                logger.info("Aerospike security disabled - connecting without authentication");
            }

            // Parse hosts
            Host[] hostArray = parseHosts(aerospikeHosts);

            // Create Aerospike client (singleton instance)
            aerospikeClient = new AerospikeClient(clientPolicy, hostArray);

            // Verify connection
            if (aerospikeClient.isConnected()) {
                isConnected = true;
                logger.info("✅ Aerospike connection established successfully to hosts: {} (namespace: {})", 
                    aerospikeHosts, namespace);
            } else {
                logger.warn("⚠️ Aerospike client created but not connected to hosts: {} (will retry on first use)", 
                    aerospikeHosts);
            }

        } catch (AerospikeException e) {
            logger.error("Failed to initialize Aerospike connection: {}", e.getMessage(), e);
            isConnected = false;
        } catch (Exception e) {
            logger.error("Unexpected error initializing Aerospike connection: {}", e.getMessage(), e);
            isConnected = false;
        }
    }

    /**
     * Get Aerospike client instance (singleton)
     * 
     * @return Aerospike client instance
     * @throws IllegalStateException if Aerospike is disabled or not connected
     */
    public AerospikeClient getClient() {
        if (!aerospikeEnabled) {
            throw new IllegalStateException("Aerospike is disabled. Enable it in application.properties");
        }

        if (aerospikeClient == null) {
            throw new IllegalStateException("Aerospike client is not initialized");
        }

        // Check connection status and reconnect if needed
        if (!isConnected() && !checkAndReconnect()) {
            throw new IllegalStateException("Aerospike client is not connected. Check server availability.");
        }

        return aerospikeClient;
    }

    /**
     * Check if Aerospike is enabled
     * 
     * @return true if Aerospike is enabled
     */
    public boolean isEnabled() {
        return aerospikeEnabled;
    }

    /**
     * Check if Aerospike client is connected
     * 
     * @return true if connected
     */
    public boolean isConnected() {
        if (!aerospikeEnabled || aerospikeClient == null) {
            return false;
        }

        try {
            return aerospikeClient.isConnected();
        } catch (Exception e) {
            logger.debug("Error checking Aerospike connection status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check connection and reconnect if needed
     * 
     * @return true if connected or reconnected successfully
     */
    private boolean checkAndReconnect() {
        try {
            if (aerospikeClient != null && aerospikeClient.isConnected()) {
                isConnected = true;
                return true;
            }

            // Attempt reconnection
            logger.info("Attempting to reconnect to Aerospike...");
            Host[] hostArray = parseHosts(aerospikeHosts);

            // Close existing client if any
            if (aerospikeClient != null) {
                try {
                    aerospikeClient.close();
                } catch (Exception e) {
                    logger.debug("Error closing existing Aerospike client: {}", e.getMessage());
                }
            }

            // Create new client
            ClientPolicy clientPolicy = new ClientPolicy();
            clientPolicy.timeout = connectionTimeout;
            clientPolicy.maxSocketIdle = maxSocketIdle;
            clientPolicy.tendInterval = tendInterval;
            clientPolicy.maxConnsPerNode = maxConnections;
            clientPolicy.failIfNotConnected = false;

            // Set authentication only if security is enabled AND credentials are provided
            if (securityEnabled && username != null && !username.trim().isEmpty() &&
                    password != null && !password.trim().isEmpty()) {
                clientPolicy.user = username.trim();
                clientPolicy.password = password.trim();
            }

            aerospikeClient = new AerospikeClient(clientPolicy, hostArray);

            if (aerospikeClient.isConnected()) {
                isConnected = true;
                logger.info("Successfully reconnected to Aerospike");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.warn("Failed to reconnect to Aerospike: {}", e.getMessage());
            isConnected = false;
            return false;
        }
    }

    /**
     * Parse hosts string into array
     * Format: "host1:port1,host2:port2" or "host1:port1"
     * 
     * @param hostsString Hosts string
     * @return Array of host strings
     */
    private Host[] parseHosts(String hostsString) {
        if (hostsString == null || hostsString.trim().isEmpty()) {
            return new Host[] { new Host("localhost", 3000) };
        }

        String[] hostStrings = hostsString.split(",");
        Host[] hosts = new Host[hostStrings.length];

        for (int i = 0; i < hostStrings.length; i++) {
            String[] parts = hostStrings[i].split(":");
            String host = parts[0].trim();
            int port = 3000; // Default port
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port in host string '{}', using default 3000", hostStrings[i]);
                }
            }
            hosts[i] = new Host(host, port);
        }
        return hosts;
    }

    /**
     * Cleanup on shutdown
     * Closes Aerospike client connection
     */
    @PreDestroy
    public void shutdown() {
        if (aerospikeClient != null) {
            try {
                logger.info("Closing Aerospike connection...");
                aerospikeClient.close();
                isConnected = false;
                logger.info("Aerospike connection closed successfully");
            } catch (Exception e) {
                logger.error("Error closing Aerospike connection: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Get default namespace for sanctions screening
     * 
     * @return Namespace name
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get connection status information
     * 
     * @return Connection status details
     */
    public ConnectionStatus getConnectionStatus() {
        ConnectionStatus status = new ConnectionStatus();
        status.setEnabled(aerospikeEnabled);
        status.setConnected(isConnected());
        status.setHosts(aerospikeHosts);
        status.setNamespace(namespace);
        status.setSecurityEnabled(securityEnabled);
        status.setClientInitialized(aerospikeClient != null);
        status.setMaxConnections(maxConnections);
        status.setConnectionTimeout(connectionTimeout);

        return status;
    }

    /**
     * Connection Status DTO
     */
    public static class ConnectionStatus {
        private boolean enabled;
        private boolean connected;
        private String hosts;
        private String namespace;
        private boolean securityEnabled;
        private boolean clientInitialized;
        private int maxConnections;
        private int connectionTimeout;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public String getHosts() {
            return hosts;
        }

        public void setHosts(String hosts) {
            this.hosts = hosts;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public boolean isSecurityEnabled() {
            return securityEnabled;
        }

        public void setSecurityEnabled(boolean securityEnabled) {
            this.securityEnabled = securityEnabled;
        }

        public boolean isClientInitialized() {
            return clientInitialized;
        }

        public void setClientInitialized(boolean clientInitialized) {
            this.clientInitialized = clientInitialized;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }
}
