package com.posgateway.aml.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Transaction Request DTO
 * Request DTO for transaction ingestion
 */
public class TransactionRequestDTO {

    @NotBlank(message = "Merchant ID is required")
    private String merchantId;

    private String terminalId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amountCents;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String pan;

    private String isoMsg;

    private Map<String, Object> emvTags;

    private String acquirerResponse;

    private String direction; // INBOUND, OUTBOUND

    // Getters and Setters
    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
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

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getIsoMsg() {
        return isoMsg;
    }

    public void setIsoMsg(String isoMsg) {
        this.isoMsg = isoMsg;
    }

    public Map<String, Object> getEmvTags() {
        return emvTags;
    }

    public void setEmvTags(Map<String, Object> emvTags) {
        this.emvTags = emvTags;
    }

    public String getAcquirerResponse() {
        return acquirerResponse;
    }

    public void setAcquirerResponse(String acquirerResponse) {
        this.acquirerResponse = acquirerResponse;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}

