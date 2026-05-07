package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #4 – Schedule of Shareholders (annual, Jan 4).
 * Maps to table psp_shareholders.
 */
@Entity
@Table(name = "psp_shareholders", indexes = {
        @Index(name = "idx_psp_shareholders_psp_id", columnList = "psp_id")
})
public class PspShareholder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "shareholder_name", length = 512)
    private String shareholderName;

    @Column(name = "shareholder_gender", length = 16)
    private String shareholderGender;

    @Column(name = "shareholder_type", length = 64)
    private String shareholderType;

    @Column(name = "dob_or_reg_date")
    private LocalDate dobOrRegDate;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Column(name = "resident_country", length = 64)
    private String residentCountry;

    @Column(name = "country_of_inc", length = 64)
    private String countryOfInc;

    @Column(name = "id_no_passport", length = 128)
    private String idNoPassport;

    @Column(name = "pin", length = 64)
    private String pin;

    @Column(name = "contact_number", length = 64)
    private String contactNumber;

    @Column(name = "qualifications", columnDefinition = "TEXT")
    private String qualifications;

    @Column(name = "previous_employment", columnDefinition = "TEXT")
    private String previousEmployment;

    @Column(name = "onboarding_date")
    private LocalDate onboardingDate;

    @Column(name = "no_of_shares_held")
    private Long noOfSharesHeld;

    @Column(name = "share_value", precision = 18, scale = 4)
    private BigDecimal shareValue;

    @Column(name = "percentage_of_share", precision = 7, scale = 4)
    private BigDecimal percentageOfShare;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspShareholder() {}

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

    public static PspShareholderBuilder builder() { return new PspShareholderBuilder(); }

    public static class PspShareholderBuilder {
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

        PspShareholderBuilder() {}

        public PspShareholderBuilder id(Long id) { this.id = id; return this; }
        public PspShareholderBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspShareholderBuilder shareholderName(String shareholderName) { this.shareholderName = shareholderName; return this; }
        public PspShareholderBuilder shareholderGender(String shareholderGender) { this.shareholderGender = shareholderGender; return this; }
        public PspShareholderBuilder shareholderType(String shareholderType) { this.shareholderType = shareholderType; return this; }
        public PspShareholderBuilder dobOrRegDate(LocalDate dobOrRegDate) { this.dobOrRegDate = dobOrRegDate; return this; }
        public PspShareholderBuilder nationality(String nationality) { this.nationality = nationality; return this; }
        public PspShareholderBuilder residentCountry(String residentCountry) { this.residentCountry = residentCountry; return this; }
        public PspShareholderBuilder countryOfInc(String countryOfInc) { this.countryOfInc = countryOfInc; return this; }
        public PspShareholderBuilder idNoPassport(String idNoPassport) { this.idNoPassport = idNoPassport; return this; }
        public PspShareholderBuilder pin(String pin) { this.pin = pin; return this; }
        public PspShareholderBuilder contactNumber(String contactNumber) { this.contactNumber = contactNumber; return this; }
        public PspShareholderBuilder qualifications(String qualifications) { this.qualifications = qualifications; return this; }
        public PspShareholderBuilder previousEmployment(String previousEmployment) { this.previousEmployment = previousEmployment; return this; }
        public PspShareholderBuilder onboardingDate(LocalDate onboardingDate) { this.onboardingDate = onboardingDate; return this; }
        public PspShareholderBuilder noOfSharesHeld(Long noOfSharesHeld) { this.noOfSharesHeld = noOfSharesHeld; return this; }
        public PspShareholderBuilder shareValue(BigDecimal shareValue) { this.shareValue = shareValue; return this; }
        public PspShareholderBuilder percentageOfShare(BigDecimal percentageOfShare) { this.percentageOfShare = percentageOfShare; return this; }
        public PspShareholderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspShareholderBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspShareholder build() {
            PspShareholder e = new PspShareholder();
            e.id = this.id;
            e.pspId = this.pspId;
            e.shareholderName = this.shareholderName;
            e.shareholderGender = this.shareholderGender;
            e.shareholderType = this.shareholderType;
            e.dobOrRegDate = this.dobOrRegDate;
            e.nationality = this.nationality;
            e.residentCountry = this.residentCountry;
            e.countryOfInc = this.countryOfInc;
            e.idNoPassport = this.idNoPassport;
            e.pin = this.pin;
            e.contactNumber = this.contactNumber;
            e.qualifications = this.qualifications;
            e.previousEmployment = this.previousEmployment;
            e.onboardingDate = this.onboardingDate;
            e.noOfSharesHeld = this.noOfSharesHeld;
            e.shareValue = this.shareValue;
            e.percentageOfShare = this.percentageOfShare;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
