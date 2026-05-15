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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Feature Engine Service — consumes {@code transactions.raw} and maintains
 * per-customer velocity counters in Redis via {@link FeatureCacheService}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Parse raw transaction payload from {@code transactions.raw}.</li>
 *   <li>Update velocity counters (1 h, 24 h) and last-seen timestamp in Redis.</li>
 *   <li>Re-publish an enriched event to {@code transactions.enriched} with the
 *       current velocity snapshot attached.</li>
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
    private final KafkaTemplate<String, String> kafkaTemplate;

    public FeatureEngineService(ObjectMapper objectMapper,
                                FeatureCacheService featureCacheService,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.featureCacheService = featureCacheService;
        this.kafkaTemplate = kafkaTemplate;
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
            String merchantId = optStr(root, "merchantId");
            Long   amountCents = optLong(root, "amountCents");
            String channelType = optStr(root, "channelType");

            if (panHash == null || panHash.isBlank()) {
                logger.debug("FeatureEngine: skipping event with no panHash");
                return;
            }

            long nowMs = System.currentTimeMillis();

            // 1. Record this transaction timestamp in the sorted-set velocity window.
            //    Idempotent: same timestamp written twice doesn't create duplicates in zset.
            featureCacheService.recordTransaction(panHash, nowMs, amountCents != null ? amountCents : 0);

            // 2. Increment simple counters for 1 h and 24 h windows.
            featureCacheService.incrementCounter(panHash, "tx.count.1h",  3_600L);
            featureCacheService.incrementCounter(panHash, "tx.count.24h", 86_400L);

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

            // 5. Publish enriched event (fire-and-forget).
            publishEnrichedEvent(root, panHash, pspId, merchantId, channelType,
                                 amountCents, count1h, count24h, count7d);

            logger.debug("FeatureEngine: processed panHash={} pspId={} count1h={} count24h={}",
                    panHash, pspId, count1h, count24h);

        } catch (Exception e) {
            logger.error("FeatureEngine: failed to process raw transaction event: {}", e.getMessage(), e);
        }
    }

    private void publishEnrichedEvent(JsonNode original, String panHash, Long pspId,
                                      String merchantId, String channelType,
                                      Long amountCents, long count1h, long count24h, long count7d) {
        try {
            Map<String, Object> enriched = new HashMap<>();
            // Carry through original fields
            copyLong(original,   enriched, "transactionId");
            copyStr(original,    enriched, "currencyCode");
            copyStr(original,    enriched, "transactionTimestamp");
            copyStr(original,    enriched, "riskLevel");
            copyStr(original,    enriched, "decision");
            enriched.put("panHash",    panHash);
            enriched.put("pspId",      pspId);
            enriched.put("merchantId", merchantId);
            enriched.put("channelType", channelType);
            enriched.put("amountCents", amountCents);
            // Velocity snapshot
            enriched.put("velocityCount1h",  count1h);
            enriched.put("velocityCount24h", count24h);
            enriched.put("velocityCount7d",  count7d);
            enriched.put("enrichedAt", LocalDateTime.now().toString());

            String enrichedPayload = objectMapper.writeValueAsString(enriched);
            String partitionKey = pspId != null ? String.valueOf(pspId) : "0";
            kafkaTemplate.send(KafkaConfig.TOPIC_TRANSACTIONS_ENRICHED, partitionKey, enrichedPayload);
        } catch (Exception e) {
            logger.warn("FeatureEngine: failed to publish enriched event: {}", e.getMessage());
        }
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

    private static void copyStr(JsonNode src, Map<String, Object> dst, String field) {
        JsonNode n = src.get(field);
        if (n != null && !n.isNull()) dst.put(field, n.asText());
    }

    private static void copyLong(JsonNode src, Map<String, Object> dst, String field) {
        JsonNode n = src.get(field);
        if (n != null && n.canConvertToLong()) dst.put(field, n.asLong());
    }
}
