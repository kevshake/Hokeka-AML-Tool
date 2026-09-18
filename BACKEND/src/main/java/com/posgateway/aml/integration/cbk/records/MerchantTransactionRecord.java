package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #16 — Merchant Transactions.
 * Wrapper key: {@code MERCHANT_STLMNT_ACCT_DATA}
 * Schedule: Daily (previous day successes).
 *
 * <p>Field names contain literal spaces — kept verbatim per inventory.
 */
public final class MerchantTransactionRecord {

    @JsonProperty("BANK ID")
    private String bankId;

    @JsonProperty("REPORTING DATE")
    private String reportingDate;

    @JsonProperty("MERCHANT ACCOUNT NUMBER")
    private String merchantAccountNumber;

    @JsonProperty("CHANNEL OF SETTLEMENT")
    private String channelOfSettlement;

    @JsonProperty("MERCHANT ID")
    private String merchantId;

    @JsonProperty("EMAIL ADDRESS")
    private String emailAddress;

    @JsonProperty("MERCHANT COUNTRY")
    private String merchantCountry;

    @JsonProperty("ECONOMIC SECTORS")
    private String economicSectors;

    @JsonProperty("NUMBER OF TRANSACTIONS")
    private String numberOfTransactions;

    @JsonProperty("VALUE OF TRANSACTIONS")
    private String valueOfTransactions;

    public MerchantTransactionRecord() {}

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getMerchantAccountNumber() { return merchantAccountNumber; }
    public void setMerchantAccountNumber(String merchantAccountNumber) { this.merchantAccountNumber = merchantAccountNumber; }

    public String getChannelOfSettlement() { return channelOfSettlement; }
    public void setChannelOfSettlement(String channelOfSettlement) { this.channelOfSettlement = channelOfSettlement; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getMerchantCountry() { return merchantCountry; }
    public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }

    public String getEconomicSectors() { return economicSectors; }
    public void setEconomicSectors(String economicSectors) { this.economicSectors = economicSectors; }

    public String getNumberOfTransactions() { return numberOfTransactions; }
    public void setNumberOfTransactions(String numberOfTransactions) { this.numberOfTransactions = numberOfTransactions; }

    public String getValueOfTransactions() { return valueOfTransactions; }
    public void setValueOfTransactions(String valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; }
}
