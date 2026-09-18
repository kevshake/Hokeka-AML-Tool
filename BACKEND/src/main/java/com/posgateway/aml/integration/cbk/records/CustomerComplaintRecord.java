package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #5 — Customer Complaints.
 * Wrapper key: {@code PSP_CUTOMER_COMPLAINTS}
 * Schedule: Monthly, day 3.
 */
public final class CustomerComplaintRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("COMPLAINT_ID")
    private String complaintId;

    @JsonProperty("COMPLAINT_CODE")
    private String complaintCode;

    @JsonProperty("COMPLAINANT_GENDER")
    private String complainantGender;

    @JsonProperty("COMPLAINT_FREQUENCY")
    private String complaintFrequency;

    @JsonProperty("COMPLAINANT_NAME")
    private String complainantName;

    @JsonProperty("COMPLAINANT_AGE")
    private String complainantAge;

    @JsonProperty("COMPLAINANT_CONTACT_NUMBER")
    private String complainantContactNumber;

    @JsonProperty("COMPLAINANT_SUB_COUNTY_LOCATION")
    private String complainantSubCountyLocation;

    @JsonProperty("COMPLAINANT_EDUCATION_LEVEL")
    private String complainantEducationLevel;

    @JsonProperty("OTHERS_COMPLAINANT_DETAILS")
    private String othersComplainantDetails;

    @JsonProperty("AGENT_ID")
    private String agentId;

    @JsonProperty("DATE_OF_OCCURRENCE")
    private String dateOfOccurrence;

    @JsonProperty("DATE_REPORTED_TO_THE_INSTITUTION")
    private String dateReportedToTheInstitution;

    @JsonProperty("DATE_RESOLVED")
    private String dateResolved;

    @JsonProperty("REMEDIAL_STATUS")
    private String remedialStatus;

    @JsonProperty("AMOUNT_LOST")
    private String amountLost;

    @JsonProperty("AMOUNT_RECOVERED")
    private String amountRecovered;

    public CustomerComplaintRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getComplaintCode() { return complaintCode; }
    public void setComplaintCode(String complaintCode) { this.complaintCode = complaintCode; }

    public String getComplainantGender() { return complainantGender; }
    public void setComplainantGender(String complainantGender) { this.complainantGender = complainantGender; }

    public String getComplaintFrequency() { return complaintFrequency; }
    public void setComplaintFrequency(String complaintFrequency) { this.complaintFrequency = complaintFrequency; }

    public String getComplainantName() { return complainantName; }
    public void setComplainantName(String complainantName) { this.complainantName = complainantName; }

    public String getComplainantAge() { return complainantAge; }
    public void setComplainantAge(String complainantAge) { this.complainantAge = complainantAge; }

    public String getComplainantContactNumber() { return complainantContactNumber; }
    public void setComplainantContactNumber(String complainantContactNumber) { this.complainantContactNumber = complainantContactNumber; }

    public String getComplainantSubCountyLocation() { return complainantSubCountyLocation; }
    public void setComplainantSubCountyLocation(String complainantSubCountyLocation) { this.complainantSubCountyLocation = complainantSubCountyLocation; }

    public String getComplainantEducationLevel() { return complainantEducationLevel; }
    public void setComplainantEducationLevel(String complainantEducationLevel) { this.complainantEducationLevel = complainantEducationLevel; }

    public String getOthersComplainantDetails() { return othersComplainantDetails; }
    public void setOthersComplainantDetails(String othersComplainantDetails) { this.othersComplainantDetails = othersComplainantDetails; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getDateOfOccurrence() { return dateOfOccurrence; }
    public void setDateOfOccurrence(String dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; }

    public String getDateReportedToTheInstitution() { return dateReportedToTheInstitution; }
    public void setDateReportedToTheInstitution(String dateReportedToTheInstitution) { this.dateReportedToTheInstitution = dateReportedToTheInstitution; }

    public String getDateResolved() { return dateResolved; }
    public void setDateResolved(String dateResolved) { this.dateResolved = dateResolved; }

    public String getRemedialStatus() { return remedialStatus; }
    public void setRemedialStatus(String remedialStatus) { this.remedialStatus = remedialStatus; }

    public String getAmountLost() { return amountLost; }
    public void setAmountLost(String amountLost) { this.amountLost = amountLost; }

    public String getAmountRecovered() { return amountRecovered; }
    public void setAmountRecovered(String amountRecovered) { this.amountRecovered = amountRecovered; }
}
