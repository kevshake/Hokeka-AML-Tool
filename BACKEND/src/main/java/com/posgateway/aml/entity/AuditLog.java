package com.posgateway.aml.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Enhanced Audit Log Entity
 * Captures comprehensive details about user actions for regulatory compliance
 */
@Entity
@Table(name = "audit_logs_enhanced")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who
    @Column(nullable = false)
    private String userId; // User ID or "SYSTEM"

    @Column(nullable = false)
    private String username;

    private String userRole;

    @Column(name = "psp_id")
    private Long pspId; // 0 for Super Admin/System, >0 for specific PSP

    // What
    @Column(nullable = false)
    private String actionType; // VIEW, CREATE, UPDATE, DELETE, EXPORT, LOGIN, LOGOUT

    @Column(nullable = false)
    private String entityType; // CASE, SAR, TRANSACTION, USER, RULE

    @Column(nullable = false)
    private String entityId;

    // Change Tracking
    @Column(columnDefinition = "TEXT")
    private String beforeValue; // JSON of state before change

    @Column(columnDefinition = "TEXT")
    private String afterValue; // JSON of state after change

    // When
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Where
    private String ipAddress;
    private String sessionId;
    private String userAgent;

    // Why
    private String reason; // Mandatory for sensitive actions like overrides

    // Result
    private boolean success = true;

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // If failed

    // Integrity
    @Column(updatable = false)
    private String checksum; // Hash of the record for tamper detection (HMAC)

    public AuditLog() {
    }

    public AuditLog(Long id, String userId, String username, String userRole, String actionType, String entityType,
            String entityId, String beforeValue, String afterValue, LocalDateTime timestamp, String ipAddress,
            String sessionId, String userAgent, String reason, boolean success, String errorMessage, String checksum, Long pspId) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.userRole = userRole;
        this.pspId = pspId;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.sessionId = sessionId;
        this.userAgent = userAgent;
        this.reason = reason;
        this.success = success;
        this.errorMessage = errorMessage;
        this.checksum = checksum;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private Long id;
        private String userId;
        private String username;
        private String userRole;
        private Long pspId;
        private String actionType;
        private String entityType;
        private String entityId;
        private String beforeValue;
        private String afterValue;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String sessionId;
        private String userAgent;
        private String reason;
        private boolean success = true;
        private String errorMessage;
        private String checksum;

        AuditLogBuilder() {
        }

        public AuditLogBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public AuditLogBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public AuditLogBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AuditLogBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AuditLogBuilder userRole(String userRole) {
            this.userRole = userRole;
            return this;
        }

        public AuditLogBuilder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public AuditLogBuilder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public AuditLogBuilder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public AuditLogBuilder beforeValue(String beforeValue) {
            this.beforeValue = beforeValue;
            return this;
        }

        public AuditLogBuilder afterValue(String afterValue) {
            this.afterValue = afterValue;
            return this;
        }

        public AuditLogBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditLogBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public AuditLogBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public AuditLogBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public AuditLogBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public AuditLogBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public AuditLogBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AuditLogBuilder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public AuditLog build() {
            return new AuditLog(id, userId, username, userRole, actionType, entityType, entityId, beforeValue,
                    afterValue, timestamp, ipAddress, sessionId, userAgent, reason, success, errorMessage, checksum, pspId);
        }

        public String toString() {
            return "AuditLog.AuditLogBuilder(id=" + this.id + ", userId=" + this.userId + ", username=" + this.username
                    + ", userRole=" + this.userRole + ", actionType=" + this.actionType + ", entityType="
                    + this.entityType + ", entityId=" + this.entityId + ", beforeValue=" + this.beforeValue
                    + ", afterValue=" + this.afterValue + ", timestamp=" + this.timestamp + ", ipAddress="
                    + this.ipAddress + ", sessionId=" + this.sessionId + ", userAgent=" + this.userAgent + ", reason="
                    + this.reason + ", success=" + this.success + ", errorMessage=" + this.errorMessage + ", checksum="
                    + this.checksum + ")";
        }
    }
}
