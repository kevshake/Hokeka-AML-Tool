package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #2 — Schedule of Directors.
 * Wrapper key: {@code SCHED_OF_DIR}
 */
public final class DirectorRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("DIRECTOR_NAMES")
    private String directorNames;

    @JsonProperty("DIRECTOR_GENDER")
    private String directorGender;

    @JsonProperty("TYPE_OF_DIRECTOR")
    private String typeOfDirector;

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

    @JsonProperty("OTHER_DIRECTORSHIPS")
    private String otherDirectorships;

    @JsonProperty("DATE_OF_APPOINTMENT")
    private String dateOfAppointment;

    @JsonProperty("DATE_OF_RETIREMENT")
    private String dateOfRetirement;

    @JsonProperty("RETIREMENT_REASON")
    private String retirementReason;

    @JsonProperty("DISCLOSURES")
    private String disclosures;

    public DirectorRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getDirectorNames() { return directorNames; }
    public void setDirectorNames(String directorNames) { this.directorNames = directorNames; }

    public String getDirectorGender() { return directorGender; }
    public void setDirectorGender(String directorGender) { this.directorGender = directorGender; }

    public String getTypeOfDirector() { return typeOfDirector; }
    public void setTypeOfDirector(String typeOfDirector) { this.typeOfDirector = typeOfDirector; }

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

    public String getOtherDirectorships() { return otherDirectorships; }
    public void setOtherDirectorships(String otherDirectorships) { this.otherDirectorships = otherDirectorships; }

    public String getDateOfAppointment() { return dateOfAppointment; }
    public void setDateOfAppointment(String dateOfAppointment) { this.dateOfAppointment = dateOfAppointment; }

    public String getDateOfRetirement() { return dateOfRetirement; }
    public void setDateOfRetirement(String dateOfRetirement) { this.dateOfRetirement = dateOfRetirement; }

    public String getRetirementReason() { return retirementReason; }
    public void setRetirementReason(String retirementReason) { this.retirementReason = retirementReason; }

    public String getDisclosures() { return disclosures; }
    public void setDisclosures(String disclosures) { this.disclosures = disclosures; }
}
