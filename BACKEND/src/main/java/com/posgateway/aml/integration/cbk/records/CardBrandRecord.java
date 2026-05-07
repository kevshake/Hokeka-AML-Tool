package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #12 — Card Brands.
 * Wrapper key: {@code PYMT_GW_CARD_BRANDS}
 * Schedule: Monthly, day 2.
 * Card brand codes: CDB01 = Visa, CDB02 = Mastercard.
 */
public final class CardBrandRecord {

    @JsonProperty("ROW_ID")
    private String rowId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("BANK_ID")
    private String bankId;

    @JsonProperty("TRANSACTION_CATEGORY")
    private String transactionCategory;

    @JsonProperty("CARD_BRAND_TYPE")
    private String cardBrandType;

    @JsonProperty("NUMBER_OF_TXNS")
    private String numberOfTxns;

    @JsonProperty("VALUE_OF_TXNS")
    private String valueOfTxns;

    public CardBrandRecord() {}

    public String getRowId() { return rowId; }
    public void setRowId(String rowId) { this.rowId = rowId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getTransactionCategory() { return transactionCategory; }
    public void setTransactionCategory(String transactionCategory) { this.transactionCategory = transactionCategory; }

    public String getCardBrandType() { return cardBrandType; }
    public void setCardBrandType(String cardBrandType) { this.cardBrandType = cardBrandType; }

    public String getNumberOfTxns() { return numberOfTxns; }
    public void setNumberOfTxns(String numberOfTxns) { this.numberOfTxns = numberOfTxns; }

    public String getValueOfTxns() { return valueOfTxns; }
    public void setValueOfTxns(String valueOfTxns) { this.valueOfTxns = valueOfTxns; }
}
