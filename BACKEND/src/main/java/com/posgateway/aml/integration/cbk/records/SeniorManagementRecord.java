package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #1 — Senior Management Schedule.
 * Wrapper key: {@code SENIOR_MNGT_SCHEDULE}
 */
public final class SeniorManagementRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("OFFICER_NAMES")
    private String officerNames;

    @JsonProperty("GENDER")
    private String gender;

    @JsonProperty("DESIGNATION")
    private String designation;

    @JsonProperty("DOB")
    private String dob;

    @JsonProperty("NATIONALITY")
    private String nationality;

    @JsonProperty("ID_NO")
    private String idNo;

    @JsonProperty("TAX_ID")
    private String taxId;

    @JsonProperty("QUALIFICATION")
    private String qualification;

    @JsonProperty("DATE_OF_EMP")
    private String dateOfEmp;

    @JsonProperty("EMP_TYPE")
    private String empType;

    @JsonProperty("RETIREMENT_DT")
    private String retirementDt;

    @JsonProperty("EXTERNAL_AFFLIATES")
    private String externalAffliates;

    @JsonProperty("OTHER_DISCLOSURE")
    private String otherDisclosure;

    public SeniorManagementRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getOfficerNames() { return officerNames; }
    public void setOfficerNames(String officerNames) { this.officerNames = officerNames; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getIdNo() { return idNo; }
    public void setIdNo(String idNo) { this.idNo = idNo; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public String getDateOfEmp() { return dateOfEmp; }
    public void setDateOfEmp(String dateOfEmp) { this.dateOfEmp = dateOfEmp; }

    public String getEmpType() { return empType; }
    public void setEmpType(String empType) { this.empType = empType; }

    public String getRetirementDt() { return retirementDt; }
    public void setRetirementDt(String retirementDt) { this.retirementDt = retirementDt; }

    public String getExternalAffliates() { return externalAffliates; }
    public void setExternalAffliates(String externalAffliates) { this.externalAffliates = externalAffliates; }

    public String getOtherDisclosure() { return otherDisclosure; }
    public void setOtherDisclosure(String otherDisclosure) { this.otherDisclosure = otherDisclosure; }
}
