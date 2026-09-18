package com.posgateway.aml.service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.features.CustomerFeatures;
import com.posgateway.aml.service.cache.FeatureCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Feature Engine Service — consumes {@code transactions.raw} and maintains
 * per-customer velocity counters in Redis via {@link FeatureCacheService}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Parse raw transaction payload from {@code transactions.raw}.</li>
 *   <li>Update velocity counters (1 h, 24 h) and last-seen timestamp in Redis.</li>
 *   <li>Update the cached feature snapshot used by the synchronous scoring path.</li>
 * </ol>
 *
 * <p>Consumer group: {@code aml-feature-engine}.
 * Consumer method is idempotent: recording the same timestamp-keyed event twice
 * leaves the Redis sorted-set score unchanged.
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class FeatureEngineService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureEngineService.class);

    private static final long WINDOW_1H_MS  =      3_600_000L;
    private static final long WINDOW_24H_MS =     86_400_000L;
    private static final long WINDOW_7D_MS  =    604_800_000L;

    private final ObjectMapper objectMapper;
    private final FeatureCacheService featureCacheService;

    public FeatureEngineService(ObjectMapper objectMapper,
                                FeatureCacheService featureCacheService) {
        this.objectMapper = objectMapper;
        this.featureCacheService = featureCacheService;
    }

    /**
     * Consume a raw transaction event and update customer velocity features.
     *
     * @param payload JSON string published by {@link com.posgateway.aml.service.TransactionIngestionService}
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_TRANSACTIONS_RAW,
            groupId = "${spring.kafka.feature-engine.group-id:aml-feature-engine}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRawTransaction(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String panHash    = optStr(root, "panHash");
            Long   pspId      = optLong(root, "pspId");
            Long   transactionId = optLong(root, "transactionId");
            Long   amountCents = optLong(root, "amountCents");

            if (panHash == null || panHash.isBlank()) {
                logger.debug("FeatureEngine: skipping event with no panHash");
                return;
            }
            if (transactionId == null) {
                throw new IllegalArgumentException("Raw transaction event has no transactionId");
            }

            long eventTimestampMs = eventTimestamp(root);

            // 1. Record this transaction timestamp in the sorted-set velocity window.
            //    Idempotent: same timestamp written twice doesn't create duplicates in zset.
            featureCacheService.recordTransactionEvent(
                    panHash, String.valueOf(transactionId), eventTimestampMs);

            // 3. Read current velocity counts from the sorted-set for the enriched event.
            long count1h  = featureCacheService.getTxCountInWindow(panHash, WINDOW_1H_MS);
            long count24h = featureCacheService.getTxCountInWindow(panHash, WINDOW_24H_MS);
            long count7d  = featureCacheService.getTxCountInWindow(panHash, WINDOW_7D_MS);

            // 4. Patch the cached CustomerFeatures object (if present) with fresh velocity data.
            featureCacheService.getFeatures(panHash).ifPresent(features -> {
                features.setTxCount1h((int) count1h);
                features.setTxCount24h((int) count24h);
                features.setTxCount7d((int) count7d);
                features.setLastTxTimestamp(LocalDateTime.now());
                if (amountCents != null) {
                    features.setLastTxAmount(amountCents.doubleValue());
                }
                features.setUpdatedAt(LocalDateTime.now());
                featureCacheService.putFeatures(panHash, features);
            });

            logger.debug("FeatureEngine: processed panHash={} pspId={} count1h={} count24h={}",
                    panHash, pspId, count1h, count24h);

        } catch (Exception e) {
            logger.error("FeatureEngine: failed to process raw transaction event: {}", e.getMessage(), e);
            throw new IllegalStateException("Feature projection failed", e);
        }
    }

    private static long eventTimestamp(JsonNode root) {
        String value = optStr(root, "transactionTimestamp");
        if (value == null || value.isBlank()) {
            return System.currentTimeMillis();
        }
        return LocalDateTime.parse(value)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String optStr(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    private static Long optLong(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && n.canConvertToLong()) ? n.asLong() : null;
    }

}
