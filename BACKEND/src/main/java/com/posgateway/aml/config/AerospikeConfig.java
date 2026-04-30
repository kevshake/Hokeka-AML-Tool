package com.posgateway.aml.config;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.policy.ClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aerospike Client Configuration (Production Best Practices)
 * 
 * Key Features:
 * - Single shared client (thread-safe)
 * - Clean lifecycle management (close() on shutdown)
 * - Environment-driven configuration
 * - Connection pooling with tunable maxConnsPerNode
 * - Timeout and retry policies
 * - Optional authentication support
 */
@Configuration
@ConditionalOnProperty(name = "aerospike.enabled", havingValue = "true", matchIfMissing = false)
public class AerospikeConfig {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeConfig.class);

    @Value("${aerospike.hosts:localhost:3000}")
    private String aerospikeHosts;

    @Value("${aerospike.namespace:test}")
    private String namespace;

    @Value("${aerospike.timeoutMs:1000}")
    private int timeoutMs;

    @Value("${aerospike.user:}")
    private String user;

    @Value("${aerospike.password:}")
    private String password;

    // Connection & Performance Tuning
    @Value("${aerospike.maxConnsPerNode:300}")
    private int maxConnsPerNode;

    @Value("${aerospike.connPoolsPerNode:1}")
    private int connPoolsPerNode;

    @Value("${aerospike.maxRetries:1}")
    private int maxRetries;

    @Value("${aerospike.sleepBetweenRetries:0}")
    private int sleepBetweenRetries;

    @Value("${aerospike.failIfNotConnected:false}")
    private boolean failIfNotConnected;

    @Value("${aerospike.tendInterval:1000}")
    private int tendInterval;

    @Bean(destroyMethod = "close")
    public AerospikeClient aerospikeClient() {
        ClientPolicy policy = new ClientPolicy();
        
        // Connection Timeout
        policy.timeout = timeoutMs;
        
        // Connection & Performance Tuning (Production Best Practices)
        policy.maxConnsPerNode = maxConnsPerNode;
        policy.connPoolsPerNode = connPoolsPerNode;
        policy.failIfNotConnected = failIfNotConnected;
        policy.tendInterval = tendInterval;
        
        // Retry Configuration
        policy.readPolicyDefault.maxRetries = maxRetries;
        policy.readPolicyDefault.sleepBetweenRetries = sleepBetweenRetries;
        policy.readPolicyDefault.totalTimeout = timeoutMs;
        policy.readPolicyDefault.socketTimeout = timeoutMs;
        
        policy.writePolicyDefault.maxRetries = maxRetries;
        policy.writePolicyDefault.sleepBetweenRetries = sleepBetweenRetries;
        policy.writePolicyDefault.totalTimeout = timeoutMs;
        policy.writePolicyDefault.socketTimeout = timeoutMs;

        // Enable authentication if configured
        if (user != null && !user.isEmpty()) {
            policy.user = user;
            policy.password = password;
            logger.info("Aerospike authentication enabled for user: {}", user);
        }

        // Parse hosts (format: host:port,host:port)
        String[] hostStrings = aerospikeHosts.split(",");
        Host[] hosts = new Host[hostStrings.length];

        for (int i = 0; i < hostStrings.length; i++) {
            String[] parts = hostStrings[i].trim().split(":");
            String host = parts[0].trim();
            int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 3000;
            hosts[i] = new Host(host, port);
        }

        logger.info("Initializing Aerospike client - hosts: {}, namespace: {}, maxConnsPerNode: {}, timeout: {}ms",
                aerospikeHosts, namespace, maxConnsPerNode, timeoutMs);

        return new AerospikeClient(policy, hosts);
    }

    public String getNamespace() {
        return namespace;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
}
