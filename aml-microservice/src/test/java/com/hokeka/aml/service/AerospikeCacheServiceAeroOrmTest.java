package com.hokeka.aml.service;

import com.aeroorm.AeroRepository;
import com.aerospike.client.AerospikeClient;
import com.hokeka.aml.cache.RiskProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AerospikeCacheServiceAeroOrmTest {

    private AerospikeClient client;
    private AeroRepository<RiskProfileCache> repository;
    private AerospikeCacheService service;

    @BeforeEach
    void setUp() {
        client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        repository = mock(AeroRepository.class);
        service = new AerospikeCacheService();
        ReflectionTestUtils.setField(service, "aerospikeClient", client);
        ReflectionTestUtils.setField(service, "riskProfileRepository", repository);
        ReflectionTestUtils.setField(service, "namespace", "aml_cache");
    }

    @Test
    void putRiskProfileUsesAeroOrm() {
        Map<String, Object> profile = Map.of("score", 42L);
        service.putRiskProfile(7L, profile);
        verify(repository).saveMap(eq("7"), eq(profile), anyInt());
    }

    @Test
    void getRiskProfileUsesAeroOrm() {
        when(repository.findMap("7")).thenReturn(Map.of("score", 42L));
        Map<String, Object> found = service.getRiskProfile(7L);
        assertEquals(42L, found.get("score"));
    }
}
