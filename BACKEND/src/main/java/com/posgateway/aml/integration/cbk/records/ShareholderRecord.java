package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #4 — Schedule of Shareholders.
 * Wrapper key: {@code SCHED_OF_SHARE_HLDRS}
 */
public final class ShareholderRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("SHAREHOLDER_NAME")
    private String shareholderName;

    @JsonProperty("SHAREHOLDER_GENDER")
    private String shareholderGender;

    @JsonProperty("SHAREHOLDER_TYPE")
    private String shareholderType;

    @JsonProperty("DOB_OR_REG_DATE")
    private String dobOrRegDate;

    @JsonProperty("NATIONALITY")
    private String nationality;

    @JsonProperty("RESIDENT_COUNTRY")
    private String residentCountry;

    @JsonProperty("COUNTRY_OF_INC")
    private String countryOfInc;

    @JsonProperty("ID_NO_PASSPORT")
    private String idNoPassport;

    @JsonProperty("PIN")
    private String pin;

    @JsonProperty("CONTACT_NUMBER")
    private String contactNumber;

    @JsonProperty("QUALIFICATIONS")
    private String qualifications;

    @JsonProperty("PREVIOUS_EMPLOYMENT")
    private String previousEmployment;

    @JsonProperty("ONBOARDING_DATE")
    private String onboardingDate;

    @JsonProperty("NO_OF_SHARES_HELD")
    private String noOfSharesHeld;

    @JsonProperty("SHARE_VALUE")
    private String shareValue;

    @JsonProperty("PERCENTAGE_OF_SHARE")
    private String percentageOfShare;

    public ShareholderRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getShareholderName() { return shareholderName; }
    public void setShareholderName(String shareholderName) { this.shareholderName = shareholderName; }

    public String getShareholderGender() { return shareholderGender; }
    public void setShareholderGender(String shareholderGender) { this.shareholderGender = shareholderGender; }

    public String getShareholderType() { return shareholderType; }
    public void setShareholderType(String shareholderType) { this.shareholderType = shareholderType; }

    public String getDobOrRegDate() { return dobOrRegDate; }
    public void setDobOrRegDate(String dobOrRegDate) { this.dobOrRegDate = dobOrRegDate; }

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

    public String getOnboardingDate() { return onboardingDate; }
    public void setOnboardingDate(String onboardingDate) { this.onboardingDate = onboardingDate; }

    public String getNoOfSharesHeld() { return noOfSharesHeld; }
    public void setNoOfSharesHeld(String noOfSharesHeld) { this.noOfSharesHeld = noOfSharesHeld; }

    public String getShareValue() { return shareValue; }
    public void setShareValue(String shareValue) { this.shareValue = shareValue; }

    public String getPercentageOfShare() { return percentageOfShare; }
    public void setPercentageOfShare(String percentageOfShare) { this.percentageOfShare = percentageOfShare; }
}
