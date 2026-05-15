package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit Log Service
 * Handles the creation and storage of immutable audit logs
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private static final List<String> PROD_PROFILES = Arrays.asList("prod", "production");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${audit.hmac.key:}")
    private String hmacKey;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           Environment environment,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Validate the HMAC signing key is present at startup.
     *
     * <p>In {@code prod}/{@code production}, a missing or blank {@code AUDIT_HMAC_KEY}
     * is a fatal misconfiguration: audit-log checksums would be forgeable / reproducible
     * by anyone with source access. Fail-fast with {@link IllegalStateException} so the
     * boot aborts and the operator sees a loud error.
     *
     * <p>In any other profile (including {@code dev} and {@code testenv}), a blank key
     * is replaced by a randomly-generated per-boot secret with a WARN log so local
     * development is not blocked. The key is NOT persisted — restarts get a fresh one,
     * which is fine for dev/testenv since checksums are only validated in long-lived
     * environments.
     */
    @PostConstruct
    void validateHmacKey() {
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PROD_PROFILES::contains);
        if (hmacKey != null && !hmacKey.isBlank()) {
            return;
        }
        if (isProd) {
            throw new IllegalStateException(
                    "audit.hmac.key (env AUDIT_HMAC_KEY) is required in prod/production but was blank. " +
                    "Set a long random secret and restart. Refusing to boot with a forgeable audit-log key.");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.hmacKey = Base64.getEncoder().encodeToString(random);
        logger.warn("AUDIT_HMAC_KEY is blank in non-prod profile {} — generated an ephemeral per-boot key. " +
                        "DO NOT rely on audit-log checksum validity across restarts.",
                Arrays.toString(environment.getActiveProfiles()));
    }

    /**
     * Log an action asynchronously
     */
    @Async
    @Transactional
    public void logAction(User user, String actionType, String entityType, String entityId,
            Object beforeState, Object afterState, String ipAddress, String reason) {
        try {
            String beforeJson = beforeState != null ? objectMapper.writeValueAsString(beforeState) : null;
            String afterJson = afterState != null ? objectMapper.writeValueAsString(afterState) : null;
            
            Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : 0L;

            AuditLog log = AuditLog.builder()
                    .userId(user != null ? user.getId().toString() : "SYSTEM")
                    .username(user != null ? user.getUsername() : "SYSTEM")
                    .userRole(user != null ? user.getRole().getName() : "SYSTEM")
                    .pspId(pspId)
                    .actionType(actionType)
                    .entityType(entityType)
                    .entityId(entityId)
                    .beforeValue(beforeJson)
                    .afterValue(afterJson)
                    .timestamp(LocalDateTime.now())
                    .ipAddress(ipAddress)
                    .reason(reason)
                    .success(true)
                    .build();

            // Calculate checksum for immutability
            String contentToHash = log.getUserId() + log.getActionType() + log.getEntityType() +
                    log.getEntityId() + log.getTimestamp().toString();
            String checksum = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, hmacKey).hmacHex(contentToHash);
            log.setChecksum(checksum);

            auditLogRepository.save(log);

            // Mirror significant data-modification events to the audit Kafka topic.
            // Fire-and-forget: Kafka failure must not disrupt the audit DB write already completed.
            publishAuditEvent(log);

        } catch (Exception e) {
            logger.error("Failed to create audit log", e);
            // Don't rethrow to avoid breaking the main business flow
        }
    }

    private void publishAuditEvent(AuditLog log) {
        // Only stream write operations to avoid flooding the audit topic with read noise.
        String actionType = log.getActionType();
        if (actionType == null) return;
        if ("VIEW".equalsIgnoreCase(actionType)) return;

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("userId",      log.getUserId());
            event.put("username",    log.getUsername());
            event.put("actionType",  log.getActionType());
            event.put("entityType",  log.getEntityType());
            event.put("entityId",    log.getEntityId());
            event.put("pspId",       log.getPspId());
            event.put("timestamp",   log.getTimestamp() != null ? log.getTimestamp().toString() : null);
            event.put("ipAddress",   log.getIpAddress());
            event.put("checksum",    log.getChecksum());

            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = log.getPspId() != null ? String.valueOf(log.getPspId()) : "0";
            kafkaTemplate.send(KafkaConfig.TOPIC_TRANSACTIONS_AUDIT, partitionKey, payload);
            logger.debug("Published audit event: entityType={} entityId={}", log.getEntityType(), log.getEntityId());
        } catch (Exception e) {
            logger.warn("Failed to publish audit event to Kafka: entityType={} entityId={} error={}",
                    log.getEntityType(), log.getEntityId(), e.getMessage());
        }
    }
    
    // Retention Policy: Keep logs for 30 days
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 0 * * *") // Run at midnight
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long deleted = auditLogRepository.deleteByTimestampBefore(cutoff);
        logger.info("Retention Policy: Cleaned up {} audit logs older than {}", deleted, cutoff);
    }
}
