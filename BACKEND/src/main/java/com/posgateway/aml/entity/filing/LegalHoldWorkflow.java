package com.posgateway.aml.entity.filing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The workflow record that owns a set of legal holds ({@code legal_hold_workflows}, V269): a SAR,
 * an escalated case or a filed CTR deadline. One row per workflow record; it is locked while the
 * holds covering its transactions are computed, so two Control Plane instances never place
 * overlapping holds for the same record.
 */
@Entity
@Table(name = "legal_hold_workflows")
public class LegalHoldWorkflow {

    /** Workflow records that can own holds. */
    public enum Type { SAR, CASE, CTR_DEADLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false, updatable = false)
    private Long pspId;

    @Column(name = "workflow_type", nullable = false, length = 24, updatable = false)
    private String workflowType;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;

    @Column(name = "reason_code", nullable = false, length = 24, updatable = false)
    private String reasonCode;

    @Column(name = "reference", nullable = false, length = 64, updatable = false)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA constructor. */
    public LegalHoldWorkflow() {
    }

    /** Creates the row for one workflow record. */
    public LegalHoldWorkflow(long pspId, Type type, long workflowId, String reasonCode, String reference) {
        this.pspId = pspId;
        this.workflowType = type.name();
        this.workflowId = workflowId;
        this.reasonCode = reasonCode;
        this.reference = reference;
    }

    /** Stamps the creation time. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Database id. */
    public Long getId() { return id; }
    /** Owning PSP. */
    public Long getPspId() { return pspId; }
    /** SAR, CASE or CTR_DEADLINE. */
    public String getWorkflowType() { return workflowType; }
    /** Id of the workflow record. */
    public Long getWorkflowId() { return workflowId; }
    /** hold.place reasonCode used for this workflow's holds. */
    public String getReasonCode() { return reasonCode; }
    /** hold.place reference (workflowRef) used for this workflow's holds. */
    public String getReference() { return reference; }
    /** Creation instant. */
    public Instant getCreatedAt() { return createdAt; }
}
