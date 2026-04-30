package com.posgateway.aml.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Request DTO for beneficial owner
 */
public class BeneficialOwnerRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 500)
    private String fullName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Nationality is required")
    @Size(min = 3, max = 3, message = "Nationality must be 3-letter ISO code")
    private String nationality;

    @Size(min = 3, max = 3)
    private String countryOfResidence;

    @Size(max = 100)
    private String passportNumber;

    @Size(max = 100)
    private String nationalId;

    @NotNull(message = "Ownership percentage is required")
    @Min(value = 0, message = "Ownership percentage must be between 0 and 100")
    @Max(value = 100, message = "Ownership percentage must be between 0 and 100")
    private Integer ownershipPercentage;

    public BeneficialOwnerRequest() {
    }

    public BeneficialOwnerRequest(String fullName, LocalDate dateOfBirth, String nationality, String countryOfResidence,
            String passportNumber, String nationalId, Integer ownershipPercentage) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.countryOfResidence = countryOfResidence;
        this.passportNumber = passportNumber;
        this.nationalId = nationalId;
        this.ownershipPercentage = ownershipPercentage;
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

    public static BeneficialOwnerRequestBuilder builder() {
        return new BeneficialOwnerRequestBuilder();
    }

    public static class BeneficialOwnerRequestBuilder {
        private String fullName;
        private LocalDate dateOfBirth;
        private String nationality;
        private String countryOfResidence;
        private String passportNumber;
        private String nationalId;
        private Integer ownershipPercentage;

        BeneficialOwnerRequestBuilder() {
        }

        public BeneficialOwnerRequestBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public BeneficialOwnerRequestBuilder dateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public BeneficialOwnerRequestBuilder nationality(String nationality) {
            this.nationality = nationality;
            return this;
        }

        public BeneficialOwnerRequestBuilder countryOfResidence(String countryOfResidence) {
            this.countryOfResidence = countryOfResidence;
            return this;
        }

        public BeneficialOwnerRequestBuilder passportNumber(String passportNumber) {
            this.passportNumber = passportNumber;
            return this;
        }

        public BeneficialOwnerRequestBuilder nationalId(String nationalId) {
            this.nationalId = nationalId;
            return this;
        }

        public BeneficialOwnerRequestBuilder ownershipPercentage(Integer ownershipPercentage) {
            this.ownershipPercentage = ownershipPercentage;
            return this;
        }

        public BeneficialOwnerRequest build() {
            return new BeneficialOwnerRequest(fullName, dateOfBirth, nationality, countryOfResidence, passportNumber,
                    nationalId, ownershipPercentage);
        }

        public String toString() {
            return "BeneficialOwnerRequest.BeneficialOwnerRequestBuilder(fullName=" + this.fullName + ", dateOfBirth="
                    + this.dateOfBirth + ", nationality=" + this.nationality + ", countryOfResidence="
                    + this.countryOfResidence + ", passportNumber=" + this.passportNumber + ", nationalId="
                    + this.nationalId + ", ownershipPercentage=" + this.ownershipPercentage + ")";
        }
    }
}
