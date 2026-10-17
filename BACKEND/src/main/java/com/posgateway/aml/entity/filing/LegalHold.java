package com.posgateway.aml.entity.filing;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One legal hold ({@code legal_holds}, V269; residency contract §7.10, §8.3): at most 1000 decision
 * record ids (the {@code hold.place} id-scope limit) that the PSP's Edge Ledgers must keep past
 * retention, and that the cloud decision-record purge must keep, until the hold is released.
 * The cloud is the hold's source of truth; per-site delivery is tracked in {@link LegalHoldSiteAck}.
 */
@Entity
@Table(name = "legal_holds")
public class LegalHold {

    /** Cloud-side hold state. */
    public enum State { ACTIVE, RELEASED }

    /** §7.10 {@code hold.release} reason codes. */
    public enum ReleaseReason { CASE_CLOSED, FILING_RETENTION_ELAPSED, REGULATOR_RELEASED, PLACED_IN_ERROR }

    /** Largest id scope of one hold ({@code notify/hold.place} schema). */
    public static final int MAX_ITEMS = 1000;

    @Id
    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "workflow_ref_id", nullable = false, updatable = false)
    private Long workflowRefId;

    @Column(name = "psp_id", nullable = false, updatable = false)
    private Long pspId;

    @Column(name = "reason_code", nullable = false, length = 24, updatable = false)
    private String reasonCode;

    @Column(name = "reference", nullable = false, length = 64, updatable = false)
    private String reference;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "placed_by", nullable = false, length = 128, updatable = false)
    private String placedBy;

    @Column(name = "item_count", nullable = false, updatable = false)
    private Integer itemCount;

    /** End of the workflow (filing, case closure): the hold is released retention years later. */
    @Column(name = "release_basis_at")
    private Instant releaseBasisAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by", length = 128)
    private String releasedBy;

    @Column(name = "release_reason", length = 32)
    private String releaseReason;

    /** The decision record ids the hold covers (class D ids only). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "legal_hold_items", joinColumns = @JoinColumn(name = "hold_id"))
    @Column(name = "edge_assessment_id", nullable = false)
    private Set<UUID> edgeAssessmentIds = new LinkedHashSet<>();

    /** JPA constructor. */
    public LegalHold() {
    }

    /** A new ACTIVE hold of a workflow over {@code ids} (1..1000). */
    public LegalHold(UUID holdId, LegalHoldWorkflow workflow, Set<UUID> ids, Instant placedAt, String placedBy) {
        if (ids.isEmpty() || ids.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("a hold covers 1.." + MAX_ITEMS + " ids, got " + ids.size());
        }
        this.holdId = holdId;
        this.workflowRefId = workflow.getId();
        this.pspId = workflow.getPspId();
        this.reasonCode = workflow.getReasonCode();
        this.reference = workflow.getReference();
        this.state = State.ACTIVE.name();
        this.placedAt = placedAt;
        this.placedBy = placedBy;
        this.itemCount = ids.size();
        this.edgeAssessmentIds = new LinkedHashSet<>(ids);
    }

    /** Marks the hold released (the notify is sent by the caller after commit). */
    public void release(Instant at, String by, ReleaseReason reason) {
        this.state = State.RELEASED.name();
        this.releasedAt = at;
        this.releasedBy = by;
        this.releaseReason = reason.name();
    }

    /** True while the hold is in force. */
    public boolean isActive() {
        return State.ACTIVE.name().equals(state);
    }

    /** Hold id (UUID, shared with the Edge). */
    public UUID getHoldId() { return holdId; }
    /** Owning workflow row id. */
    public Long getWorkflowRefId() { return workflowRefId; }
    /** Owning PSP. */
    public Long getPspId() { return pspId; }
    /** hold.place reasonCode. */
    public String getReasonCode() { return reasonCode; }
    /** hold.place reference. */
    public String getReference() { return reference; }
    /** ACTIVE or RELEASED. */
    public String getState() { return state; }
    /** Placement instant. */
    public Instant getPlacedAt() { return placedAt; }
    /** Placing user ref ({@code u-<id>} or {@code system:<job>}). */
    public String getPlacedBy() { return placedBy; }
    /** Number of ids covered. */
    public Integer getItemCount() { return itemCount; }
    /** Workflow end instant, or null while the workflow is open. */
    public Instant getReleaseBasisAt() { return releaseBasisAt; }
    /** Sets (or clears, on reopening) the workflow end instant. */
    public void setReleaseBasisAt(Instant releaseBasisAt) { this.releaseBasisAt = releaseBasisAt; }
    /** Release instant. */
    public Instant getReleasedAt() { return releasedAt; }
    /** Releasing user ref. */
    public String getReleasedBy() { return releasedBy; }
    /** hold.release reasonCode. */
    public String getReleaseReason() { return releaseReason; }
    /** Covered decision record ids. */
    public Set<UUID> getEdgeAssessmentIds() { return edgeAssessmentIds; }
}
