package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #13 — Billing Template.
 * Wrapper key: {@code PAY_GTWAY_BILL_TEMP}
 * Schedule: Daily.
 */
public final class BillingTemplateRecord {

    @JsonProperty("ROW_ID")
    private String rowId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("BILL_CLASSIFICATION_CODE")
    private String billClassificationCode;

    @JsonProperty("NUMBER_OF_TRANSACTION")
    private String numberOfTransaction;

    @JsonProperty("VALUE_OF_TRANSACTIONS")
    private String valueOfTransactions;

    public BillingTemplateRecord() {}

    public String getRowId() { return rowId; }
    public void setRowId(String rowId) { this.rowId = rowId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getBillClassificationCode() { return billClassificationCode; }
    public void setBillClassificationCode(String billClassificationCode) { this.billClassificationCode = billClassificationCode; }

    public String getNumberOfTransaction() { return numberOfTransaction; }
    public void setNumberOfTransaction(String numberOfTransaction) { this.numberOfTransaction = numberOfTransaction; }

    public String getValueOfTransactions() { return valueOfTransactions; }
    public void setValueOfTransactions(String valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; }
}
