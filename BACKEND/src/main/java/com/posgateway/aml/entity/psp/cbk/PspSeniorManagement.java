package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #1 – Senior Management Schedule (annual, Jan 5).
 * Maps to table psp_senior_management.
 */
@Entity
@Table(name = "psp_senior_management", indexes = {
        @Index(name = "idx_psp_senior_management_psp_id", columnList = "psp_id")
})
public class PspSeniorManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "officer_names", length = 512)
    private String officerNames;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "designation", length = 128)
    private String designation;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Column(name = "id_no", length = 128)
    private String idNo;

    @Column(name = "tax_id", length = 128)
    private String taxId;

    @Column(name = "qualification", columnDefinition = "TEXT")
    private String qualification;

    @Column(name = "date_of_emp")
    private LocalDate dateOfEmp;

    @Column(name = "emp_type", length = 64)
    private String empType;

    @Column(name = "retirement_dt")
    private LocalDate retirementDt;

    @Column(name = "external_affliates", columnDefinition = "TEXT")
    private String externalAffliates;

    @Column(name = "other_disclosure", columnDefinition = "TEXT")
    private String otherDisclosure;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspSeniorManagement() {}

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

    public String getOfficerNames() { return officerNames; }
    public void setOfficerNames(String officerNames) { this.officerNames = officerNames; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getIdNo() { return idNo; }
    public void setIdNo(String idNo) { this.idNo = idNo; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public LocalDate getDateOfEmp() { return dateOfEmp; }
    public void setDateOfEmp(LocalDate dateOfEmp) { this.dateOfEmp = dateOfEmp; }

    public String getEmpType() { return empType; }
    public void setEmpType(String empType) { this.empType = empType; }

    public LocalDate getRetirementDt() { return retirementDt; }
    public void setRetirementDt(LocalDate retirementDt) { this.retirementDt = retirementDt; }

    public String getExternalAffliates() { return externalAffliates; }
    public void setExternalAffliates(String externalAffliates) { this.externalAffliates = externalAffliates; }

    public String getOtherDisclosure() { return otherDisclosure; }
    public void setOtherDisclosure(String otherDisclosure) { this.otherDisclosure = otherDisclosure; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static PspSeniorManagementBuilder builder() { return new PspSeniorManagementBuilder(); }

    public static class PspSeniorManagementBuilder {
        private Long id;
        private Long pspId;
        private String officerNames;
        private String gender;
        private String designation;
        private LocalDate dob;
        private String nationality;
        private String idNo;
        private String taxId;
        private String qualification;
        private LocalDate dateOfEmp;
        private String empType;
        private LocalDate retirementDt;
        private String externalAffliates;
        private String otherDisclosure;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        PspSeniorManagementBuilder() {}

        public PspSeniorManagementBuilder id(Long id) { this.id = id; return this; }
        public PspSeniorManagementBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspSeniorManagementBuilder officerNames(String officerNames) { this.officerNames = officerNames; return this; }
        public PspSeniorManagementBuilder gender(String gender) { this.gender = gender; return this; }
        public PspSeniorManagementBuilder designation(String designation) { this.designation = designation; return this; }
        public PspSeniorManagementBuilder dob(LocalDate dob) { this.dob = dob; return this; }
        public PspSeniorManagementBuilder nationality(String nationality) { this.nationality = nationality; return this; }
        public PspSeniorManagementBuilder idNo(String idNo) { this.idNo = idNo; return this; }
        public PspSeniorManagementBuilder taxId(String taxId) { this.taxId = taxId; return this; }
        public PspSeniorManagementBuilder qualification(String qualification) { this.qualification = qualification; return this; }
        public PspSeniorManagementBuilder dateOfEmp(LocalDate dateOfEmp) { this.dateOfEmp = dateOfEmp; return this; }
        public PspSeniorManagementBuilder empType(String empType) { this.empType = empType; return this; }
        public PspSeniorManagementBuilder retirementDt(LocalDate retirementDt) { this.retirementDt = retirementDt; return this; }
        public PspSeniorManagementBuilder externalAffliates(String externalAffliates) { this.externalAffliates = externalAffliates; return this; }
        public PspSeniorManagementBuilder otherDisclosure(String otherDisclosure) { this.otherDisclosure = otherDisclosure; return this; }
        public PspSeniorManagementBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspSeniorManagementBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspSeniorManagement build() {
            PspSeniorManagement e = new PspSeniorManagement();
            e.id = this.id;
            e.pspId = this.pspId;
            e.officerNames = this.officerNames;
            e.gender = this.gender;
            e.designation = this.designation;
            e.dob = this.dob;
            e.nationality = this.nationality;
            e.idNo = this.idNo;
            e.taxId = this.taxId;
            e.qualification = this.qualification;
            e.dateOfEmp = this.dateOfEmp;
            e.empType = this.empType;
            e.retirementDt = this.retirementDt;
            e.externalAffliates = this.externalAffliates;
            e.otherDisclosure = this.otherDisclosure;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
