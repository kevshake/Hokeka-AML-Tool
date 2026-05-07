package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #17 — Failed / Rejected Transactions.
 * Wrapper key: {@code FAILED_REJECTED_TRX_INFO}
 * Schedule: Daily (previous day failures).
 *
 * <p>Field names contain literal spaces — kept verbatim per inventory.
 * Note: "Email" field name is mixed-case as documented — do NOT normalise.
 */
public final class FailedTransactionRecord {

    @JsonProperty("BANK ID")
    private String bankId;

    @JsonProperty("REPORTING DATE")
    private String reportingDate;

    @JsonProperty("CUSTOMER ACCOUNT NUMBER")
    private String customerAccountNumber;

    @JsonProperty("CHANNEL OF SETTLEMENT")
    private String channelOfSettlement;

    @JsonProperty("MERCHANT ID")
    private String merchantId;

    @JsonProperty("Email")
    private String email;

    @JsonProperty("REJECTION FAILURE REASON")
    private String rejectionFailureReason;

    @JsonProperty("NUMBER OF TRANSACTIONS")
    private String numberOfTransactions;

    @JsonProperty("VALUE OF TRANSACTIONS")
    private String valueOfTransactions;

    public FailedTransactionRecord() {}

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getCustomerAccountNumber() { return customerAccountNumber; }
    public void setCustomerAccountNumber(String customerAccountNumber) { this.customerAccountNumber = customerAccountNumber; }

    public String getChannelOfSettlement() { return channelOfSettlement; }
    public void setChannelOfSettlement(String channelOfSettlement) { this.channelOfSettlement = channelOfSettlement; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRejectionFailureReason() { return rejectionFailureReason; }
    public void setRejectionFailureReason(String rejectionFailureReason) { this.rejectionFailureReason = rejectionFailureReason; }

    public String getNumberOfTransactions() { return numberOfTransactions; }
    public void setNumberOfTransactions(String numberOfTransactions) { this.numberOfTransactions = numberOfTransactions; }

    public String getValueOfTransactions() { return valueOfTransactions; }
    public void setValueOfTransactions(String valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; }
}
