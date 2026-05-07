package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Transaction Statistics Service
 * Maintains transaction amounts and counts in Redis for fast AML velocity checks.
 * Automatically tracks: merchant counts, PAN counts, amount sums, velocity metrics.
 *
 * This service keeps running totals updated in real-time for instant AML risk assessment.
 *
 * Architecture: Redis + PostgreSQL.
 */
@Service
public class TransactionStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionStatisticsService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${transaction.stats.key.prefix:aml:stats}")
    private String keyPrefix;

    @Value("${transaction.stats.ttl.hours:168}") // 7 days default TTL
    private int ttlHours;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /**
     * Record a transaction for statistics tracking.
     * Updates counts and amounts in Redis automatically.
     *
     * @param merchantId Merchant ID
     * @param panHash PAN hash (SHA-256)
     * @param amountCents Transaction amount in cents
     * @param terminalId Terminal ID (optional)
     */
    public void recordTransaction(String merchantId, String panHash, Long amountCents, String terminalId) {
        if (amountCents == null || amountCents <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String dateKey = now.format(DATE_FORMATTER);
        String hourKey = now.format(HOUR_FORMATTER);

        try {
            if (merchantId != null) {
                updateMerchantStats(merchantId, amountCents, dateKey, hourKey);
            }
            if (panHash != null) {
                updatePanStats(panHash, amountCents, dateKey, hourKey, terminalId);
            }
        } catch (Exception e) {
            logger.warn("Error recording transaction statistics: {}", e.getMessage());
            // Don't fail transaction processing if stats fail
        }
    }

    /** Get merchant transaction count for time window */
    public long getMerchantTransactionCount(String merchantId, int hours) {
        if (merchantId == null) {
            return 0;
        }

        try {
            if (hours <= 24) {
                String key = String.format("%s:merchant:%s:count:24h", keyPrefix, merchantId);
                Object value = redisTemplate.opsForValue().get(key);
                return value != null ? Long.parseLong(value.toString()) : 0;
            }

            long total = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < hours; i++) {
                String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                String key = String.format("%s:merchant:%s:count:%s", keyPrefix, merchantId, hourKey);
                Object value = redisTemplate.opsForValue().get(key);
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

    /** Get merchant transaction amount sum for time window */
    public long getMerchantAmountSum(String merchantId, int hours) {
        if (merchantId == null) {
            return 0;
        }

        try {
            if (hours <= 24) {
                String key = String.format("%s:merchant:%s:amount:24h", keyPrefix, merchantId);
                Object value = redisTemplate.opsForValue().get(key);
                return value != null ? Long.parseLong(value.toString()) : 0;
            }

            long total = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < hours; i++) {
                String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                String key = String.format("%s:merchant:%s:amount:%s", keyPrefix, merchantId, hourKey);
                Object value = redisTemplate.opsForValue().get(key);
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

    /** Get PAN transaction count for time window */
    public long getPanTransactionCount(String panHash, int hours) {
        if (panHash == null) {
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
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < hours && i < 168; i++) {
                        String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                        String k = String.format("%s:pan:%s:count:%s", keyPrefix, panHash, hourKey);
                        Object value = redisTemplate.opsForValue().get(k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }

            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN count: {}", e.getMessage());
            return 0;
        }
    }

    /** Get PAN transaction amount sum for time window */
    public long getPanAmountSum(String panHash, int hours) {
        if (panHash == null) {
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
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < hours && i < 168; i++) {
                        String hourKey = now.minusHours(i).format(HOUR_FORMATTER);
                        String k = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, hourKey);
                        Object value = redisTemplate.opsForValue().get(k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }

            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN amount: {}", e.getMessage());
            return 0;
        }
    }

    /** Get cumulative amount for PAN over days */
    public long getPanCumulativeAmount(String panHash, int days) {
        if (panHash == null) {
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
                    long total = 0;
                    LocalDateTime now = LocalDateTime.now();
                    for (int i = 0; i < days && i < 90; i++) {
                        String dateKey = now.minusDays(i).format(DATE_FORMATTER);
                        String k = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, dateKey);
                        Object value = redisTemplate.opsForValue().get(k);
                        if (value != null) {
                            total += Long.parseLong(value.toString());
                        }
                    }
                    return total;
            }

            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (Exception e) {
            logger.debug("Error getting PAN cumulative: {}", e.getMessage());
            return 0;
        }
    }

    // ==================== Update Helpers ====================

    private void updateMerchantStats(String merchantId, Long amountCents, String dateKey, String hourKey) {
        int ttlSeconds = ttlHours * 3600;

        String hourCountKey = String.format("%s:merchant:%s:count:%s", keyPrefix, merchantId, hourKey);
        String hourAmountKey = String.format("%s:merchant:%s:amount:%s", keyPrefix, merchantId, hourKey);
        incrementCounter(hourCountKey, 1L, ttlSeconds);
        incrementCounter(hourAmountKey, amountCents, ttlSeconds);

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

        String hourCountKey = String.format("%s:pan:%s:count:%s", keyPrefix, panHash, hourKey);
        String hourAmountKey = String.format("%s:pan:%s:amount:%s", keyPrefix, panHash, hourKey);
        incrementCounter(hourCountKey, 1L, ttlSeconds);
        incrementCounter(hourAmountKey, amountCents, ttlSeconds);

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

        // Track distinct terminals using a hash field per terminal (set-like semantics, O(1) per terminal)
        if (terminalId != null) {
            String terminalKey = String.format("%s:pan:%s:terminals:30d", keyPrefix, panHash);
            redisTemplate.opsForHash().put(terminalKey, terminalId, "1");
            redisTemplate.expire(terminalKey, Duration.ofSeconds(31 * 24 * 3600));
        }
    }

    /** Increment a counter in Redis (read-modify-write to mirror prior behavior). */
    private void incrementCounter(String key, Long incrementBy, int ttlSeconds) {
        try {
            Object currentValue = redisTemplate.opsForValue().get(key);
            long newValue = (currentValue != null ? Long.parseLong(currentValue.toString()) : 0) + incrementBy;
            redisTemplate.opsForValue().set(key, newValue, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            logger.debug("Error incrementing counter {}: {}", key, e.getMessage());
        }
    }

    /** Get distinct terminal count for PAN */
    public long getDistinctTerminalCount(String panHash, int days) {
        if (panHash == null) {
            return 0;
        }

        try {
            String key = String.format("%s:pan:%s:terminals:%dd", keyPrefix, panHash, days);
            Long size = redisTemplate.opsForHash().size(key);
            return size != null ? size : 0;
        } catch (Exception e) {
            logger.debug("Error getting distinct terminal count: {}", e.getMessage());
            return 0;
        }
    }

    /** Clear statistics (for testing or reset). */
    public void clearStatistics(String merchantId, String panHash) {
        try {
            // Wildcard SCAN-based deletes are intentionally not used here. To fully reset
            // counters, callers should explicitly delete known keys or flush via redis-cli.
            logger.warn("Clear statistics not fully implemented - requires explicit key tracking");
        } catch (Exception e) {
            logger.warn("Error clearing statistics: {}", e.getMessage());
        }
    }
}
