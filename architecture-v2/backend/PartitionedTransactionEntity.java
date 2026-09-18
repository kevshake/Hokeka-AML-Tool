package com.posgateway.aml.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Optimized Transaction Entity for Partitioned Table
 * 
 * Database Design:
 * - Table partitioned by RANGE on txn_ts (monthly partitions)
 * - Named: transactions_2026_03, transactions_2026_04, etc.
 * 
 * Indexes:
 * - (customer_id, txn_ts DESC) - Most important for feature computation
 * - (txn_ts) - For time-range queries
 * - (merchant_id) - For merchant risk analysis
 * - (pan_hash) - For card-based tracking
 * 
 * Partition Strategy:
 * - CREATE TABLE transactions_2026_03 PARTITION OF transactions
 *   FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
 * 
 * Performance:
 * - Partition pruning: Only scan relevant time ranges
 * - Query time reduction: 10x-100x for time-bound queries
 * - Maintenance: Drop old partitions instead of DELETE
 */
@Entity
@Table(name = "transactions", 
       indexes = {
           @Index(name = "idx_txn_customer_ts", columnList = "customer_id, txn_ts DESC"),
           @Index(name = "idx_txn_timestamp", columnList = "txn_ts"),
           @Index(name = "idx_txn_merchant", columnList = "merchant_id"),
           @Index(name = "idx_txn_pan_hash", columnList = "pan_hash"),
           @Index(name = "idx_txn_decision", columnList = "decision"),
           @Index(name = "idx_txn_country", columnList = "country_code")
       })
public class PartitionedTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "txn_seq")
    @SequenceGenerator(name = "txn_seq", sequenceName = "transaction_seq", allocationSize = 100)
    @Column(name = "txn_id")
    private Long txnId;

    // Core transaction identifiers
    @Column(name = "txn_reference", length = 64, unique = true, nullable = false)
    private String txnReference; // External reference (UUID)

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId; // Partition key for Kafka, indexed

    @Column(name = "pan_hash", length = 64)
    private String panHash; // Card fingerprint (hashed)

    @Column(name = "merchant_id", length = 32)
    private String merchantId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "terminal_id", length = 16)
    private String terminalId;

    // Transaction details
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "KES";

    @Column(name = "txn_ts", nullable = false)
    private LocalDateTime txnTs; // Partition column

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "channel", length = 16)
    private String channel; // POS, ATM, API, MOBILE

    @Column(name = "mcc", length = 4)
    private String mcc; // Merchant category code

    // Risk & Decision (populated by rule engine)
    @Column(name = "decision", length = 16)
    private String decision; // ALLOW, REVIEW, BLOCK

    @Column(name = "risk_score")
    private Integer riskScore; // 0-100

    @Column(name = "rules_triggered", columnDefinition = "TEXT")
    private String rulesTriggered; // JSON array of rule names

    // Processing metadata
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs; // End-to-end latency

    @Column(name = "features_version")
    private Long featuresVersion; // Version of features used for decision

    // Raw data storage
    @Column(name = "iso_msg", columnDefinition = "TEXT")
    private String isoMsg; // Raw ISO message (optional, for debugging)

    @Column(name = "emv_tags", columnDefinition = "JSONB")
    private String emvTags; // JSON string

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // IPv4 or IPv6

    @Column(name = "device_fingerprint", length = 128)
    private String deviceFingerprint;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "acquirer_response", length = 4)
    private String acquirerResponse; // ISO response code

    // Metadata
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ingestion_ts")
    private LocalDateTime ingestionTs; // When received by AML system

    @Column(name = "decision_ts")
    private LocalDateTime decisionTs; // When decision was made

    // Pre-insert hook
    @PrePersist
    protected void onCreate() {
        if (txnTs == null) {
            txnTs = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (txnReference == null) {
            txnReference = java.util.UUID.randomUUID().toString();
        }
    }

    // Helper methods
    public Double getAmountInMajorUnits() {
        if (amountCents == null) return 0.0;
        return amountCents / 100.0;
    }

    public boolean isHighValue() {
        return amountCents != null && amountCents > 10000000; // > 100,000 in major units
    }

    // Getters & Setters
    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }

    public String getTxnReference() { return txnReference; }
    public void setTxnReference(String txnReference) { this.txnReference = txnReference; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getPanHash() { return panHash; }
    public void setPanHash(String panHash) { this.panHash = panHash; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getTxnTs() { return txnTs; }
    public void setTxnTs(LocalDateTime txnTs) { this.txnTs = txnTs; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getRulesTriggered() { return rulesTriggered; }
    public void setRulesTriggered(String rulesTriggered) { this.rulesTriggered = rulesTriggered; }

    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public Long getFeaturesVersion() { return featuresVersion; }
    public void setFeaturesVersion(Long featuresVersion) { this.featuresVersion = featuresVersion; }

    public String getIsoMsg() { return isoMsg; }
    public void setIsoMsg(String isoMsg) { this.isoMsg = isoMsg; }

    public String getEmvTags() { return emvTags; }
    public void setEmvTags(String emvTags) { this.emvTags = emvTags; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getAcquirerResponse() { return acquirerResponse; }
    public void setAcquirerResponse(String acquirerResponse) { this.acquirerResponse = acquirerResponse; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getIngestionTs() { return ingestionTs; }
    public void setIngestionTs(LocalDateTime ingestionTs) { this.ingestionTs = ingestionTs; }

    public LocalDateTime getDecisionTs() { return decisionTs; }
    public void setDecisionTs(LocalDateTime decisionTs) { this.decisionTs = decisionTs; }
}
