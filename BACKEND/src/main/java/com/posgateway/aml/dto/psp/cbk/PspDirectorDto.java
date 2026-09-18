package com.posgateway.aml.dto.psp.cbk;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for psp_directors.  The request form omits pspId (derived from path).
 */
public class PspDirectorDto {

    // ---- Request (create / update) fields ----
    private String directorNames;
    private String directorGender;
    private String typeOfDirector;
    private LocalDate dob;
    private String nationality;
    private String residentCountry;
    private String idNoPassport;
    private String pin;
    private String contactNumber;
    private String qualifications;
    private String otherDirectorships;
    private LocalDate dateOfAppointment;
    private LocalDate dateOfRetirement;
    private String retirementReason;
    private String disclosures;

    // ---- Response-only fields ----
    private Long id;
    private Long pspId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspDirectorDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getDirectorNames() { return directorNames; }
    public void setDirectorNames(String directorNames) { this.directorNames = directorNames; }

    public String getDirectorGender() { return directorGender; }
    public void setDirectorGender(String directorGender) { this.directorGender = directorGender; }

    public String getTypeOfDirector() { return typeOfDirector; }
    public void setTypeOfDirector(String typeOfDirector) { this.typeOfDirector = typeOfDirector; }

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

    public String getOtherDirectorships() { return otherDirectorships; }
    public void setOtherDirectorships(String otherDirectorships) { this.otherDirectorships = otherDirectorships; }

    public LocalDate getDateOfAppointment() { return dateOfAppointment; }
    public void setDateOfAppointment(LocalDate dateOfAppointment) { this.dateOfAppointment = dateOfAppointment; }

    public LocalDate getDateOfRetirement() { return dateOfRetirement; }
    public void setDateOfRetirement(LocalDate dateOfRetirement) { this.dateOfRetirement = dateOfRetirement; }

    public String getRetirementReason() { return retirementReason; }
    public void setRetirementReason(String retirementReason) { this.retirementReason = retirementReason; }

    public String getDisclosures() { return disclosures; }
    public void setDisclosures(String disclosures) { this.disclosures = disclosures; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
