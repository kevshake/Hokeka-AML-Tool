package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.CasePriority;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Escalation Rule Entity
 * Defines rules for automatic case escalation
 */
@Entity
@Table(name = "escalation_rules")
@Audited
public class EscalationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_priority")
    private CasePriority minPriority;

    @Column(name = "min_risk_score")
    private Double minRiskScore;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "days_open")
    private Integer daysOpen;

    @Column(name = "escalate_to_role")
    private String escalateToRole;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalate_to_user_id")
    private User escalateToUser;

    @Column(name = "reason_template", columnDefinition = "TEXT")
    private String reasonTemplate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EscalationRule() {
    }

    public EscalationRule(Long id, String ruleName, Boolean enabled, CasePriority minPriority,
            Double minRiskScore, BigDecimal minAmount, Integer daysOpen,
            String escalateToRole, User escalateToUser, String reasonTemplate,
            LocalDateTime createdAt) {
        this.id = id;
        this.ruleName = ruleName;
        this.enabled = enabled;
        this.minPriority = minPriority;
        this.minRiskScore = minRiskScore;
        this.minAmount = minAmount;
        this.daysOpen = daysOpen;
        this.escalateToRole = escalateToRole;
        this.escalateToUser = escalateToUser;
        this.reasonTemplate = reasonTemplate;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public CasePriority getMinPriority() {
        return minPriority;
    }

    public void setMinPriority(CasePriority minPriority) {
        this.minPriority = minPriority;
    }

    public Double getMinRiskScore() {
        return minRiskScore;
    }

    public void setMinRiskScore(Double minRiskScore) {
        this.minRiskScore = minRiskScore;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public Integer getDaysOpen() {
        return daysOpen;
    }

    public void setDaysOpen(Integer daysOpen) {
        this.daysOpen = daysOpen;
    }

    public String getEscalateToRole() {
        return escalateToRole;
    }

    public void setEscalateToRole(String escalateToRole) {
        this.escalateToRole = escalateToRole;
    }

    public User getEscalateToUser() {
        return escalateToUser;
    }

    public void setEscalateToUser(User escalateToUser) {
        this.escalateToUser = escalateToUser;
    }

    public Long getEscalateToUserId() {
        return escalateToUser != null ? escalateToUser.getId() : null;
    }

    public String getReasonTemplate() {
        return reasonTemplate;
    }

    public void setReasonTemplate(String reasonTemplate) {
        this.reasonTemplate = reasonTemplate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (enabled == null) {
            enabled = true;
        }
    }
}
