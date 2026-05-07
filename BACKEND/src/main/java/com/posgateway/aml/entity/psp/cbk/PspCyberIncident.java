package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CBK GDI #6 – Cybersecurity Incident Records (daily).
 * Maps to table psp_cyber_incidents.
 */
@Entity
@Table(name = "psp_cyber_incidents", indexes = {
        @Index(name = "idx_psp_cyber_incidents_psp_id", columnList = "psp_id")
})
public class PspCyberIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "incident_number", nullable = false, unique = true, length = 128)
    private String incidentNumber;

    @Column(name = "incident_date")
    private LocalDateTime incidentDate;

    @Column(name = "location_of_attacker", length = 256)
    private String locationOfAttacker;

    @Column(name = "incident_mode", length = 128)
    private String incidentMode;

    @Column(name = "loss_type", length = 128)
    private String lossType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @Column(name = "resolution_date")
    private LocalDateTime resolutionDate;

    @Column(name = "mitigation_actions", columnDefinition = "TEXT")
    private String mitigationActions;

    @Column(name = "amount_involved", precision = 18, scale = 4)
    private BigDecimal amountInvolved;

    @Column(name = "amount_lost", precision = 18, scale = 4)
    private BigDecimal amountLost;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspCyberIncident() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public static PspCyberIncidentBuilder builder() { return new PspCyberIncidentBuilder(); }

    public static class PspCyberIncidentBuilder {
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

        PspCyberIncidentBuilder() {}

        public PspCyberIncidentBuilder id(Long id) { this.id = id; return this; }
        public PspCyberIncidentBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspCyberIncidentBuilder incidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; return this; }
        public PspCyberIncidentBuilder incidentDate(LocalDateTime incidentDate) { this.incidentDate = incidentDate; return this; }
        public PspCyberIncidentBuilder locationOfAttacker(String locationOfAttacker) { this.locationOfAttacker = locationOfAttacker; return this; }
        public PspCyberIncidentBuilder incidentMode(String incidentMode) { this.incidentMode = incidentMode; return this; }
        public PspCyberIncidentBuilder lossType(String lossType) { this.lossType = lossType; return this; }
        public PspCyberIncidentBuilder details(String details) { this.details = details; return this; }
        public PspCyberIncidentBuilder actionTaken(String actionTaken) { this.actionTaken = actionTaken; return this; }
        public PspCyberIncidentBuilder resolutionDate(LocalDateTime resolutionDate) { this.resolutionDate = resolutionDate; return this; }
        public PspCyberIncidentBuilder mitigationActions(String mitigationActions) { this.mitigationActions = mitigationActions; return this; }
        public PspCyberIncidentBuilder amountInvolved(BigDecimal amountInvolved) { this.amountInvolved = amountInvolved; return this; }
        public PspCyberIncidentBuilder amountLost(BigDecimal amountLost) { this.amountLost = amountLost; return this; }
        public PspCyberIncidentBuilder currency(String currency) { this.currency = currency; return this; }
        public PspCyberIncidentBuilder createdBy(Long createdBy) { this.createdBy = createdBy; return this; }
        public PspCyberIncidentBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspCyberIncidentBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspCyberIncident build() {
            PspCyberIncident e = new PspCyberIncident();
            e.id = this.id;
            e.pspId = this.pspId;
            e.incidentNumber = this.incidentNumber;
            e.incidentDate = this.incidentDate;
            e.locationOfAttacker = this.locationOfAttacker;
            e.incidentMode = this.incidentMode;
            e.lossType = this.lossType;
            e.details = this.details;
            e.actionTaken = this.actionTaken;
            e.resolutionDate = this.resolutionDate;
            e.mitigationActions = this.mitigationActions;
            e.amountInvolved = this.amountInvolved;
            e.amountLost = this.amountLost;
            e.currency = this.currency;
            e.createdBy = this.createdBy;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
