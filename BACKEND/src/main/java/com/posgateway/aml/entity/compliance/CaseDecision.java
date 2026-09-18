package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import com.posgateway.aml.entity.User;

import java.time.LocalDateTime;

/**
 * Case Decision Entity
 * Represents a discrete decision made on a case (e.g. Approve, Reject, File
 * SAR).
 * Enforces mandatory justification for regulatory audits.
 */
@Entity
@Table(name = "case_decisions")
@Audited
public class CaseDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @Column(nullable = false)
    private String decisionType; // APPROVE, REJECT, HOLD, FILE_SAR, FREEZE_ACCOUNT

    @Column(columnDefinition = "TEXT", nullable = false)
    private String justification; // MANDATORY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by", referencedColumnName = "id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User decidedBy;

    @Column(nullable = false)
    private LocalDateTime decidedAt;

    private boolean isFinal; // True if this closes the case

    public CaseDecision() {
    }

    public CaseDecision(ComplianceCase complianceCase, String decisionType, String justification, User decidedBy) {
        this.complianceCase = complianceCase;
        this.decisionType = decisionType;
        this.justification = justification;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
        this.isFinal = determineIfFinal(decisionType);
    }

    private boolean determineIfFinal(String type) {
        return "APPROVE".equals(type) || "REJECT".equals(type) || "FILE_SAR".equals(type);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(String decisionType) {
        this.decisionType = decisionType;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(User decidedBy) {
        this.decidedBy = decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}
