package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Beneficial Owner (UBO) entity
 * PII fields will be encrypted at application layer
 */
@Entity
@Table(name = "beneficial_owners")
public class BeneficialOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_id")
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    // Identity (PII - should be encrypted)
    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "nationality", nullable = false, length = 3)
    private String nationality;

    @Column(name = "country_of_residence", length = 3)
    private String countryOfResidence;

    // Identification (encrypted)
    @Column(name = "passport_number", length = 100)
    private String passportNumber;

    @Column(name = "national_id", length = 100)
    private String nationalId;

    // Ownership
    @Column(name = "ownership_percentage", nullable = false)
    private Integer ownershipPercentage;

    // Screening Flags
    @Column(name = "is_pep")
    private Boolean isPep = false;

    @Column(name = "is_sanctioned")
    private Boolean isSanctioned = false;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "last_screened_at")
    private LocalDateTime lastScreenedAt;

    public BeneficialOwner() {
    }

    public BeneficialOwner(Long ownerId, Merchant merchant, String fullName, LocalDate dateOfBirth, String nationality,
            String countryOfResidence, String passportNumber, String nationalId, Integer ownershipPercentage,
            Boolean isPep, Boolean isSanctioned, LocalDateTime createdAt, LocalDateTime updatedAt,
            LocalDateTime lastScreenedAt) {
        this.ownerId = ownerId;
        this.merchant = merchant;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.countryOfResidence = countryOfResidence;
        this.passportNumber = passportNumber;
        this.nationalId = nationalId;
        this.ownershipPercentage = ownershipPercentage;
        this.isPep = isPep;
        this.isSanctioned = isSanctioned;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastScreenedAt = lastScreenedAt;
    }

    public static BeneficialOwnerBuilder builder() {
        return new BeneficialOwnerBuilder();
    }

    public static class BeneficialOwnerBuilder {
        private Long ownerId;
        private Merchant merchant;
        private String fullName;
        private LocalDate dateOfBirth;
        private String nationality;
        private String countryOfResidence;
        private String passportNumber;
        private String nationalId;
        private Integer ownershipPercentage;
        private Boolean isPep = false;
        private Boolean isSanctioned = false;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private LocalDateTime lastScreenedAt;

        BeneficialOwnerBuilder() {
        }

        public BeneficialOwnerBuilder ownerId(Long ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public BeneficialOwnerBuilder merchant(Merchant merchant) {
            this.merchant = merchant;
            return this;
        }

        public BeneficialOwnerBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public BeneficialOwnerBuilder dateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public BeneficialOwnerBuilder nationality(String nationality) {
            this.nationality = nationality;
            return this;
        }

        public BeneficialOwnerBuilder countryOfResidence(String countryOfResidence) {
            this.countryOfResidence = countryOfResidence;
            return this;
        }

        public BeneficialOwnerBuilder passportNumber(String passportNumber) {
            this.passportNumber = passportNumber;
            return this;
        }

        public BeneficialOwnerBuilder nationalId(String nationalId) {
            this.nationalId = nationalId;
            return this;
        }

        public BeneficialOwnerBuilder ownershipPercentage(Integer ownershipPercentage) {
            this.ownershipPercentage = ownershipPercentage;
            return this;
        }

        public BeneficialOwnerBuilder isPep(Boolean isPep) {
            this.isPep = isPep;
            return this;
        }

        public BeneficialOwnerBuilder isSanctioned(Boolean isSanctioned) {
            this.isSanctioned = isSanctioned;
            return this;
        }

        public BeneficialOwnerBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public BeneficialOwnerBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public BeneficialOwnerBuilder lastScreenedAt(LocalDateTime lastScreenedAt) {
            this.lastScreenedAt = lastScreenedAt;
            return this;
        }

        public BeneficialOwner build() {
            return new BeneficialOwner(ownerId, merchant, fullName, dateOfBirth, nationality, countryOfResidence,
                    passportNumber, nationalId, ownershipPercentage, isPep, isSanctioned, createdAt, updatedAt,
                    lastScreenedAt);
        }
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getCountryOfResidence() {
        return countryOfResidence;
    }

    public void setCountryOfResidence(String countryOfResidence) {
        this.countryOfResidence = countryOfResidence;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public Integer getOwnershipPercentage() {
        return ownershipPercentage;
    }

    public void setOwnershipPercentage(Integer ownershipPercentage) {
        this.ownershipPercentage = ownershipPercentage;
    }

    public Boolean getIsPep() {
        return isPep;
    }

    public void setIsPep(Boolean isPep) {
        this.isPep = isPep;
    }

    public Boolean getIsSanctioned() {
        return isSanctioned;
    }

    public void setIsSanctioned(Boolean isSanctioned) {
        this.isSanctioned = isSanctioned;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastScreenedAt() {
        return lastScreenedAt;
    }

    public void setLastScreenedAt(LocalDateTime lastScreenedAt) {
        this.lastScreenedAt = lastScreenedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
