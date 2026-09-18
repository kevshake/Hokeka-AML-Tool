package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #6 — Cybersecurity Incident Record.
 * Wrapper key: {@code PSP_CYBERSECURITY_INCIDENT_RECORD}
 * Schedule: Daily.
 */
public final class CyberIncidentRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("INCIDENT_NUMBER")
    private String incidentNumber;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("LOCATION_OF_ATTACKER")
    private String locationOfAttacker;

    @JsonProperty("INCIDENT_MODE")
    private String incidentMode;

    @JsonProperty("DATE_AND_TIME_OF_INCIDENT_HAPPENED")
    private String dateAndTimeOfIncidentHappened;

    @JsonProperty("LOSS_TYPE")
    private String lossType;

    @JsonProperty("DETAILS_OF_THE_INCIDENT")
    private String detailsOfTheIncident;

    @JsonProperty("ACTION_TAKEN_TO_MANAGE_THE_INCIDENT")
    private String actionTakenToManageTheIncident;

    @JsonProperty("DATE_AND_TIME_OF_THE_INCIDENT_RESOLUTION")
    private String dateAndTimeOfTheIncidentResolution;

    @JsonProperty("ACTION_TAKEN_TO_MITIGATE_FUTURE_INCIDENTS")
    private String actionTakenToMitigateFutureIncidents;

    @JsonProperty("AMOUNT_INVOLVED")
    private String amountInvolved;

    @JsonProperty("AMOUNT_LOST")
    private String amountLost;

    public CyberIncidentRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getIncidentNumber() { return incidentNumber; }
    public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getLocationOfAttacker() { return locationOfAttacker; }
    public void setLocationOfAttacker(String locationOfAttacker) { this.locationOfAttacker = locationOfAttacker; }

    public String getIncidentMode() { return incidentMode; }
    public void setIncidentMode(String incidentMode) { this.incidentMode = incidentMode; }

    public String getDateAndTimeOfIncidentHappened() { return dateAndTimeOfIncidentHappened; }
    public void setDateAndTimeOfIncidentHappened(String dateAndTimeOfIncidentHappened) { this.dateAndTimeOfIncidentHappened = dateAndTimeOfIncidentHappened; }

    public String getLossType() { return lossType; }
    public void setLossType(String lossType) { this.lossType = lossType; }

    public String getDetailsOfTheIncident() { return detailsOfTheIncident; }
    public void setDetailsOfTheIncident(String detailsOfTheIncident) { this.detailsOfTheIncident = detailsOfTheIncident; }

    public String getActionTakenToManageTheIncident() { return actionTakenToManageTheIncident; }
    public void setActionTakenToManageTheIncident(String actionTakenToManageTheIncident) { this.actionTakenToManageTheIncident = actionTakenToManageTheIncident; }

    public String getDateAndTimeOfTheIncidentResolution() { return dateAndTimeOfTheIncidentResolution; }
    public void setDateAndTimeOfTheIncidentResolution(String dateAndTimeOfTheIncidentResolution) { this.dateAndTimeOfTheIncidentResolution = dateAndTimeOfTheIncidentResolution; }

    public String getActionTakenToMitigateFutureIncidents() { return actionTakenToMitigateFutureIncidents; }
    public void setActionTakenToMitigateFutureIncidents(String actionTakenToMitigateFutureIncidents) { this.actionTakenToMitigateFutureIncidents = actionTakenToMitigateFutureIncidents; }

    public String getAmountInvolved() { return amountInvolved; }
    public void setAmountInvolved(String amountInvolved) { this.amountInvolved = amountInvolved; }

    public String getAmountLost() { return amountLost; }
    public void setAmountLost(String amountLost) { this.amountLost = amountLost; }
}
