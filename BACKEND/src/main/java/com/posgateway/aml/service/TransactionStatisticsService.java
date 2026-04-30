package com.posgateway.aml.service;

import com.posgateway.aml.service.cache.AerospikeCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Transaction Statistics Service
 * Maintains transaction amounts and counts in Aerospike for fast AML velocity checks
 * Automatically tracks: merchant counts, PAN counts, amount sums, velocity metrics
 * 
 * This service keeps running totals updated in real-time for instant AML risk assessment
 * 
 * Architecture: Aerospike + PostgreSQL only (Redis removed)
 */
@Service
@SuppressWarnings("null") // String.format never returns null, Aerospike operations are safe
public class TransactionStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionStatisticsService.class);

    @Autowired(required = false)
    private AerospikeCacheService aerospikeCacheService;

    @Value("${aerospike.enabled:false}")
    private boolean aerospikeEnabled;

    @Value("${transaction.stats.key.prefix:aml:stats}")
    private String keyPrefix;

    @Value("${transaction.stats.ttl.hours:168}") // 7 days default TTL
    private int ttlHours;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String STATS_SET = "transaction_stats";

    /**
     * Record a transaction for statistics tracking
     * Updates counts and amounts in Aerospike automatically
     * 
     * @param merchantId Merchant ID
     * @param panHash PAN hash (SHA-256)
     * @param amountCents Transaction amount in cents
     * @param terminalId Terminal ID (optional)
     */
    public void recordTransaction(String merchantId, String panHash, Long amountCents, String terminalId) {
        if (amountCents == null || amountCents <= 0 || !aerospikeEnabled || aerospikeCacheService == null) {
            return; // Skip if invalid or Aerospike not available
        }

        LocalDateTime now = LocalDateTime.now();
        String dateKey = now.format(DATE_FORMATTER);
        String hourKey = now.format(HOUR_FORMATTER);

        try {
            // Update merchant statistics
            if (merchantId != null) {
                updateMerchantStats(merchantId, amountCents, dateKey, hourKey);
            }

            // Update PAN statistics
            if (panHash != null) {
                updatePanStats(panHash, amountCents, dateKey, hourKey, terminalId);
            }

        } catch (Exception e) {
            logger.warn("Error recording transaction statistics: {}", e.getMessage());
            // Don't fail transaction processing if stats fail
        }
    }

    /**
     * Get merchant transaction count for time window
     */
    public long getMerchantTransactionCount(String merchantId, int hours) {
        if (merchantId == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            if (hours <= 24) {
                String key = String.format("%s:merchant:%s:count:24h", keyPrefix, merchantId);
                Object value = aerospikeCacheService.get(STATS_SET, key);
                return value != null ? Long.parseLong(value.toString()) : 0;
            }

            // Sum hourly keys for custom windows
            long total = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < hours; i++) {
                String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                String key = String.format("%s:merchant:%s:count:%s", keyPrefix, merchantId, hourKey);
                Object value = aerospikeCacheService.get(STATS_SET, key);
                if (value != null) {
                    total += Long.parseLong(value.toString());
                }
            }
            return total;
        } catch (Exception e) {
            logger.debug("Error getting merchant count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get merchant transaction amount sum for time window
     */
    public long getMerchantAmountSum(String merchantId, int hours) {
        if (merchantId == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            if (hours <= 24) {
                String key = String.format("%s:merchant:%s:amount:24h", keyPrefix, merchantId);
                Object value = aerospikeCacheService.get(STATS_SET, key);
                return value != null ? Long.parseLong(value.toString()) : 0;
            }

            // Sum hourly keys
            long total = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < hours; i++) {
                String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                String key = String.format("%s:merchant:%s:amount:%s", keyPrefix, merchantId, hourKey);
                Object value = aerospikeCacheService.get(STATS_SET, key);
                if (value != null) {
                    total += Long.parseLong(value.toString());
                }
            }
            return total;
        } catch (Exception e) {
            logger.debug("Error getting merchant amount: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get PAN transaction count for time window
     */
    public long getPanTransactionCount(String panHash, int hours) {
        if (panHash == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            String key;
            switch (hours) {
                case 1:
                    key = String.format("%s:pan:%s:count:1h", keyPrefix, panHash);
                    break;
                case 24:
                    key = String.format("%s:pan:%s:count:24h", keyPrefix, panHash);
                    break;
                default:
                    // Sum hourly keys
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < hours && i < 168; i++) { // Max 7 days
                        String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                        String k = String.format("%s:pan:%s:count:%s", keyPrefix, panHash, hourKey);
                        Object value = aerospikeCacheService.get(STATS_SET, k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }
            
            Object value = aerospikeCacheService.get(STATS_SET, key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get PAN transaction amount sum for time window
     */
    public long getPanAmountSum(String panHash, int hours) {
        if (panHash == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            String key;
            switch (hours) {
                case 1:
                    key = String.format("%s:pan:%s:amount:1h", keyPrefix, panHash);
                    break;
                case 24:
                    key = String.format("%s:pan:%s:amount:24h", keyPrefix, panHash);
                    break;
                default:
                    // Sum hourly keys
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < hours && i < 168; i++) {
                        String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                        String k = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, hourKey);
                        Object value = aerospikeCacheService.get(STATS_SET, k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }
            
            Object value = aerospikeCacheService.get(STATS_SET, key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN amount: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get cumulative amount for PAN over days
     */
    public long getPanCumulativeAmount(String panHash, int days) {
        if (panHash == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            String key;
            switch (days) {
                case 7:
                    key = String.format("%s:pan:%s:amount:7d", keyPrefix, panHash);
                    break;
                case 30:
                    key = String.format("%s:pan:%s:amount:30d", keyPrefix, panHash);
                    break;
                default:
                    // Sum daily keys
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < days && i < 90; i++) {
                        String dateKey = now.minusDays(i).format(DATE_FORMATTER);
                        String k = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, dateKey);
                        Object value = aerospikeCacheService.get(STATS_SET, k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }
            
            Object value = aerospikeCacheService.get(STATS_SET, key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN cumulative: {}", e.getMessage());
            return 0;
        }
    }

    // ==================== Aerospike Implementation ====================

    private void updateMerchantStats(String merchantId, Long amountCents, String dateKey, String hourKey) {
        int ttlSeconds = ttlHours * 3600;

        // Hourly counters
        String hourCountKey = String.format("%s:merchant:%s:count:%s", keyPrefix, merchantId, hourKey);
        String hourAmountKey = String.format("%s:merchant:%s:amount:%s", keyPrefix, merchantId, hourKey);
        
        incrementCounter(hourCountKey, 1L, ttlSeconds);
        incrementCounter(hourAmountKey, amountCents, ttlSeconds);

        // Daily counters
        String dayCountKey = String.format("%s:merchant:%s:count:%s", keyPrefix, merchantId, dateKey);
        String dayAmountKey = String.format("%s:merchant:%s:amount:%s", keyPrefix, merchantId, dateKey);
        
        incrementCounter(dayCountKey, 1L, ttlSeconds);
        incrementCounter(dayAmountKey, amountCents, ttlSeconds);

        // Rolling 24h window (for fast lookup)
        String rolling24hCountKey = String.format("%s:merchant:%s:count:24h", keyPrefix, merchantId);
        String rolling24hAmountKey = String.format("%s:merchant:%s:amount:24h", keyPrefix, merchantId);
        
        incrementCounter(rolling24hCountKey, 1L, 25 * 3600); // 25 hours TTL
        incrementCounter(rolling24hAmountKey, amountCents, 25 * 3600);
    }

    private void updatePanStats(String panHash, Long amountCents, String dateKey, String hourKey, String terminalId) {
        int ttlSeconds = ttlHours * 3600;

        // Hourly counters
        String hourCountKey = String.format("%s:pan:%s:count:%s", keyPrefix, panHash, hourKey);
        String hourAmountKey = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, hourKey);
        
        incrementCounter(hourCountKey, 1L, ttlSeconds);
        incrementCounter(hourAmountKey, amountCents, ttlSeconds);

        // Daily counters
        String dayCountKey = String.format("%s:pan:%s:count:%s", keyPrefix, panHash, dateKey);
        String dayAmountKey = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, dateKey);
        
        incrementCounter(dayCountKey, 1L, ttlSeconds);
        incrementCounter(dayAmountKey, amountCents, ttlSeconds);

        // Rolling windows
        incrementCounter(String.format("%s:pan:%s:count:1h", keyPrefix, panHash), 1L, 2 * 3600);
        incrementCounter(String.format("%s:pan:%s:amount:1h", keyPrefix, panHash), amountCents, 2 * 3600);
        
        incrementCounter(String.format("%s:pan:%s:count:24h", keyPrefix, panHash), 1L, 25 * 3600);
        incrementCounter(String.format("%s:pan:%s:amount:24h", keyPrefix, panHash), amountCents, 25 * 3600);
        
        incrementCounter(String.format("%s:pan:%s:count:7d", keyPrefix, panHash), 1L, 8 * 24 * 3600);
        incrementCounter(String.format("%s:pan:%s:amount:7d", keyPrefix, panHash), amountCents, 8 * 24 * 3600);
        
        incrementCounter(String.format("%s:pan:%s:count:30d", keyPrefix, panHash), 1L, 31 * 24 * 3600);
        incrementCounter(String.format("%s:pan:%s:amount:30d", keyPrefix, panHash), amountCents, 31 * 24 * 3600);

        // Track distinct terminals using a map
        if (terminalId != null) {
            String terminalKey = String.format("%s:pan:%s:terminals:30d", keyPrefix, panHash);
            Map<String, Object> terminals = aerospikeCacheService.getMap(STATS_SET, terminalKey);
            if (terminals == null) {
                terminals = new HashMap<>();
            }
            terminals.put(terminalId, "1");
            aerospikeCacheService.putMap(STATS_SET, terminalKey, terminals, 31 * 24 * 3600);
        }
    }

    /**
     * Increment a counter in Aerospike
     */
    private void incrementCounter(String key, Long incrementBy, int ttlSeconds) {
        try {
            Object currentValue = aerospikeCacheService.get(STATS_SET, key);
            long newValue = (currentValue != null ? Long.parseLong(currentValue.toString()) : 0) + incrementBy;
            aerospikeCacheService.put(STATS_SET, key, newValue, ttlSeconds);
        } catch (Exception e) {
            logger.debug("Error incrementing counter {}: {}", key, e.getMessage());
        }
    }

    /**
     * Get distinct terminal count for PAN
     */
    public long getDistinctTerminalCount(String panHash, int days) {
        if (panHash == null || !aerospikeEnabled || aerospikeCacheService == null) {
            return 0;
        }

        try {
            String key = String.format("%s:pan:%s:terminals:%dd", keyPrefix, panHash, days);
            Map<String, Object> terminals = aerospikeCacheService.getMap(STATS_SET, key);
            return terminals != null ? terminals.size() : 0;
        } catch (Exception e) {
            logger.debug("Error getting distinct terminal count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Clear statistics (for testing or reset)
     */
    public void clearStatistics(String merchantId, String panHash) {
        if (!aerospikeEnabled || aerospikeCacheService == null) {
            return;
        }
        
        try {
            // Note: Aerospike doesn't support wildcard deletes like Redis
            // We would need to track keys or implement a different approach
            // For now, this is a placeholder
            logger.warn("Clear statistics not fully implemented for Aerospike - requires key tracking");
        } catch (Exception e) {
            logger.warn("Error clearing statistics: {}", e.getMessage());
        }
    }
}
