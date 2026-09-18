package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #7 — Fraud / Theft / Robbery Incidents.
 * Wrapper key: {@code INCIDENTS_DATA}
 * Schedule: Daily.
 */
public final class FraudIncidentRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("SUB_COUNTY_CODE")
    private String subCountyCode;

    @JsonProperty("SUB_FRAUD_CODE")
    private String subFraudCode;

    @JsonProperty("FRAUD_CATEGORY_FLAG")
    private String fraudCategoryFlag;

    @JsonProperty("VICTIM_CATEGORY")
    private String victimCategory;

    @JsonProperty("VICTIM_INFORMATION")
    private String victimInformation;

    @JsonProperty("DATE_OF_OCCURRENCE")
    private String dateOfOccurrence;

    @JsonProperty("NUMBER_OF_INCIDENCES")
    private String numberOfIncidences;

    @JsonProperty("AMOUNT_INVOLVED")
    private String amountInvolved;

    @JsonProperty("AMOUNT_LOST")
    private String amountLost;

    @JsonProperty("AMOUNT_RECOVERED")
    private String amountRecovered;

    @JsonProperty("ACTION_TAKEN")
    private String actionTaken;

    @JsonProperty("RECOVERY_DETAILS")
    private String recoveryDetails;

    public FraudIncidentRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getSubCountyCode() { return subCountyCode; }
    public void setSubCountyCode(String subCountyCode) { this.subCountyCode = subCountyCode; }

    public String getSubFraudCode() { return subFraudCode; }
    public void setSubFraudCode(String subFraudCode) { this.subFraudCode = subFraudCode; }

    public String getFraudCategoryFlag() { return fraudCategoryFlag; }
    public void setFraudCategoryFlag(String fraudCategoryFlag) { this.fraudCategoryFlag = fraudCategoryFlag; }

    public String getVictimCategory() { return victimCategory; }
    public void setVictimCategory(String victimCategory) { this.victimCategory = victimCategory; }

    public String getVictimInformation() { return victimInformation; }
    public void setVictimInformation(String victimInformation) { this.victimInformation = victimInformation; }

    public String getDateOfOccurrence() { return dateOfOccurrence; }
    public void setDateOfOccurrence(String dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; }

    public String getNumberOfIncidences() { return numberOfIncidences; }
    public void setNumberOfIncidences(String numberOfIncidences) { this.numberOfIncidences = numberOfIncidences; }

    public String getAmountInvolved() { return amountInvolved; }
    public void setAmountInvolved(String amountInvolved) { this.amountInvolved = amountInvolved; }

    public String getAmountLost() { return amountLost; }
    public void setAmountLost(String amountLost) { this.amountLost = amountLost; }

    public String getAmountRecovered() { return amountRecovered; }
    public void setAmountRecovered(String amountRecovered) { this.amountRecovered = amountRecovered; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public String getRecoveryDetails() { return recoveryDetails; }
    public void setRecoveryDetails(String recoveryDetails) { this.recoveryDetails = recoveryDetails; }
}
