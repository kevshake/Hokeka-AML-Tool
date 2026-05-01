package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class TransactionRequest {
    @JsonProperty("transactionId")
    private String transactionId;
    @JsonProperty("pspId")
    private Long pspId;
    @JsonProperty("merchantId")
    private String merchantId;
    @JsonProperty("amount")
    private BigDecimal amount;
    /** Encoded amount in minor units (cents). Optional but encouraged for precision. */
    @JsonProperty("amountCents")
    private Long amountCents;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("transactionType")
    private String transactionType;
    @JsonProperty("country")
    private String country;
    @JsonProperty("customerId")
    private String customerId;
    /** Optional one-way hash of PAN — never carry the raw PAN across services. */
    @JsonProperty("panHash")
    private String panHash;
    /** Optional sender / counterparty name for inline sanctions screening. */
    @JsonProperty("senderName")
    private String senderName;

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long v) { this.pspId = v; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String v) { this.merchantId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long v) { this.amountCents = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { this.transactionType = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }
    public String getPanHash() { return panHash; }
    public void setPanHash(String v) { this.panHash = v; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String v) { this.senderName = v; }
}
