package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Transaction Entity
 * Stores raw transactions from all merchants
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_merchant", columnList = "merchant_id"),
        @Index(name = "idx_txn_timestamp", columnList = "txn_ts"),
        @Index(name = "idx_txn_pan_hash", columnList = "pan_hash"),
        @Index(name = "idx_txn_risk_level", columnList = "risk_level"),
        @Index(name = "idx_txn_decision", columnList = "decision")
})
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "iso_msg", columnDefinition = "TEXT")
    private String isoMsg;

    @Column(name = "pan_hash")
    private String panHash;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "terminal_id")
    private String terminalId;

    @Column(name = "amount_cents")
    private Long amountCents;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "txn_ts")
    private LocalDateTime txnTs;

    @Column(name = "emv_tags", columnDefinition = "JSONB")
    private String emvTags; // JSON string representation

    @Column(name = "acquirer_response")
    private String acquirerResponse;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Flagright Risk Scores
    @Column(name = "krs")
    private Double krs;

    @Column(name = "trs")
    private Double trs;

    @Column(name = "cra")
    private Double cra;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "merchant_country", length = 3)
    private String merchantCountry;

    // Calculated fields stored for pagination performance
    @Column(name = "risk_level", length = 20)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "decision", length = 20)
    private String decision; // APPROVED, MANUAL_REVIEW, DECLINED

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (txnTs == null) {
            txnTs = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public String getIsoMsg() {
        return isoMsg;
    }

    public void setIsoMsg(String isoMsg) {
        this.isoMsg = isoMsg;
    }

    public String getPanHash() {
        return panHash;
    }

    public void setPanHash(String panHash) {
        this.panHash = panHash;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getTxnTs() {
        return txnTs;
    }

    public void setTxnTs(LocalDateTime txnTs) {
        this.txnTs = txnTs;
    }

    public String getEmvTags() {
        return emvTags;
    }

    public void setEmvTags(String emvTags) {
        this.emvTags = emvTags;
    }

    public String getAcquirerResponse() {
        return acquirerResponse;
    }

    public void setAcquirerResponse(String acquirerResponse) {
        this.acquirerResponse = acquirerResponse;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getKrs() {
        return krs;
    }

    public void setKrs(Double krs) {
        this.krs = krs;
    }

    public Double getTrs() {
        return trs;
    }

    public void setTrs(Double trs) {
        this.trs = trs;
    }

    public Double getCra() {
        return cra;
    }

    public void setCra(Double cra) {
        this.cra = cra;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMerchantCountry() {
        return merchantCountry;
    }

    public void setMerchantCountry(String merchantCountry) {
        this.merchantCountry = merchantCountry;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
