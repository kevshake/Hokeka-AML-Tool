package com.posgateway.aml.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a three-letter ISO 4217 code")
    private String currency;

    private String pan;

    private String isoMsg;

    private Map<String, Object> emvTags;

    private String acquirerResponse;

    private String direction; // INBOUND, OUTBOUND
    private String ipAddress;
    private String countryCode;
    private String channelType;
    private Boolean cashTransaction = false;
    @Size(max = 255, message = "Customer account reference must not exceed 255 characters")
    private String customerAccountReference;
    @Email(message = "Customer email must be a valid email address")
    @Size(max = 320, message = "Customer email must not exceed 320 characters")
    private String customerEmail;

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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public Boolean getCashTransaction() { return cashTransaction; }
    public void setCashTransaction(Boolean cashTransaction) { this.cashTransaction = cashTransaction; }
    public String getCustomerAccountReference() { return customerAccountReference; }
    public void setCustomerAccountReference(String customerAccountReference) {
        this.customerAccountReference = customerAccountReference;
    }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
}

