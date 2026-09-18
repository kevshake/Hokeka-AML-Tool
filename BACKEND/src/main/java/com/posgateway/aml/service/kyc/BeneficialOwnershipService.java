package com.posgateway.aml.service.kyc;

import com.posgateway.aml.dto.request.BeneficialOwnerRequest;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import com.posgateway.aml.service.security.PiiLookupHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class BeneficialOwnershipService {

    private final BeneficialOwnerRepository ownerRepository;
    private final MerchantRepository merchantRepository;
    private final AmlScreeningOrchestrator screeningOrchestrator;
    private final PiiLookupHasher piiLookupHasher;

    public BeneficialOwnershipService(BeneficialOwnerRepository ownerRepository,
                                      MerchantRepository merchantRepository,
                                      AmlScreeningOrchestrator screeningOrchestrator,
                                      PiiLookupHasher piiLookupHasher) {
        this.ownerRepository = ownerRepository;
        this.merchantRepository = merchantRepository;
        this.screeningOrchestrator = screeningOrchestrator;
        this.piiLookupHasher = piiLookupHasher;
    }

    @Transactional(readOnly = true)
    public OwnershipStructure getOwnershipStructure(Long merchantId) {
        requireMerchant(merchantId);
        List<BeneficialOwner> owners = ownerRepository.findByMerchant_MerchantId(merchantId);
        int total = owners.stream().map(BeneficialOwner::getOwnershipPercentage)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        List<BeneficialOwner> ubos = owners.stream()
                .filter(owner -> owner.getOwnershipPercentage() != null && owner.getOwnershipPercentage() >= 25)
                .toList();
        return new OwnershipStructure(merchantId, owners, ubos, total, total == 100);
    }

    @Transactional
    public BeneficialOwner create(Long merchantId, BeneficialOwnerRequest request) {
        Merchant merchant = requireMerchant(merchantId);
        validateOwnershipTotal(merchantId, null, request.getOwnershipPercentage());
        BeneficialOwner owner = new BeneficialOwner();
        apply(owner, request, false);
        owner.setMerchant(merchant);
        return ownerRepository.save(owner);
    }

    @Transactional
    public BeneficialOwner update(Long merchantId, Long ownerId, BeneficialOwnerRequest request) {
        BeneficialOwner owner = requireOwner(merchantId, ownerId);
        validateOwnershipTotal(merchantId, ownerId, request.getOwnershipPercentage());
        apply(owner, request, true);
        return ownerRepository.save(owner);
    }

    @Transactional
    public void delete(Long merchantId, Long ownerId) {
        BeneficialOwner owner = requireOwner(merchantId, ownerId);
        ownerRepository.delete(owner);
    }

    @Transactional
    public ScreeningResult screen(Long merchantId, Long ownerId) {
        BeneficialOwner owner = requireOwner(merchantId, ownerId);
        ScreeningResult result = screeningOrchestrator.screenBeneficialOwner(owner, owner.getMerchant());
        ownerRepository.save(owner);
        return result;
    }

    @Transactional(readOnly = true)
    public BeneficialOwner requireOwner(Long merchantId, Long ownerId) {
        BeneficialOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficial owner not found: " + ownerId));
        if (owner.getMerchant() == null || !merchantId.equals(owner.getMerchant().getMerchantId())) {
            throw new IllegalArgumentException("Beneficial owner does not belong to merchant: " + merchantId);
        }
        return owner;
    }

    private Merchant requireMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }

    private void validateOwnershipTotal(Long merchantId, Long excludedOwnerId, Integer requestedPercentage) {
        int existing = ownerRepository.findByMerchant_MerchantId(merchantId).stream()
                .filter(owner -> excludedOwnerId == null || !excludedOwnerId.equals(owner.getOwnerId()))
                .map(BeneficialOwner::getOwnershipPercentage).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (requestedPercentage == null || requestedPercentage < 0 || requestedPercentage > 100
                || existing + requestedPercentage > 100) {
            throw new IllegalArgumentException("Beneficial ownership percentages cannot exceed 100");
        }
    }

    private void apply(BeneficialOwner owner, BeneficialOwnerRequest request, boolean preserveMissingIdentifiers) {
        owner.setFullName(request.getFullName().strip());
        owner.setDateOfBirth(request.getDateOfBirth());
        owner.setNationality(request.getNationality().strip().toUpperCase(Locale.ROOT));
        owner.setCountryOfResidence(request.getCountryOfResidence() == null ? null
                : request.getCountryOfResidence().strip().toUpperCase(Locale.ROOT));
        String passport = blankToNull(request.getPassportNumber());
        String nationalId = blankToNull(request.getNationalId());
        if (!preserveMissingIdentifiers || passport != null) {
            owner.setPassportNumber(passport);
            owner.setPassportHash(piiLookupHasher.hashIdentifier(passport));
        }
        if (!preserveMissingIdentifiers || nationalId != null) {
            owner.setNationalId(nationalId);
            owner.setNationalIdHash(piiLookupHasher.hashIdentifier(nationalId));
        }
        owner.setOwnershipPercentage(request.getOwnershipPercentage());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record OwnershipStructure(Long merchantId, List<BeneficialOwner> beneficialOwners,
                                     List<BeneficialOwner> ultimateBeneficialOwners,
                                     int totalOwnershipPercentage, boolean complete) {}
}
