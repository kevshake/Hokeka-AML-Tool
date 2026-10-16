package com.posgateway.aml.entity.residency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One merchant the Edge must refuse (table {@code edge_merchant_blocklist}, V261), distributed in
 * the Edge config document's {@code merchantBlocklist} section. Rows are never deleted; removal sets
 * {@code removedAt} so the blocklist version only grows.
 */
@Entity
@Table(name = "edge_merchant_blocklist")
public class EdgeMerchantBlocklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    /** SANCTIONED, REGULATOR_ORDER, PSP_TERMINATED or FRAUD_CONFIRMED. */
    @Column(name = "reason_code", nullable = false, length = 24)
    private String reasonCode;

    @Column(name = "since", nullable = false)
    private Instant since = Instant.now();

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by", length = 255)
    private String removedBy;

    /** Row id. */
    public Long getId() { return id; }

    /** Sets the row id (tests only). */
    public void setId(Long id) { this.id = id; }

    /** Owning PSP. */
    public Long getPspId() { return pspId; }

    /** Sets the owning PSP. */
    public void setPspId(Long pspId) { this.pspId = pspId; }

    /** Blocked merchant (cloud master-data id). */
    public String getMerchantId() { return merchantId; }

    /** Sets the blocked merchant. */
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    /** Why the merchant is blocked. */
    public String getReasonCode() { return reasonCode; }

    /** Sets why the merchant is blocked. */
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    /** When the block started. */
    public Instant getSince() { return since; }

    /** Sets when the block started. */
    public void setSince(Instant since) { this.since = since; }

    /** Who added the block. */
    public String getCreatedBy() { return createdBy; }

    /** Sets who added the block. */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** When the block was lifted, or null while active. */
    public Instant getRemovedAt() { return removedAt; }

    /** Lifts the block at the given time. */
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }

    /** Who lifted the block. */
    public String getRemovedBy() { return removedBy; }

    /** Sets who lifted the block. */
    public void setRemovedBy(String removedBy) { this.removedBy = removedBy; }
}
