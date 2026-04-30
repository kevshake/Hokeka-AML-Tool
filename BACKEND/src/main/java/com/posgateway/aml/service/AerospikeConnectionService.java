package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub kept after Aerospike removal so legacy call sites continue to compile.
 *
 * <p>Aerospike now lives entirely in the {@code aml-microservice}; this in-process
 * shim always reports {@code isEnabled() == false} and {@code isConnected() == false},
 * causing all legacy Aerospike code paths to short-circuit cleanly.
 *
 * <p>TODO(aerospike-removal): delete this class once every caller has been migrated
 * to {@link com.posgateway.aml.client.aml.AmlMicroserviceClient} or to the
 * Caffeine-backed {@code AerospikeCacheService} (which is local-only).
 */
@Service
public class AerospikeConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeConnectionService.class);

    public AerospikeConnectionService() {
        logger.info("AerospikeConnectionService is a no-op stub. Aerospike has moved to aml-microservice.");
    }

    /** Always false — Aerospike is no longer reachable from this process. */
    public boolean isEnabled() { return false; }

    /** Always false — there is no in-process Aerospike client any more. */
    public boolean isConnected() { return false; }

    /** Default sanctions namespace name (unused, retained for API compatibility). */
    public String getNamespace() { return "sanctions"; }

    public ConnectionStatus getConnectionStatus() {
        ConnectionStatus s = new ConnectionStatus();
        s.setEnabled(false);
        s.setConnected(false);
        s.setHosts("(remote: aml-microservice)");
        s.setNamespace(getNamespace());
        s.setSecurityEnabled(false);
        s.setClientInitialized(false);
        return s;
    }

    /** Snapshot DTO retained for legacy compatibility. */
    public static class ConnectionStatus {
        private boolean enabled;
        private boolean connected;
        private String hosts;
        private String namespace;
        private boolean securityEnabled;
        private boolean clientInitialized;
        private int maxConnections;
        private int connectionTimeout;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean v) { this.connected = v; }
        public String getHosts() { return hosts; }
        public void setHosts(String v) { this.hosts = v; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String v) { this.namespace = v; }
        public boolean isSecurityEnabled() { return securityEnabled; }
        public void setSecurityEnabled(boolean v) { this.securityEnabled = v; }
        public boolean isClientInitialized() { return clientInitialized; }
        public void setClientInitialized(boolean v) { this.clientInitialized = v; }
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int v) { this.maxConnections = v; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int v) { this.connectionTimeout = v; }
    }
}
