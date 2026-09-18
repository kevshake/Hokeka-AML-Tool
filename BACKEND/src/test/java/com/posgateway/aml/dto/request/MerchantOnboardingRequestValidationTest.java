package com.posgateway.aml.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantOnboardingRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsOwnerWithoutIdentityDocument() {
        MerchantOnboardingRequest request = validRequest();
        request.getBeneficialOwners().get(0).setNationalId(null);

        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> violation.getMessage().contains("national ID or passport")));
    }

    @Test
    void rejectsOwnershipTotalAboveOneHundred() {
        MerchantOnboardingRequest request = validRequest();
        BeneficialOwnerRequest second = validOwner();
        second.setNationalId("ID-2");
        second.setOwnershipPercentage(60);
        request.setBeneficialOwners(List.of(request.getBeneficialOwners().get(0), second));

        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> violation.getMessage().contains("total no more than 100")));
    }

    private MerchantOnboardingRequest validRequest() {
        MerchantOnboardingRequest request = new MerchantOnboardingRequest();
        request.setPspId(7L);
        request.setLegalName("Evidence Merchant Ltd");
        request.setCountry("KEN");
        request.setRegistrationNumber("REG-7");
        request.setMcc("5411");
        request.setBeneficialOwners(List.of(validOwner()));
        return request;
    }

    private BeneficialOwnerRequest validOwner() {
        BeneficialOwnerRequest owner = new BeneficialOwnerRequest();
        owner.setFullName("Owner One");
        owner.setDateOfBirth(LocalDate.of(1980, 1, 1));
        owner.setNationality("KEN");
        owner.setNationalId("ID-1");
        owner.setOwnershipPercentage(60);
        return owner;
    }
}
