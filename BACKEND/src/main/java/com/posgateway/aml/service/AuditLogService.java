package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.AuditLogRepository;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Audit Log Service
 * Handles the creation and storage of immutable audit logs
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${audit.hmac.key:default-secret-key-change-in-production}")
    private String hmacKey;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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

        } catch (Exception e) {
            logger.error("Failed to create audit log", e);
            // Don't rethrow to avoid breaking the main business flow
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
