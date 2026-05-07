package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspShareholderDto {

    private Long id;
    private Long pspId;
    private String shareholderName;
    private String shareholderGender;
    private String shareholderType;
    private LocalDate dobOrRegDate;
    private String nationality;
    private String residentCountry;
    private String countryOfInc;
    private String idNoPassport;
    private String pin;
    private String contactNumber;
    private String qualifications;
    private String previousEmployment;
    private LocalDate onboardingDate;
    private Long noOfSharesHeld;
    private BigDecimal shareValue;
    private BigDecimal percentageOfShare;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspShareholderDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getShareholderName() { return shareholderName; }
    public void setShareholderName(String shareholderName) { this.shareholderName = shareholderName; }

    public String getShareholderGender() { return shareholderGender; }
    public void setShareholderGender(String shareholderGender) { this.shareholderGender = shareholderGender; }

    public String getShareholderType() { return shareholderType; }
    public void setShareholderType(String shareholderType) { this.shareholderType = shareholderType; }

    public LocalDate getDobOrRegDate() { return dobOrRegDate; }
    public void setDobOrRegDate(LocalDate dobOrRegDate) { this.dobOrRegDate = dobOrRegDate; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getResidentCountry() { return residentCountry; }
    public void setResidentCountry(String residentCountry) { this.residentCountry = residentCountry; }

    public String getCountryOfInc() { return countryOfInc; }
    public void setCountryOfInc(String countryOfInc) { this.countryOfInc = countryOfInc; }

    public String getIdNoPassport() { return idNoPassport; }
    public void setIdNoPassport(String idNoPassport) { this.idNoPassport = idNoPassport; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }

    public String getPreviousEmployment() { return previousEmployment; }
    public void setPreviousEmployment(String previousEmployment) { this.previousEmployment = previousEmployment; }

    public LocalDate getOnboardingDate() { return onboardingDate; }
    public void setOnboardingDate(LocalDate onboardingDate) { this.onboardingDate = onboardingDate; }

    public Long getNoOfSharesHeld() { return noOfSharesHeld; }
    public void setNoOfSharesHeld(Long noOfSharesHeld) { this.noOfSharesHeld = noOfSharesHeld; }

    public BigDecimal getShareValue() { return shareValue; }
    public void setShareValue(BigDecimal shareValue) { this.shareValue = shareValue; }

    public BigDecimal getPercentageOfShare() { return percentageOfShare; }
    public void setPercentageOfShare(BigDecimal percentageOfShare) { this.percentageOfShare = percentageOfShare; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
