package com.posgateway.aml.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Risk-assessment request model for AML and fraud scoring APIs.
 * Not a JPA entity — persisted transactions use {@link com.posgateway.aml.entity.TransactionEntity}.
 */
public class Transaction {

    private Long id;

    @NotNull
    private String transactionId;

    private Long pspId;

    @NotNull
    private String accountNumber;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private String currencyCode;

    @NotNull
    private String transactionType;

    private String merchantId;

    private String merchantName;

    private String merchantCategoryCode;

    private LocalDateTime transactionTimestamp;

    private TransactionStatus status;

    private Integer amlRiskScore;

    private Integer fraudScore;

    private String ipAddress;

    private String deviceFingerprint;

    private String transactionUrl;

    private String countryCode;

    private java.math.BigDecimal latitude;

    private java.math.BigDecimal longitude;

    private boolean cashTransaction;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Returns the surrogate id when this payload was loaded from storage. */
    public Long getId() {
        return id;
    }

    /** Sets the surrogate id when this payload was loaded from storage. */
    public void setId(Long id) {
        this.id = id;
    }

    /** Returns the client-facing transaction identifier. */
    public String getTransactionId() {
        return transactionId;
    }

    /** Sets the client-facing transaction identifier. */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    /** Returns the PSP tenant id when supplied on the request. */
    public Long getPspId() {
        return pspId;
    }

    /** Sets the PSP tenant id when supplied on the request. */
    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    /** Returns the hashed or tokenized account identifier used for velocity rules. */
    public String getAccountNumber() {
        return accountNumber;
    }

    /** Sets the hashed or tokenized account identifier used for velocity rules. */
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    /** Returns the transaction amount in major currency units. */
    public BigDecimal getAmount() {
        return amount;
    }

    /** Sets the transaction amount in major currency units. */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /** Returns the ISO 4217 currency code. */
    public String getCurrencyCode() {
        return currencyCode;
    }

    /** Sets the ISO 4217 currency code. */
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    /** Returns the transaction type label (e.g. INBOUND / OUTBOUND). */
    public String getTransactionType() {
        return transactionType;
    }

    /** Sets the transaction type label (e.g. INBOUND / OUTBOUND). */
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    /** Returns the merchant identifier when present on the request. */
    public String getMerchantId() {
        return merchantId;
    }

    /** Sets the merchant identifier when present on the request. */
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    /** Returns the merchant display name used as payer proxy in third-party checks. */
    public String getMerchantName() {
        return merchantName;
    }

    /** Sets the merchant display name used as payer proxy in third-party checks. */
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    /** Returns the MCC when present on the request. */
    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    /** Sets the MCC when present on the request. */
    public void setMerchantCategoryCode(String merchantCategoryCode) {
        this.merchantCategoryCode = merchantCategoryCode;
    }

    /** Returns the transaction timestamp used for velocity windows. */
    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    /** Sets the transaction timestamp used for velocity windows. */
    public void setTransactionTimestamp(LocalDateTime transactionTimestamp) {
        this.transactionTimestamp = transactionTimestamp;
    }

    /** Returns the workflow status when the caller supplies one. */
    public TransactionStatus getStatus() {
        return status;
    }

    /** Sets the workflow status when the caller supplies one. */
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    /** Returns the AML risk score when pre-computed upstream. */
    public Integer getAmlRiskScore() {
        return amlRiskScore;
    }

    /** Sets the AML risk score when pre-computed upstream. */
    public void setAmlRiskScore(Integer amlRiskScore) {
        this.amlRiskScore = amlRiskScore;
    }

    /** Returns the fraud score when pre-computed upstream. */
    public Integer getFraudScore() {
        return fraudScore;
    }

    /** Sets the fraud score when pre-computed upstream. */
    public void setFraudScore(Integer fraudScore) {
        this.fraudScore = fraudScore;
    }

    /** Returns the originating IP address when present. */
    public String getIpAddress() {
        return ipAddress;
    }

    /** Sets the originating IP address when present. */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /** Returns the device fingerprint when present. */
    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    /** Sets the device fingerprint when present. */
    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    /** Returns an optional transaction URL for link analysis. */
    public String getTransactionUrl() {
        return transactionUrl;
    }

    /** Sets an optional transaction URL for link analysis. */
    public void setTransactionUrl(String transactionUrl) {
        this.transactionUrl = transactionUrl;
    }

    /** Returns the ISO country code when present. */
    public String getCountryCode() {
        return countryCode;
    }

    /** Sets the ISO country code when present. */
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    /** Returns the transaction latitude supplied by an upstream adapter. */
    public java.math.BigDecimal getLatitude() {
        return latitude;
    }

    /** Sets the transaction latitude supplied by an upstream adapter. */
    public void setLatitude(java.math.BigDecimal latitude) {
        this.latitude = latitude;
    }

    /** Returns the transaction longitude supplied by an upstream adapter. */
    public java.math.BigDecimal getLongitude() {
        return longitude;
    }

    /** Sets the transaction longitude supplied by an upstream adapter. */
    public void setLongitude(java.math.BigDecimal longitude) {
        this.longitude = longitude;
    }

    /** Returns true when the transaction is a cash reporting candidate. */
    public boolean isCashTransaction() {
        return cashTransaction;
    }

    /** Marks whether the transaction is a cash reporting candidate. */
    public void setCashTransaction(boolean cashTransaction) {
        this.cashTransaction = cashTransaction;
    }

    /** Returns the created-at timestamp when populated by adapters. */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** Sets the created-at timestamp when populated by adapters. */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** Returns the updated-at timestamp when populated by adapters. */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Sets the updated-at timestamp when populated by adapters. */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Normalizes timestamps before risk assessment when callers omit them. */
    public void ensureTimestampsForAssessment() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (transactionTimestamp == null) {
            transactionTimestamp = now;
        }
    }
}
