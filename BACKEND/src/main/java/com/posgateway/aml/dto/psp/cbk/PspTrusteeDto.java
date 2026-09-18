package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspTrusteeDto {

    private Long id;
    private Long pspId;
    private String trustCompName;
    private String directorsTrustComp;
    private String trusteeNames;
    private String trusteeGender;
    private LocalDate dob;
    private String nationality;
    private String residentCountry;
    private String idNoPassport;
    private String pin;
    private String contactNumber;
    private String qualifications;
    private String othersTrusteeships;
    private String disclosures;
    private String shareholders;
    private BigDecimal shareholdingPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspTrusteeDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getTrustCompName() { return trustCompName; }
    public void setTrustCompName(String trustCompName) { this.trustCompName = trustCompName; }

    public String getDirectorsTrustComp() { return directorsTrustComp; }
    public void setDirectorsTrustComp(String directorsTrustComp) { this.directorsTrustComp = directorsTrustComp; }

    public String getTrusteeNames() { return trusteeNames; }
    public void setTrusteeNames(String trusteeNames) { this.trusteeNames = trusteeNames; }

    public String getTrusteeGender() { return trusteeGender; }
    public void setTrusteeGender(String trusteeGender) { this.trusteeGender = trusteeGender; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getResidentCountry() { return residentCountry; }
    public void setResidentCountry(String residentCountry) { this.residentCountry = residentCountry; }

    public String getIdNoPassport() { return idNoPassport; }
    public void setIdNoPassport(String idNoPassport) { this.idNoPassport = idNoPassport; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }

    public String getOthersTrusteeships() { return othersTrusteeships; }
    public void setOthersTrusteeships(String othersTrusteeships) { this.othersTrusteeships = othersTrusteeships; }

    public String getDisclosures() { return disclosures; }
    public void setDisclosures(String disclosures) { this.disclosures = disclosures; }

    public String getShareholders() { return shareholders; }
    public void setShareholders(String shareholders) { this.shareholders = shareholders; }

    public BigDecimal getShareholdingPercentage() { return shareholdingPercentage; }
    public void setShareholdingPercentage(BigDecimal shareholdingPercentage) { this.shareholdingPercentage = shareholdingPercentage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
