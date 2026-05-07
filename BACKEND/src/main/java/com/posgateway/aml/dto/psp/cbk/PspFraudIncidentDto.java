package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspFraudIncidentDto {

    private Long id;
    private Long pspId;
    private LocalDate reportingDate;
    private String subCountyCode;
    private String subFraudCode;
    private String fraudCategoryFlag;
    private String victimCategory;
    private String victimInformation;
    private LocalDate dateOfOccurrence;
    private Integer numberOfIncidences;
    private BigDecimal amountInvolved;
    private BigDecimal amountLost;
    private BigDecimal amountRecovered;
    private String actionTaken;
    private String recoveryDetails;
    private Long alertIdLink;
    private Long caseIdLink;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspFraudIncidentDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public LocalDate getReportingDate() { return reportingDate; }
    public void setReportingDate(LocalDate reportingDate) { this.reportingDate = reportingDate; }

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

    public LocalDate getDateOfOccurrence() { return dateOfOccurrence; }
    public void setDateOfOccurrence(LocalDate dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; }

    public Integer getNumberOfIncidences() { return numberOfIncidences; }
    public void setNumberOfIncidences(Integer numberOfIncidences) { this.numberOfIncidences = numberOfIncidences; }

    public BigDecimal getAmountInvolved() { return amountInvolved; }
    public void setAmountInvolved(BigDecimal amountInvolved) { this.amountInvolved = amountInvolved; }

    public BigDecimal getAmountLost() { return amountLost; }
    public void setAmountLost(BigDecimal amountLost) { this.amountLost = amountLost; }

    public BigDecimal getAmountRecovered() { return amountRecovered; }
    public void setAmountRecovered(BigDecimal amountRecovered) { this.amountRecovered = amountRecovered; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public String getRecoveryDetails() { return recoveryDetails; }
    public void setRecoveryDetails(String recoveryDetails) { this.recoveryDetails = recoveryDetails; }

    public Long getAlertIdLink() { return alertIdLink; }
    public void setAlertIdLink(Long alertIdLink) { this.alertIdLink = alertIdLink; }

    public Long getCaseIdLink() { return caseIdLink; }
    public void setCaseIdLink(Long caseIdLink) { this.caseIdLink = caseIdLink; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
