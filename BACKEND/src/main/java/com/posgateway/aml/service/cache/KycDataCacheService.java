package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * KYC Data Cache Service
 * Caches KYC completeness scores and risk ratings in Aerospike for fast lookups
 */
@Service
public class KycDataCacheService {

    private static final Logger logger = LoggerFactory.getLogger(KycDataCacheService.class);

    @Autowired
    private AerospikeCacheService cacheService;

    @Value("${kyc.cache.ttl.hours:12}")
    private int cacheTtlHours;

    private static final String SET_KYC_COMPLETENESS = "kyc_completeness";
    private static final String SET_RISK_RATINGS = "risk_ratings";
    private static final String SET_KYC_EXPIRATION = "kyc_expiration";

    /**
     * Cache KYC completeness score
     */
    public void cacheCompletenessScore(Long merchantId, Double score) {
        String key = String.valueOf(merchantId);
        cacheService.put(SET_KYC_COMPLETENESS, key, score, (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached KYC completeness score for merchant: {} = {}", merchantId, score);
    }

    /**
     * Get cached KYC completeness score
     */
    public Double getCachedCompletenessScore(Long merchantId) {
        String key = String.valueOf(merchantId);
        return cacheService.get(SET_KYC_COMPLETENESS, key, Double.class);
    }

    /**
     * Cache risk rating
     */
    public void cacheRiskRating(Long merchantId, String riskLevel, Double riskScore) {
        String key = String.valueOf(merchantId);
        java.util.Map<String, Object> rating = new java.util.HashMap<>();
        rating.put("riskLevel", riskLevel);
        rating.put("riskScore", riskScore);
        cacheService.putMap(SET_RISK_RATINGS, key, rating, (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached risk rating for merchant: {} = {} ({})", merchantId, riskLevel, riskScore);
    }

    /**
     * Get cached risk rating
     */
    public java.util.Map<String, Object> getCachedRiskRating(Long merchantId) {
        String key = String.valueOf(merchantId);
        return cacheService.getMap(SET_RISK_RATINGS, key);
    }

    /**
     * Cache KYC expiration alert
     */
    public void cacheKycExpiration(Long merchantId, java.time.LocalDate expirationDate) {
        String key = String.valueOf(merchantId);
        cacheService.put(SET_KYC_EXPIRATION, key, expirationDate.toString(), 
                (int) TimeUnit.HOURS.toSeconds(24));
        logger.debug("Cached KYC expiration for merchant: {} = {}", merchantId, expirationDate);
    }

    /**
     * Get cached KYC expiration date
     */
    public String getCachedKycExpiration(Long merchantId) {
        String key = String.valueOf(merchantId);
        return cacheService.get(SET_KYC_EXPIRATION, key, String.class);
    }

    /**
     * Invalidate KYC data cache for merchant
     */
    public void invalidateKycData(Long merchantId) {
        String key = String.valueOf(merchantId);
        cacheService.delete(SET_KYC_COMPLETENESS, key);
        cacheService.delete(SET_RISK_RATINGS, key);
        cacheService.delete(SET_KYC_EXPIRATION, key);
        logger.debug("Invalidated KYC data cache for merchant: {}", merchantId);
    }
}

