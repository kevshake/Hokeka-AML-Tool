package com.posgateway.aml.dto.psp.cbk;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspSeniorManagementDto {

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

    public PspSeniorManagementDto() {}

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
}
