package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * KYC Data Cache Service
 * Caches KYC completeness scores and risk ratings in Redis for fast lookups.
 */
@Service
public class KycDataCacheService {

    private static final Logger logger = LoggerFactory.getLogger(KycDataCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${kyc.cache.ttl.hours:12}")
    private int cacheTtlHours;

    private static final String SET_KYC_COMPLETENESS = "kyc_completeness";
    private static final String SET_RISK_RATINGS = "risk_ratings";
    private static final String SET_KYC_EXPIRATION = "kyc_expiration";

    private static String key(String set, String id) {
        return set + ":" + id;
    }

    /** Cache KYC completeness score */
    public void cacheCompletenessScore(Long merchantId, Double score) {
        String k = key(SET_KYC_COMPLETENESS, String.valueOf(merchantId));
        redisTemplate.opsForValue().set(k, score, Duration.ofHours(cacheTtlHours));
        logger.debug("Cached KYC completeness score for merchant: {} = {}", merchantId, score);
    }

    /** Get cached KYC completeness score */
    public Double getCachedCompletenessScore(Long merchantId) {
        String k = key(SET_KYC_COMPLETENESS, String.valueOf(merchantId));
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    /** Cache risk rating */
    public void cacheRiskRating(Long merchantId, String riskLevel, Double riskScore) {
        String k = key(SET_RISK_RATINGS, String.valueOf(merchantId));
        Map<String, Object> rating = new HashMap<>();
        rating.put("riskLevel", riskLevel);
        rating.put("riskScore", riskScore);
        redisTemplate.opsForValue().set(k, rating, Duration.ofHours(cacheTtlHours));
        logger.debug("Cached risk rating for merchant: {} = {} ({})", merchantId, riskLevel, riskScore);
    }

    /** Get cached risk rating */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedRiskRating(Long merchantId) {
        String k = key(SET_RISK_RATINGS, String.valueOf(merchantId));
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Map ? new HashMap<>((Map<String, Object>) v) : null;
    }

    /** Cache KYC expiration alert */
    public void cacheKycExpiration(Long merchantId, java.time.LocalDate expirationDate) {
        String k = key(SET_KYC_EXPIRATION, String.valueOf(merchantId));
        redisTemplate.opsForValue().set(k, expirationDate.toString(), Duration.ofHours(24));
        logger.debug("Cached KYC expiration for merchant: {} = {}", merchantId, expirationDate);
    }

    /** Get cached KYC expiration date */
    public String getCachedKycExpiration(Long merchantId) {
        String k = key(SET_KYC_EXPIRATION, String.valueOf(merchantId));
        Object v = redisTemplate.opsForValue().get(k);
        return v != null ? v.toString() : null;
    }

    /** Invalidate KYC data cache for merchant */
    public void invalidateKycData(Long merchantId) {
        String id = String.valueOf(merchantId);
        redisTemplate.delete(key(SET_KYC_COMPLETENESS, id));
        redisTemplate.delete(key(SET_RISK_RATINGS, id));
        redisTemplate.delete(key(SET_KYC_EXPIRATION, id));
        logger.debug("Invalidated KYC data cache for merchant: {}", merchantId);
    }
}
