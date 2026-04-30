package com.posgateway.aml.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caches compliance-case summaries.
 *
 * <p>Originally backed by Aerospike. Aerospike was relocated to the standalone
 * aml-microservice; this service now uses an in-process Caffeine cache.
 *
 * <p>TODO(aerospike-removal): if cross-instance cache visibility is required (e.g.
 * round-robined replicas of the API server), route through AmlMicroserviceClient
 * or stand up a Redis layer instead.
 */
@Service
public class AerospikeCaseCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeCaseCacheService.class);

    private final ObjectMapper objectMapper;

    private final Cache<Long, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    @Autowired
    public AerospikeCaseCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void cacheCase(ComplianceCase cCase) {
        if (cCase == null || cCase.getId() == null) return;
        try {
            cache.put(cCase.getId(), objectMapper.writeValueAsString(cCase));
            logger.debug("Cached case {}", cCase.getCaseReference());
        } catch (Exception e) {
            logger.warn("Failed to cache case {}", cCase.getCaseReference(), e);
        }
    }

    public ComplianceCase getCachedCase(Long caseId) {
        if (caseId == null) return null;
        String json = cache.getIfPresent(caseId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, ComplianceCase.class);
        } catch (Exception e) {
            logger.warn("Failed to deserialize cached case {}", caseId, e);
            return null;
        }
    }

    public void evictCase(Long caseId) {
        if (caseId != null) cache.invalidate(caseId);
    }
}
