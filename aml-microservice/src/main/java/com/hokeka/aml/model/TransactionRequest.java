package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class TransactionRequest {
    @JsonProperty("transactionId")
    private String transactionId;
    @JsonProperty("merchantId")
    private String merchantId;
    @JsonProperty("amount")
    private BigDecimal amount;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("transactionType")
    private String transactionType;
    @JsonProperty("country")
    private String country;
    @JsonProperty("customerId")
    private String customerId;

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String v) { this.merchantId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { this.transactionType = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }
}
