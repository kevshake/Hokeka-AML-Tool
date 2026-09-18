package com.hokeka.aml.config;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.policy.ClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AerospikeConfig {
    private static final Logger log = LoggerFactory.getLogger(AerospikeConfig.class);

    @Value("${aerospike.host:fd-test-aerospike}")
    private String host;

    @Value("${aerospike.port:3000}")
    private int port;

    @Bean
    public AerospikeClient aerospikeClient() {
        try {
            ClientPolicy policy = new ClientPolicy();
            policy.timeout = 2000;
            // Do NOT hard-fail bean creation if Aerospike isn't reachable at startup (common
            // when the service and Aerospike boot in parallel). With failIfNotConnected=false
            // the client is created anyway and its cluster-tend thread connects once Aerospike
            // is up, so the cache self-heals — no "cache-less until manual restart" window.
            // Every read/write is already guarded by isConnected(), so an unconnected client
            // simply behaves as a cache miss.
            policy.failIfNotConnected = false;
            AerospikeClient client = new AerospikeClient(policy, host, port);
            log.info("Aerospike client initialized for {}:{} (connected={})", host, port, client.isConnected());
            return client;
        } catch (Exception e) {
            log.warn("Aerospike client init failed ({}:{}): {} - running without cache", host, port, e.getMessage());
            return null;
        }
    }
}
