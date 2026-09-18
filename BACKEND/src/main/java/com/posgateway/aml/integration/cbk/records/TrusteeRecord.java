package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #3 — Schedule of Trustees.
 * Wrapper key: {@code SCHED_OF_TRUSTEES}
 */
public final class TrusteeRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("TRUST_COMP_NAME")
    private String trustCompName;

    @JsonProperty("DIRECTORS_TRUST_COMP")
    private String directorsTrustComp;

    @JsonProperty("TRUSTEE_NAMES")
    private String trusteeNames;

    @JsonProperty("TRUSTEE_GENDER")
    private String trusteeGender;

    @JsonProperty("DOB")
    private String dob;

    @JsonProperty("NATIONALITY")
    private String nationality;

    @JsonProperty("RESIDENT_COUNTRY")
    private String residentCountry;

    @JsonProperty("ID_NO_PASSPORT")
    private String idNoPassport;

    @JsonProperty("PIN")
    private String pin;

    @JsonProperty("CONTACT_NUMBER")
    private String contactNumber;

    @JsonProperty("QUALIFICATIONS")
    private String qualifications;

    @JsonProperty("OTHERS_TRUSTEESHIPS")
    private String othersTrusteeships;

    @JsonProperty("DISCLOSURES")
    private String disclosures;

    @JsonProperty("SHAREHOLDERS")
    private String shareholders;

    @JsonProperty("SHAREHOLDING_PERCENTAGE")
    private String shareholdingPercentage;

    public TrusteeRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getTrustCompName() { return trustCompName; }
    public void setTrustCompName(String trustCompName) { this.trustCompName = trustCompName; }

    public String getDirectorsTrustComp() { return directorsTrustComp; }
    public void setDirectorsTrustComp(String directorsTrustComp) { this.directorsTrustComp = directorsTrustComp; }

    public String getTrusteeNames() { return trusteeNames; }
    public void setTrusteeNames(String trusteeNames) { this.trusteeNames = trusteeNames; }

    public String getTrusteeGender() { return trusteeGender; }
    public void setTrusteeGender(String trusteeGender) { this.trusteeGender = trusteeGender; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

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

    public String getShareholdingPercentage() { return shareholdingPercentage; }
    public void setShareholdingPercentage(String shareholdingPercentage) { this.shareholdingPercentage = shareholdingPercentage; }
}
