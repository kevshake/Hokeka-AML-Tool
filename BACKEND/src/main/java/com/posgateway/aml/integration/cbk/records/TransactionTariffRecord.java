package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #15 — Transaction Tariffs.
 * Wrapper key: {@code PAYMENT_GATEWAY_TARIFFS}
 * Schedule: Monthly.
 *
 * <p>Field names contain literal spaces — kept verbatim per inventory.
 */
public final class TransactionTariffRecord {

    @JsonProperty("ROW ID")
    private String rowId;

    @JsonProperty("REPORTING DATE")
    private String reportingDate;

    @JsonProperty("CHANNEL USED")
    private String channelUsed;

    @JsonProperty("CHANNEL PARTNER NAME")
    private String channelPartnerName;

    @JsonProperty("CHARGE DESCRIPTION")
    private String chargeDescription;

    @JsonProperty("PERCENTAGE TRANSACTION COST")
    private String percentageTransactionCost;

    @JsonProperty("ABSOLUTE TRANSACTION COST")
    private String absoluteTransactionCost;

    public TransactionTariffRecord() {}

    public String getRowId() { return rowId; }
    public void setRowId(String rowId) { this.rowId = rowId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getChannelUsed() { return channelUsed; }
    public void setChannelUsed(String channelUsed) { this.channelUsed = channelUsed; }

    public String getChannelPartnerName() { return channelPartnerName; }
    public void setChannelPartnerName(String channelPartnerName) { this.channelPartnerName = channelPartnerName; }

    public String getChargeDescription() { return chargeDescription; }
    public void setChargeDescription(String chargeDescription) { this.chargeDescription = chargeDescription; }

    public String getPercentageTransactionCost() { return percentageTransactionCost; }
    public void setPercentageTransactionCost(String percentageTransactionCost) { this.percentageTransactionCost = percentageTransactionCost; }

    public String getAbsoluteTransactionCost() { return absoluteTransactionCost; }
    public void setAbsoluteTransactionCost(String absoluteTransactionCost) { this.absoluteTransactionCost = absoluteTransactionCost; }
}
