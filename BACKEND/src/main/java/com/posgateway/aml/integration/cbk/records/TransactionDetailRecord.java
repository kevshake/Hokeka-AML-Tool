package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #14 — Transaction Details.
 * Wrapper key: {@code PAYMENT_GATEWAY_TRANSACTIONS_DETAILS}
 * Schedule: Monthly.
 *
 * <p>Field names contain literal spaces — kept verbatim per inventory.
 */
public final class TransactionDetailRecord {

    @JsonProperty("Reporting date")
    private String reportingDate;

    @JsonProperty("Row ID")
    private String rowId;

    @JsonProperty("Card brand type")
    private String cardBrandType;

    @JsonProperty("Card type")
    private String cardType;

    @JsonProperty("Card class type")
    private String cardClassType;

    @JsonProperty("Mobile money partner ID")
    private String mobileMonePartnerId;

    @JsonProperty("Mobile banking partner ID")
    private String mobileBankingPartnerId;

    @JsonProperty("Transaction category type")
    private String transactionCategoryType;

    @JsonProperty("Channel type")
    private String channelType;

    @JsonProperty("Total number of transactions done")
    private String totalNumberOfTransactionsDone;

    @JsonProperty("Total value of transactions done")
    private String totalValueOfTransactionsDone;

    public TransactionDetailRecord() {}

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getRowId() { return rowId; }
    public void setRowId(String rowId) { this.rowId = rowId; }

    public String getCardBrandType() { return cardBrandType; }
    public void setCardBrandType(String cardBrandType) { this.cardBrandType = cardBrandType; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getCardClassType() { return cardClassType; }
    public void setCardClassType(String cardClassType) { this.cardClassType = cardClassType; }

    public String getMobileMonePartnerId() { return mobileMonePartnerId; }
    public void setMobileMonePartnerId(String mobileMonePartnerId) { this.mobileMonePartnerId = mobileMonePartnerId; }

    public String getMobileBankingPartnerId() { return mobileBankingPartnerId; }
    public void setMobileBankingPartnerId(String mobileBankingPartnerId) { this.mobileBankingPartnerId = mobileBankingPartnerId; }

    public String getTransactionCategoryType() { return transactionCategoryType; }
    public void setTransactionCategoryType(String transactionCategoryType) { this.transactionCategoryType = transactionCategoryType; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getTotalNumberOfTransactionsDone() { return totalNumberOfTransactionsDone; }
    public void setTotalNumberOfTransactionsDone(String totalNumberOfTransactionsDone) { this.totalNumberOfTransactionsDone = totalNumberOfTransactionsDone; }

    public String getTotalValueOfTransactionsDone() { return totalValueOfTransactionsDone; }
    public void setTotalValueOfTransactionsDone(String totalValueOfTransactionsDone) { this.totalValueOfTransactionsDone = totalValueOfTransactionsDone; }
}
