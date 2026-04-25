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
            AerospikeClient client = new AerospikeClient(policy, host, port);
            log.info("Aerospike client connected to {}:{}", host, port);
            return client;
        } catch (Exception e) {
            log.warn("Aerospike connection failed ({}:{}): {} - running without cache", host, port, e.getMessage());
            return null;
        }
    }
}
