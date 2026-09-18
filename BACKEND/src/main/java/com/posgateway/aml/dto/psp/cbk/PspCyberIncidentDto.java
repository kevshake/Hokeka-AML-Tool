package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PspCyberIncidentDto {

    private Long id;
    private Long pspId;
    private String incidentNumber;
    private LocalDateTime incidentDate;
    private String locationOfAttacker;
    private String incidentMode;
    private String lossType;
    private String details;
    private String actionTaken;
    private LocalDateTime resolutionDate;
    private String mitigationActions;
    private BigDecimal amountInvolved;
    private BigDecimal amountLost;
    private String currency;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspCyberIncidentDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getIncidentNumber() { return incidentNumber; }
    public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }

    public LocalDateTime getIncidentDate() { return incidentDate; }
    public void setIncidentDate(LocalDateTime incidentDate) { this.incidentDate = incidentDate; }

    public String getLocationOfAttacker() { return locationOfAttacker; }
    public void setLocationOfAttacker(String locationOfAttacker) { this.locationOfAttacker = locationOfAttacker; }

    public String getIncidentMode() { return incidentMode; }
    public void setIncidentMode(String incidentMode) { this.incidentMode = incidentMode; }

    public String getLossType() { return lossType; }
    public void setLossType(String lossType) { this.lossType = lossType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public LocalDateTime getResolutionDate() { return resolutionDate; }
    public void setResolutionDate(LocalDateTime resolutionDate) { this.resolutionDate = resolutionDate; }

    public String getMitigationActions() { return mitigationActions; }
    public void setMitigationActions(String mitigationActions) { this.mitigationActions = mitigationActions; }

    public BigDecimal getAmountInvolved() { return amountInvolved; }
    public void setAmountInvolved(BigDecimal amountInvolved) { this.amountInvolved = amountInvolved; }

    public BigDecimal getAmountLost() { return amountLost; }
    public void setAmountLost(BigDecimal amountLost) { this.amountLost = amountLost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
