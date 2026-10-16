package com.posgateway.aml.entity.edge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Cloud tunnel access audit row ({@code edge_data_access_log}, V263;
 * {@code docs/EDGE_DATA_RESIDENCY_CONTRACT.md} §7.15). One row per tunnel request, written before
 * the request is sent and completed on the terminal frame; {@link #auditId} is shared with the
 * Edge-local {@code edge_access_log} row.
 *
 * <p><b>Never</b> holds a response item or a {@code txnRef} value: {@link #paramsSummary} is the
 * restricted summary built by {@code EdgeDataAccessAuditor.summarize} and {@link #paramsDigest} the
 * SHA-256 of the JCS params. Deliberately not {@code @Audited}: Envers tables are on the §7.16
 * no-payload list and this row is itself the audit record.
 */
@Entity
@Table(name = "edge_data_access_log")
public class EdgeDataAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false, unique = true, updatable = false)
    private UUID auditId;

    @Column(name = "psp_id", nullable = false, updatable = false)
    private Long pspId;

    @Column(name = "actor_kind", nullable = false, length = 16, updatable = false)
    private String actorKind;

    @Column(name = "actor_user_id", nullable = false, length = 128, updatable = false)
    private String actorUserId;

    @Column(name = "actor_role", nullable = false, length = 64, updatable = false)
    private String actorRole;

    @Column(name = "purpose", nullable = false, length = 32, updatable = false)
    private String purpose;

    @Column(name = "case_id", length = 64, updatable = false)
    private String caseId;

    @Column(name = "alert_id", length = 64, updatable = false)
    private String alertId;

    @Column(name = "filing_ref", length = 64, updatable = false)
    private String filingRef;

    @Column(name = "support_access", nullable = false, updatable = false)
    private boolean supportAccess;

    @Column(name = "support_reason", length = 500, updatable = false)
    private String supportReason;

    @Column(name = "op", nullable = false, length = 32, updatable = false)
    private String op;

    @Column(name = "params_summary", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String paramsSummary;

    @Column(name = "params_digest", nullable = false, length = 64, updatable = false)
    private String paramsDigest;

    @Column(name = "sites_contacted", length = 4096)
    private String sitesContacted;

    @Column(name = "sites_answered", length = 4096)
    private String sitesAnswered;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "bytes", nullable = false)
    private long bytes;

    /** {@code OK}, {@code PARTIAL} or a §7.11 error code; {@code null} while in flight. */
    @Column(name = "outcome", length = 32)
    private String outcome;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Returns {@code id}. */
    public Long getId() { return id; }
    /** Returns {@code auditId}. */
    public UUID getAuditId() { return auditId; }
    /** Sets {@code auditId}. */
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    /** Returns {@code pspId}. */
    public Long getPspId() { return pspId; }
    /** Sets {@code pspId}. */
    public void setPspId(Long pspId) { this.pspId = pspId; }
    /** Returns {@code actorKind}. */
    public String getActorKind() { return actorKind; }
    /** Sets {@code actorKind}. */
    public void setActorKind(String actorKind) { this.actorKind = actorKind; }
    /** Returns {@code actorUserId}. */
    public String getActorUserId() { return actorUserId; }
    /** Sets {@code actorUserId}. */
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    /** Returns {@code actorRole}. */
    public String getActorRole() { return actorRole; }
    /** Sets {@code actorRole}. */
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    /** Returns {@code purpose}. */
    public String getPurpose() { return purpose; }
    /** Sets {@code purpose}. */
    public void setPurpose(String purpose) { this.purpose = purpose; }
    /** Returns {@code caseId}. */
    public String getCaseId() { return caseId; }
    /** Sets {@code caseId}. */
    public void setCaseId(String caseId) { this.caseId = caseId; }
    /** Returns {@code alertId}. */
    public String getAlertId() { return alertId; }
    /** Sets {@code alertId}. */
    public void setAlertId(String alertId) { this.alertId = alertId; }
    /** Returns {@code filingRef}. */
    public String getFilingRef() { return filingRef; }
    /** Sets {@code filingRef}. */
    public void setFilingRef(String filingRef) { this.filingRef = filingRef; }
    /** Returns {@code supportAccess}. */
    public boolean isSupportAccess() { return supportAccess; }
    /** Sets {@code supportAccess}. */
    public void setSupportAccess(boolean supportAccess) { this.supportAccess = supportAccess; }
    /** Returns {@code supportReason}. */
    public String getSupportReason() { return supportReason; }
    /** Sets {@code supportReason}. */
    public void setSupportReason(String supportReason) { this.supportReason = supportReason; }
    /** Returns {@code op}. */
    public String getOp() { return op; }
    /** Sets {@code op}. */
    public void setOp(String op) { this.op = op; }
    /** Returns {@code paramsSummary}. */
    public String getParamsSummary() { return paramsSummary; }
    /** Sets {@code paramsSummary}. */
    public void setParamsSummary(String paramsSummary) { this.paramsSummary = paramsSummary; }
    /** Returns {@code paramsDigest}. */
    public String getParamsDigest() { return paramsDigest; }
    /** Sets {@code paramsDigest}. */
    public void setParamsDigest(String paramsDigest) { this.paramsDigest = paramsDigest; }
    /** Returns {@code sitesContacted}. */
    public String getSitesContacted() { return sitesContacted; }
    /** Sets {@code sitesContacted}. */
    public void setSitesContacted(String sitesContacted) { this.sitesContacted = sitesContacted; }
    /** Returns {@code sitesAnswered}. */
    public String getSitesAnswered() { return sitesAnswered; }
    /** Sets {@code sitesAnswered}. */
    public void setSitesAnswered(String sitesAnswered) { this.sitesAnswered = sitesAnswered; }
    /** Returns {@code itemCount}. */
    public int getItemCount() { return itemCount; }
    /** Sets {@code itemCount}. */
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    /** Returns {@code bytes}. */
    public long getBytes() { return bytes; }
    /** Sets {@code bytes}. */
    public void setBytes(long bytes) { this.bytes = bytes; }
    /** Returns {@code outcome}. */
    public String getOutcome() { return outcome; }
    /** Sets {@code outcome}. */
    public void setOutcome(String outcome) { this.outcome = outcome; }
    /** Returns {@code requestedAt}. */
    public Instant getRequestedAt() { return requestedAt; }
    /** Sets {@code requestedAt}. */
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    /** Returns {@code completedAt}. */
    public Instant getCompletedAt() { return completedAt; }
    /** Sets {@code completedAt}. */
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
