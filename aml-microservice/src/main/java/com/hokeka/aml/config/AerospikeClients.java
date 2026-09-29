package com.hokeka.aml.config;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.policy.ClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that never returns a null Aerospike client. Init failure yields a
 * disabled sentinel whose {@code isConnected()} is always false so screening
 * stays UNAVAILABLE (never CLEAR).
 */
public final class AerospikeClients {

    private static final Logger log = LoggerFactory.getLogger(AerospikeClients.class);

    private AerospikeClients() {
    }

    public static AerospikeClient connectOrDisabled(String host, int port) {
        try {
            ClientPolicy policy = new ClientPolicy();
            policy.timeout = 2000;
            policy.failIfNotConnected = false;
            AerospikeClient client = new AerospikeClient(policy, host, port);
            log.info("Aerospike client initialized for {}:{} (connected={})", host, port, client.isConnected());
            return client;
        } catch (Exception e) {
            log.warn("Aerospike client init failed ({}:{}): {} - using disabled sentinel",
                    host, port, e.getMessage());
            return disabled();
        }
    }

    public static AerospikeClient disabled() {
        try {
            ClientPolicy policy = new ClientPolicy();
            policy.failIfNotConnected = false;
            policy.timeout = 1;
            policy.maxConnsPerNode = 1;
            return new AerospikeClient(policy, new Host("127.0.0.1", 1));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to construct disabled Aerospike sentinel", e);
        }
    }
}
