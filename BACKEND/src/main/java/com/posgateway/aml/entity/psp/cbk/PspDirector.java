package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #2 – Schedule of Directors (annual, Jan 5).
 * Maps to table psp_directors.
 */
@Entity
@Table(name = "psp_directors", indexes = {
        @Index(name = "idx_psp_directors_psp_id", columnList = "psp_id")
})
public class PspDirector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "director_names", length = 512)
    private String directorNames;

    @Column(name = "director_gender", length = 16)
    private String directorGender;

    @Column(name = "type_of_director", length = 64)
    private String typeOfDirector;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Column(name = "resident_country", length = 64)
    private String residentCountry;

    @Column(name = "id_no_passport", length = 128)
    private String idNoPassport;

    @Column(name = "pin", length = 64)
    private String pin;

    @Column(name = "contact_number", length = 64)
    private String contactNumber;

    @Column(name = "qualifications", columnDefinition = "TEXT")
    private String qualifications;

    @Column(name = "other_directorships", columnDefinition = "TEXT")
    private String otherDirectorships;

    @Column(name = "date_of_appointment")
    private LocalDate dateOfAppointment;

    @Column(name = "date_of_retirement")
    private LocalDate dateOfRetirement;

    @Column(name = "retirement_reason", columnDefinition = "TEXT")
    private String retirementReason;

    @Column(name = "disclosures", columnDefinition = "TEXT")
    private String disclosures;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspDirector() {
    }

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

    public static PspDirectorBuilder builder() { return new PspDirectorBuilder(); }

    public static class PspDirectorBuilder {
        private Long id;
        private Long pspId;
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        PspDirectorBuilder() {}

        public PspDirectorBuilder id(Long id) { this.id = id; return this; }
        public PspDirectorBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspDirectorBuilder directorNames(String directorNames) { this.directorNames = directorNames; return this; }
        public PspDirectorBuilder directorGender(String directorGender) { this.directorGender = directorGender; return this; }
        public PspDirectorBuilder typeOfDirector(String typeOfDirector) { this.typeOfDirector = typeOfDirector; return this; }
        public PspDirectorBuilder dob(LocalDate dob) { this.dob = dob; return this; }
        public PspDirectorBuilder nationality(String nationality) { this.nationality = nationality; return this; }
        public PspDirectorBuilder residentCountry(String residentCountry) { this.residentCountry = residentCountry; return this; }
        public PspDirectorBuilder idNoPassport(String idNoPassport) { this.idNoPassport = idNoPassport; return this; }
        public PspDirectorBuilder pin(String pin) { this.pin = pin; return this; }
        public PspDirectorBuilder contactNumber(String contactNumber) { this.contactNumber = contactNumber; return this; }
        public PspDirectorBuilder qualifications(String qualifications) { this.qualifications = qualifications; return this; }
        public PspDirectorBuilder otherDirectorships(String otherDirectorships) { this.otherDirectorships = otherDirectorships; return this; }
        public PspDirectorBuilder dateOfAppointment(LocalDate dateOfAppointment) { this.dateOfAppointment = dateOfAppointment; return this; }
        public PspDirectorBuilder dateOfRetirement(LocalDate dateOfRetirement) { this.dateOfRetirement = dateOfRetirement; return this; }
        public PspDirectorBuilder retirementReason(String retirementReason) { this.retirementReason = retirementReason; return this; }
        public PspDirectorBuilder disclosures(String disclosures) { this.disclosures = disclosures; return this; }
        public PspDirectorBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspDirectorBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspDirector build() {
            PspDirector e = new PspDirector();
            e.id = this.id;
            e.pspId = this.pspId;
            e.directorNames = this.directorNames;
            e.directorGender = this.directorGender;
            e.typeOfDirector = this.typeOfDirector;
            e.dob = this.dob;
            e.nationality = this.nationality;
            e.residentCountry = this.residentCountry;
            e.idNoPassport = this.idNoPassport;
            e.pin = this.pin;
            e.contactNumber = this.contactNumber;
            e.qualifications = this.qualifications;
            e.otherDirectorships = this.otherDirectorships;
            e.dateOfAppointment = this.dateOfAppointment;
            e.dateOfRetirement = this.dateOfRetirement;
            e.retirementReason = this.retirementReason;
            e.disclosures = this.disclosures;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
