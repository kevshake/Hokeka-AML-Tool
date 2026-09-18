package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #5 – Customer Complaints and Remedials (monthly, day 3).
 * Maps to table psp_customer_complaints.
 */
@Entity
@Table(name = "psp_customer_complaints", indexes = {
        @Index(name = "idx_psp_customer_complaints_psp_id", columnList = "psp_id")
})
public class PspCustomerComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "complaint_id", nullable = false, unique = true, length = 128)
    private String complaintId;

    @Column(name = "complaint_code", length = 64)
    private String complaintCode;

    @Column(name = "complainant_gender", length = 16)
    private String complainantGender;

    @Column(name = "complaint_frequency")
    private Integer complaintFrequency;

    @Column(name = "complainant_name", length = 256)
    private String complainantName;

    @Column(name = "complainant_age")
    private Integer complainantAge;

    @Column(name = "complainant_contact_number", length = 64)
    private String complainantContactNumber;

    @Column(name = "complainant_sub_county_location", length = 128)
    private String complainantSubCountyLocation;

    @Column(name = "complainant_education_level", length = 64)
    private String complainantEducationLevel;

    @Column(name = "others_complainant_details", columnDefinition = "TEXT")
    private String othersComplainantDetails;

    @Column(name = "agent_id", length = 128)
    private String agentId;

    @Column(name = "date_of_occurrence")
    private LocalDate dateOfOccurrence;

    @Column(name = "date_reported_to_the_institution")
    private LocalDate dateReportedToTheInstitution;

    @Column(name = "date_resolved")
    private LocalDate dateResolved;

    @Column(name = "remedial_status", length = 64)
    private String remedialStatus;

    @Column(name = "amount_lost", precision = 18, scale = 4)
    private BigDecimal amountLost;

    @Column(name = "amount_recovered", precision = 18, scale = 4)
    private BigDecimal amountRecovered;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspCustomerComplaint() {}

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

    public static PspCustomerComplaintBuilder builder() { return new PspCustomerComplaintBuilder(); }

    public static class PspCustomerComplaintBuilder {
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

        PspCustomerComplaintBuilder() {}

        public PspCustomerComplaintBuilder id(Long id) { this.id = id; return this; }
        public PspCustomerComplaintBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspCustomerComplaintBuilder complaintId(String complaintId) { this.complaintId = complaintId; return this; }
        public PspCustomerComplaintBuilder complaintCode(String complaintCode) { this.complaintCode = complaintCode; return this; }
        public PspCustomerComplaintBuilder complainantGender(String complainantGender) { this.complainantGender = complainantGender; return this; }
        public PspCustomerComplaintBuilder complaintFrequency(Integer complaintFrequency) { this.complaintFrequency = complaintFrequency; return this; }
        public PspCustomerComplaintBuilder complainantName(String complainantName) { this.complainantName = complainantName; return this; }
        public PspCustomerComplaintBuilder complainantAge(Integer complainantAge) { this.complainantAge = complainantAge; return this; }
        public PspCustomerComplaintBuilder complainantContactNumber(String complainantContactNumber) { this.complainantContactNumber = complainantContactNumber; return this; }
        public PspCustomerComplaintBuilder complainantSubCountyLocation(String complainantSubCountyLocation) { this.complainantSubCountyLocation = complainantSubCountyLocation; return this; }
        public PspCustomerComplaintBuilder complainantEducationLevel(String complainantEducationLevel) { this.complainantEducationLevel = complainantEducationLevel; return this; }
        public PspCustomerComplaintBuilder othersComplainantDetails(String othersComplainantDetails) { this.othersComplainantDetails = othersComplainantDetails; return this; }
        public PspCustomerComplaintBuilder agentId(String agentId) { this.agentId = agentId; return this; }
        public PspCustomerComplaintBuilder dateOfOccurrence(LocalDate dateOfOccurrence) { this.dateOfOccurrence = dateOfOccurrence; return this; }
        public PspCustomerComplaintBuilder dateReportedToTheInstitution(LocalDate dateReportedToTheInstitution) { this.dateReportedToTheInstitution = dateReportedToTheInstitution; return this; }
        public PspCustomerComplaintBuilder dateResolved(LocalDate dateResolved) { this.dateResolved = dateResolved; return this; }
        public PspCustomerComplaintBuilder remedialStatus(String remedialStatus) { this.remedialStatus = remedialStatus; return this; }
        public PspCustomerComplaintBuilder amountLost(BigDecimal amountLost) { this.amountLost = amountLost; return this; }
        public PspCustomerComplaintBuilder amountRecovered(BigDecimal amountRecovered) { this.amountRecovered = amountRecovered; return this; }
        public PspCustomerComplaintBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspCustomerComplaintBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspCustomerComplaint build() {
            PspCustomerComplaint e = new PspCustomerComplaint();
            e.id = this.id;
            e.pspId = this.pspId;
            e.complaintId = this.complaintId;
            e.complaintCode = this.complaintCode;
            e.complainantGender = this.complainantGender;
            e.complaintFrequency = this.complaintFrequency;
            e.complainantName = this.complainantName;
            e.complainantAge = this.complainantAge;
            e.complainantContactNumber = this.complainantContactNumber;
            e.complainantSubCountyLocation = this.complainantSubCountyLocation;
            e.complainantEducationLevel = this.complainantEducationLevel;
            e.othersComplainantDetails = this.othersComplainantDetails;
            e.agentId = this.agentId;
            e.dateOfOccurrence = this.dateOfOccurrence;
            e.dateReportedToTheInstitution = this.dateReportedToTheInstitution;
            e.dateResolved = this.dateResolved;
            e.remedialStatus = this.remedialStatus;
            e.amountLost = this.amountLost;
            e.amountRecovered = this.amountRecovered;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
