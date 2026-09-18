package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
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
    private String decision; // ALLOW, ALERT, HOLD, BLOCK (legacy values normalized on read)

    @Column(name = "sar_required", nullable = false)
    private boolean sarRequired;

    @Column(name = "ctr_required", nullable = false)
    private boolean ctrRequired;

    @Column(name = "cash_transaction", nullable = false)
    private boolean cashTransaction;

    @Column(name = "ctr_evaluation_status", length = 32)
    private String ctrEvaluationStatus;

    @Column(name = "ctr_usd_equivalent", precision = 19, scale = 4)
    private BigDecimal ctrUsdEquivalent;

    @Column(name = "ctr_threshold_usd", precision = 19, scale = 4)
    private BigDecimal ctrThresholdUsd;

    @Column(name = "ctr_rate_source", length = 160)
    private String ctrRateSource;

    @Column(name = "ctr_rate_effective_at")
    private LocalDateTime ctrRateEffectiveAt;

    @Column(name = "ctr_evaluated_at")
    private LocalDateTime ctrEvaluatedAt;

    @Column(name = "rule_decision", length = 20)
    private String ruleDecision;

    @Column(name = "triggered_rules", columnDefinition = "TEXT")
    private String triggeredRules;

    // CBK classification columns (V128 migration)
    @Column(name = "card_brand", length = 16)
    private String cardBrand; // e.g. VISA, MASTERCARD — null if unknown

    @Column(name = "card_type", length = 16)
    private String cardType; // e.g. DEBIT, CREDIT, PREPAID

    @Column(name = "card_class", length = 16)
    private String cardClass; // e.g. CLASSIC, GOLD, PLATINUM

    @Column(name = "channel_type", length = 32)
    private String channelType; // e.g. POS, ECOMMERCE, MOBILE, ATM

    @Column(name = "bill_classification_code", length = 16)
    private String billClassificationCode; // CBK taxonomy code

    @Column(name = "customer_account_reference", length = 255)
    private String customerAccountReference;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.posgateway.aml.config.security.PiiMaskingSerializer.class)
    @Convert(converter = com.posgateway.aml.entity.converter.VersionedAesGcmStringConverter.class)
    @Column(name = "customer_email", columnDefinition = "TEXT")
    private String customerEmail;

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

    public boolean isSarRequired() { return sarRequired; }
    public void setSarRequired(boolean sarRequired) { this.sarRequired = sarRequired; }
    public boolean isCtrRequired() { return ctrRequired; }
    public void setCtrRequired(boolean ctrRequired) { this.ctrRequired = ctrRequired; }
    public boolean isCashTransaction() { return cashTransaction; }
    public void setCashTransaction(boolean cashTransaction) { this.cashTransaction = cashTransaction; }
    public String getCtrEvaluationStatus() { return ctrEvaluationStatus; }
    public void setCtrEvaluationStatus(String ctrEvaluationStatus) { this.ctrEvaluationStatus = ctrEvaluationStatus; }
    public BigDecimal getCtrUsdEquivalent() { return ctrUsdEquivalent; }
    public void setCtrUsdEquivalent(BigDecimal ctrUsdEquivalent) { this.ctrUsdEquivalent = ctrUsdEquivalent; }
    public BigDecimal getCtrThresholdUsd() { return ctrThresholdUsd; }
    public void setCtrThresholdUsd(BigDecimal ctrThresholdUsd) { this.ctrThresholdUsd = ctrThresholdUsd; }
    public String getCtrRateSource() { return ctrRateSource; }
    public void setCtrRateSource(String ctrRateSource) { this.ctrRateSource = ctrRateSource; }
    public LocalDateTime getCtrRateEffectiveAt() { return ctrRateEffectiveAt; }
    public void setCtrRateEffectiveAt(LocalDateTime ctrRateEffectiveAt) { this.ctrRateEffectiveAt = ctrRateEffectiveAt; }
    public LocalDateTime getCtrEvaluatedAt() { return ctrEvaluatedAt; }
    public void setCtrEvaluatedAt(LocalDateTime ctrEvaluatedAt) { this.ctrEvaluatedAt = ctrEvaluatedAt; }
    public String getRuleDecision() { return ruleDecision; }
    public void setRuleDecision(String ruleDecision) { this.ruleDecision = ruleDecision; }
    public String getTriggeredRules() { return triggeredRules; }
    public void setTriggeredRules(String triggeredRules) { this.triggeredRules = triggeredRules; }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardClass() {
        return cardClass;
    }

    public void setCardClass(String cardClass) {
        this.cardClass = cardClass;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getBillClassificationCode() {
        return billClassificationCode;
    }

    public void setBillClassificationCode(String billClassificationCode) {
        this.billClassificationCode = billClassificationCode;
    }

    public String getCustomerAccountReference() {
        return customerAccountReference;
    }

    public void setCustomerAccountReference(String customerAccountReference) {
        this.customerAccountReference = normalizeNullable(customerAccountReference);
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = normalizeNullable(customerEmail);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
