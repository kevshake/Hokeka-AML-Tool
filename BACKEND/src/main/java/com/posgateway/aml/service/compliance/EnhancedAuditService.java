package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enhanced Audit Service
 * Extends basic audit service with IP tracking, session tracking, and retention
 */
@Service
public class EnhancedAuditService extends AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedAuditService.class);

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public EnhancedAuditService(AuditLogRepository auditLogRepository,
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(auditLogRepository, objectMapper);
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log action with HTTP request context
     */
    @Transactional
    public void logActionWithContext(User user, String actionType, String entityType, String entityId,
                                    Object beforeState, Object afterState, String reason) {
        String ipAddress = extractIpAddress();
        String sessionId = extractSessionId();
        String userAgent = extractUserAgent();

        logAction(user, actionType, entityType, entityId, beforeState, afterState, ipAddress, reason);

        // Update the last log entry with session info
        List<AuditLog> recentLogs = auditLogRepository.findTop1ByUserIdAndActionTypeOrderByTimestampDesc(
                user.getId().toString(), actionType);
        if (!recentLogs.isEmpty()) {
            AuditLog log = recentLogs.get(0);
            log.setSessionId(sessionId);
            log.setUserAgent(userAgent);
            auditLogRepository.save(log);
        }
    }

    /**
     * Log failed action attempt
     */
    @Transactional
    public void logFailedAction(User user, String actionType, String entityType, String entityId,
                                String errorMessage) {
        try {
            AuditLog log = AuditLog.builder()
                    .userId(user != null ? user.getId().toString() : "UNKNOWN")
                    .username(user != null ? user.getUsername() : "UNKNOWN")
                    .userRole(user != null ? user.getRole().getName() : "UNKNOWN")
                    .actionType(actionType)
                    .entityType(entityType)
                    .entityId(entityId)
                    .timestamp(LocalDateTime.now())
                    .ipAddress(extractIpAddress())
                    .sessionId(extractSessionId())
                    .userAgent(extractUserAgent())
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to log failed action", e);
        }
    }

    /**
     * Extract IP address from request
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.debug("Could not extract IP address", e);
        }
        return "UNKNOWN";
    }

    /**
     * Extract session ID from request
     */
    private String extractSessionId() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getSession(false) != null ? 
                        request.getSession().getId() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not extract session ID", e);
        }
        return null;
    }

    /**
     * Extract user agent from request
     */
    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("Could not extract user agent", e);
        }
        return null;
    }

    /**
     * Enforce audit log retention policy
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void enforceRetentionPolicy() {
        // Delete logs older than retention period (default 7 years)
        LocalDateTime retentionDate = LocalDateTime.now().minusYears(7);
        long deleted = auditLogRepository.deleteByTimestampBefore(retentionDate);
        logger.info("Deleted {} audit logs older than retention period", deleted);
    }
}

