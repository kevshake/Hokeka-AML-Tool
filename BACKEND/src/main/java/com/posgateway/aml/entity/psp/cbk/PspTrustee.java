package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #3 – Schedule of Trustees (annual, Jan 5).
 * Maps to table psp_trustees.
 */
@Entity
@Table(name = "psp_trustees", indexes = {
        @Index(name = "idx_psp_trustees_psp_id", columnList = "psp_id")
})
public class PspTrustee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "trust_comp_name", length = 512)
    private String trustCompName;

    @Column(name = "directors_trust_comp", columnDefinition = "TEXT")
    private String directorsTrustComp;

    @Column(name = "trustee_names", length = 512)
    private String trusteeNames;

    @Column(name = "trustee_gender", length = 16)
    private String trusteeGender;

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

    @Column(name = "others_trusteeships", columnDefinition = "TEXT")
    private String othersTrusteeships;

    @Column(name = "disclosures", columnDefinition = "TEXT")
    private String disclosures;

    @Column(name = "shareholders", columnDefinition = "TEXT")
    private String shareholders;

    @Column(name = "shareholding_percentage", precision = 7, scale = 4)
    private BigDecimal shareholdingPercentage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspTrustee() {}

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

    public static PspTrusteeBuilder builder() { return new PspTrusteeBuilder(); }

    public static class PspTrusteeBuilder {
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

        PspTrusteeBuilder() {}

        public PspTrusteeBuilder id(Long id) { this.id = id; return this; }
        public PspTrusteeBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspTrusteeBuilder trustCompName(String trustCompName) { this.trustCompName = trustCompName; return this; }
        public PspTrusteeBuilder directorsTrustComp(String directorsTrustComp) { this.directorsTrustComp = directorsTrustComp; return this; }
        public PspTrusteeBuilder trusteeNames(String trusteeNames) { this.trusteeNames = trusteeNames; return this; }
        public PspTrusteeBuilder trusteeGender(String trusteeGender) { this.trusteeGender = trusteeGender; return this; }
        public PspTrusteeBuilder dob(LocalDate dob) { this.dob = dob; return this; }
        public PspTrusteeBuilder nationality(String nationality) { this.nationality = nationality; return this; }
        public PspTrusteeBuilder residentCountry(String residentCountry) { this.residentCountry = residentCountry; return this; }
        public PspTrusteeBuilder idNoPassport(String idNoPassport) { this.idNoPassport = idNoPassport; return this; }
        public PspTrusteeBuilder pin(String pin) { this.pin = pin; return this; }
        public PspTrusteeBuilder contactNumber(String contactNumber) { this.contactNumber = contactNumber; return this; }
        public PspTrusteeBuilder qualifications(String qualifications) { this.qualifications = qualifications; return this; }
        public PspTrusteeBuilder othersTrusteeships(String othersTrusteeships) { this.othersTrusteeships = othersTrusteeships; return this; }
        public PspTrusteeBuilder disclosures(String disclosures) { this.disclosures = disclosures; return this; }
        public PspTrusteeBuilder shareholders(String shareholders) { this.shareholders = shareholders; return this; }
        public PspTrusteeBuilder shareholdingPercentage(BigDecimal shareholdingPercentage) { this.shareholdingPercentage = shareholdingPercentage; return this; }
        public PspTrusteeBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspTrusteeBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspTrustee build() {
            PspTrustee e = new PspTrustee();
            e.id = this.id;
            e.pspId = this.pspId;
            e.trustCompName = this.trustCompName;
            e.directorsTrustComp = this.directorsTrustComp;
            e.trusteeNames = this.trusteeNames;
            e.trusteeGender = this.trusteeGender;
            e.dob = this.dob;
            e.nationality = this.nationality;
            e.residentCountry = this.residentCountry;
            e.idNoPassport = this.idNoPassport;
            e.pin = this.pin;
            e.contactNumber = this.contactNumber;
            e.qualifications = this.qualifications;
            e.othersTrusteeships = this.othersTrusteeships;
            e.disclosures = this.disclosures;
            e.shareholders = this.shareholders;
            e.shareholdingPercentage = this.shareholdingPercentage;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
