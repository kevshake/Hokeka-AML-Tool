package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspCustomerComplaintDto {

    private Long id;
    private Long pspId;
    private String complaintId;
    private String complaintCode;
    private String complainantGender;
    private Integer complaintFrequency;
    private String complainantName;
    private Integer complainantAge;
    private String complainantContactNumber;
    private String complainantSubCountyLocation;
    private String complainantEducationLevel;
    private String othersComplainantDetails;
    private String agentId;
    private LocalDate dateOfOccurrence;
    private LocalDate dateReportedToTheInstitution;
    private LocalDate dateResolved;
    private String remedialStatus;
    private BigDecimal amountLost;
    private BigDecimal amountRecovered;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspCustomerComplaintDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getComplaintCode() { return complaintCode; }
    public void setComplaintCode(String complaintCode) { this.complaintCode = complaintCode; }

    public String getComplainantGender() { return complainantGender; }
    public void setComplainantGender(String complainantGender) { this.complainantGender = complainantGender; }

    public Integer getComplaintFrequency() { return complaintFrequency; }
    public void setComplaintFrequency(Integer complaintFrequency) { this.complaintFrequency = complaintFrequency; }

    public String getComplainantName() { return complainantName; }
    public void setComplainantName(String complainantName) { this.complainantName = complainantName; }

    public Integer getComplainantAge() { return complainantAge; }
    public void setComplainantAge(Integer complainantAge) { this.complainantAge = complainantAge; }

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

    public LocalDate getDateOfOccurrence() { return dateOfOccurrence; }
    public void setDateOfOccurrence(LocalDate dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; }

    public LocalDate getDateReportedToTheInstitution() { return dateReportedToTheInstitution; }
    public void setDateReportedToTheInstitution(LocalDate dateReportedToTheInstitution) { this.dateReportedToTheInstitution = dateReportedToTheInstitution; }

    public LocalDate getDateResolved() { return dateResolved; }
    public void setDateResolved(LocalDate dateResolved) { this.dateResolved = dateResolved; }

    public String getRemedialStatus() { return remedialStatus; }
    public void setRemedialStatus(String remedialStatus) { this.remedialStatus = remedialStatus; }

    public BigDecimal getAmountLost() { return amountLost; }
    public void setAmountLost(BigDecimal amountLost) { this.amountLost = amountLost; }

    public BigDecimal getAmountRecovered() { return amountRecovered; }
    public void setAmountRecovered(BigDecimal amountRecovered) { this.amountRecovered = amountRecovered; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
