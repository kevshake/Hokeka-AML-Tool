package com.hokeka.aml.config;

import com.aeroorm.AeroRepository;
import com.aerospike.client.AerospikeClient;
import com.hokeka.aml.cache.RiskProfileCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AeroOrmConfig {

    @Bean
    public AeroRepository<RiskProfileCache> riskProfileRepository(
            @Autowired(required = false) AerospikeClient aerospikeClient,
            @Value("${aerospike.namespace:aml_cache}") String namespace) {
        return new AeroRepository<>(aerospikeClient, namespace, RiskProfileCache.class);
    }
}
