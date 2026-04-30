package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Immutable audit trail entity
 * No updates or deletes allowed
 */
@Entity
@Table(name = "audit_trail")
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    // Entity
    @Column(name = "merchant_id")
    private Long merchantId;

    // Action
    @Column(name = "action", nullable = false, length = 100)
    private String action; // ONBOARDED, SCREENED, APPROVED, REJECTED, UPDATED

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy; // User ID or SYSTEM

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt = LocalDateTime.now();

    // Evidence (immutable JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    // Rules Version
    @Column(name = "rules_version", length = 50)
    private String rulesVersion;

    // Decision
    @Column(name = "decision", length = 50)
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    // Metadata
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    public AuditTrail() {
    }

    public AuditTrail(Long auditId, Long merchantId, String action, String performedBy, LocalDateTime performedAt,
            Map<String, Object> evidence, String rulesVersion, String decision, String decisionReason, String ipAddress,
            String userAgent) {
        this.auditId = auditId;
        this.merchantId = merchantId;
        this.action = action;
        this.performedBy = performedBy;
        this.performedAt = performedAt != null ? performedAt : LocalDateTime.now();
        this.evidence = evidence;
        this.rulesVersion = rulesVersion;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public static AuditTrailBuilder builder() {
        return new AuditTrailBuilder();
    }

    public static class AuditTrailBuilder {
        private Long auditId;
        private Long merchantId;
        private String action;
        private String performedBy;
        private LocalDateTime performedAt = LocalDateTime.now();
        private Map<String, Object> evidence;
        private String rulesVersion;
        private String decision;
        private String decisionReason;
        private String ipAddress;
        private String userAgent;

        AuditTrailBuilder() {
        }

        public AuditTrailBuilder auditId(Long auditId) {
            this.auditId = auditId;
            return this;
        }

        public AuditTrailBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public AuditTrailBuilder action(String action) {
            this.action = action;
            return this;
        }

        public AuditTrailBuilder performedBy(String performedBy) {
            this.performedBy = performedBy;
            return this;
        }

        public AuditTrailBuilder performedAt(LocalDateTime performedAt) {
            this.performedAt = performedAt;
            return this;
        }

        public AuditTrailBuilder evidence(Map<String, Object> evidence) {
            this.evidence = evidence;
            return this;
        }

        public AuditTrailBuilder rulesVersion(String rulesVersion) {
            this.rulesVersion = rulesVersion;
            return this;
        }

        public AuditTrailBuilder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public AuditTrailBuilder decisionReason(String decisionReason) {
            this.decisionReason = decisionReason;
            return this;
        }

        public AuditTrailBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public AuditTrailBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public AuditTrail build() {
            return new AuditTrail(auditId, merchantId, action, performedBy, performedAt, evidence, rulesVersion,
                    decision, decisionReason, ipAddress, userAgent);
        }

        public String toString() {
            return "AuditTrail.AuditTrailBuilder(auditId=" + this.auditId + ", merchantId=" + this.merchantId
                    + ", action=" + this.action + ", performedBy=" + this.performedBy + ", performedAt="
                    + this.performedAt + ", evidence=" + this.evidence + ", rulesVersion=" + this.rulesVersion
                    + ", decision=" + this.decision + ", decisionReason=" + this.decisionReason + ", ipAddress="
                    + this.ipAddress + ", userAgent=" + this.userAgent + ")";
        }
    }
}
