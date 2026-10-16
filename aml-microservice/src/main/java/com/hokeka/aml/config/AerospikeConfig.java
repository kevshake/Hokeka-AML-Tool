package com.hokeka.aml.config;

import com.aerospike.client.AerospikeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AerospikeConfig {

    @Value("${aerospike.host:fd-test-aerospike}")
    private String host;

    @Value("${aerospike.port:3000}")
    private int port;

    @Bean
    public AerospikeClient aerospikeClient() {
        return AerospikeClients.connectOrDisabled(host, port);
    }
}
