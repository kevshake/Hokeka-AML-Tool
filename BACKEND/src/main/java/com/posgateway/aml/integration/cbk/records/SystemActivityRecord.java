package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #9 — System Activity.
 * Wrapper key: {@code SYSTEM_ACTIVITY_INFO}
 * Schedule: Daily — 24 records per submission (one per hour).
 */
public final class SystemActivityRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("HOUR_OF_THE_DAY")
    private String hourOfTheDay;

    @JsonProperty("NUMBER_OF_TXNS_PER_SEC")
    private String numberOfTxnsPerSec;

    @JsonProperty("NUMBER_OF_TRANSACTIONS_PER_HOUR")
    private String numberOfTransactionsPerHour;

    public SystemActivityRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getHourOfTheDay() { return hourOfTheDay; }
    public void setHourOfTheDay(String hourOfTheDay) { this.hourOfTheDay = hourOfTheDay; }

    public String getNumberOfTxnsPerSec() { return numberOfTxnsPerSec; }
    public void setNumberOfTxnsPerSec(String numberOfTxnsPerSec) { this.numberOfTxnsPerSec = numberOfTxnsPerSec; }

    public String getNumberOfTransactionsPerHour() { return numberOfTransactionsPerHour; }
    public void setNumberOfTransactionsPerHour(String numberOfTransactionsPerHour) { this.numberOfTransactionsPerHour = numberOfTransactionsPerHour; }
}
