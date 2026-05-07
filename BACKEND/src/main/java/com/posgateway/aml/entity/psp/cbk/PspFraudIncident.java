package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #7 – Fraud / Theft / Robbery Incidents (daily).
 * Maps to table psp_fraud_incidents.
 *
 * alert_id_link  -> alerts(alert_id)   nullable FK for traceability.
 * case_id_link   -> compliance_cases(case_id)  nullable FK for traceability.
 */
@Entity
@Table(name = "psp_fraud_incidents", indexes = {
        @Index(name = "idx_psp_fraud_incidents_psp_id",   columnList = "psp_id"),
        @Index(name = "idx_psp_fraud_incidents_alert_id", columnList = "alert_id_link"),
        @Index(name = "idx_psp_fraud_incidents_case_id",  columnList = "case_id_link")
})
public class PspFraudIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "reporting_date")
    private LocalDate reportingDate;

    @Column(name = "sub_county_code", length = 32)
    private String subCountyCode;

    @Column(name = "sub_fraud_code", length = 64)
    private String subFraudCode;

    @Column(name = "fraud_category_flag", length = 16)
    private String fraudCategoryFlag;

    @Column(name = "victim_category", length = 64)
    private String victimCategory;

    @Column(name = "victim_information", columnDefinition = "TEXT")
    private String victimInformation;

    @Column(name = "date_of_occurrence")
    private LocalDate dateOfOccurrence;

    @Column(name = "number_of_incidences")
    private Integer numberOfIncidences;

    @Column(name = "amount_involved", precision = 18, scale = 4)
    private BigDecimal amountInvolved;

    @Column(name = "amount_lost", precision = 18, scale = 4)
    private BigDecimal amountLost;

    @Column(name = "amount_recovered", precision = 18, scale = 4)
    private BigDecimal amountRecovered;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @Column(name = "recovery_details", columnDefinition = "TEXT")
    private String recoveryDetails;

    /** Nullable FK to alerts.alert_id for traceability. */
    @Column(name = "alert_id_link")
    private Long alertIdLink;

    /** Nullable FK to compliance_cases.case_id for traceability. */
    @Column(name = "case_id_link")
    private Long caseIdLink;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspFraudIncident() {}

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

    public static PspFraudIncidentBuilder builder() { return new PspFraudIncidentBuilder(); }

    public static class PspFraudIncidentBuilder {
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

        PspFraudIncidentBuilder() {}

        public PspFraudIncidentBuilder id(Long id) { this.id = id; return this; }
        public PspFraudIncidentBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspFraudIncidentBuilder reportingDate(LocalDate reportingDate) { this.reportingDate = reportingDate; return this; }
        public PspFraudIncidentBuilder subCountyCode(String subCountyCode) { this.subCountyCode = subCountyCode; return this; }
        public PspFraudIncidentBuilder subFraudCode(String subFraudCode) { this.subFraudCode = subFraudCode; return this; }
        public PspFraudIncidentBuilder fraudCategoryFlag(String fraudCategoryFlag) { this.fraudCategoryFlag = fraudCategoryFlag; return this; }
        public PspFraudIncidentBuilder victimCategory(String victimCategory) { this.victimCategory = victimCategory; return this; }
        public PspFraudIncidentBuilder victimInformation(String victimInformation) { this.victimInformation = victimInformation; return this; }
        public PspFraudIncidentBuilder dateOfOccurrence(LocalDate dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; return this; }
        public PspFraudIncidentBuilder numberOfIncidences(Integer numberOfIncidences) { this.numberOfIncidences = numberOfIncidences; return this; }
        public PspFraudIncidentBuilder amountInvolved(BigDecimal amountInvolved) { this.amountInvolved = amountInvolved; return this; }
        public PspFraudIncidentBuilder amountLost(BigDecimal amountLost) { this.amountLost = amountLost; return this; }
        public PspFraudIncidentBuilder amountRecovered(BigDecimal amountRecovered) { this.amountRecovered = amountRecovered; return this; }
        public PspFraudIncidentBuilder actionTaken(String actionTaken) { this.actionTaken = actionTaken; return this; }
        public PspFraudIncidentBuilder recoveryDetails(String recoveryDetails) { this.recoveryDetails = recoveryDetails; return this; }
        public PspFraudIncidentBuilder alertIdLink(Long alertIdLink) { this.alertIdLink = alertIdLink; return this; }
        public PspFraudIncidentBuilder caseIdLink(Long caseIdLink) { this.caseIdLink = caseIdLink; return this; }
        public PspFraudIncidentBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspFraudIncidentBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspFraudIncident build() {
            PspFraudIncident e = new PspFraudIncident();
            e.id = this.id;
            e.pspId = this.pspId;
            e.reportingDate = this.reportingDate;
            e.subCountyCode = this.subCountyCode;
            e.subFraudCode = this.subFraudCode;
            e.fraudCategoryFlag = this.fraudCategoryFlag;
            e.victimCategory = this.victimCategory;
            e.victimInformation = this.victimInformation;
            e.dateOfOccurrence = this.dateOfOccurrence;
            e.numberOfIncidences = this.numberOfIncidences;
            e.amountInvolved = this.amountInvolved;
            e.amountLost = this.amountLost;
            e.amountRecovered = this.amountRecovered;
            e.actionTaken = this.actionTaken;
            e.recoveryDetails = this.recoveryDetails;
            e.alertIdLink = this.alertIdLink;
            e.caseIdLink = this.caseIdLink;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
