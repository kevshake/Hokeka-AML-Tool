package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Beneficial Ownership Service
 * Manages Ultimate Beneficial Owner (UBO) identification and verification
 */
@Service
public class BeneficialOwnershipService {

    private static final Logger logger = LoggerFactory.getLogger(BeneficialOwnershipService.class);

    private final BeneficialOwnerRepository beneficialOwnerRepository;

    @Autowired
    public BeneficialOwnershipService(BeneficialOwnerRepository beneficialOwnerRepository) {
        this.beneficialOwnerRepository = beneficialOwnerRepository;
    }

    /**
     * Identify Ultimate Beneficial Owners
     */
    public List<BeneficialOwner> identifyUbo(Long merchantId) {
        return beneficialOwnerRepository.findByMerchant_MerchantId(merchantId).stream()
                .filter(bo -> bo.getOwnershipPercentage() != null && bo.getOwnershipPercentage() >= 25)
                .toList();
    }

    /**
     * Update beneficial owner screening status
     */
    @Transactional
    public void updateScreeningStatus(Long beneficialOwnerId, boolean isPep, boolean isSanctioned) {
        BeneficialOwner owner = beneficialOwnerRepository.findById(beneficialOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficial owner not found"));

        owner.setIsPep(isPep);
        owner.setIsSanctioned(isSanctioned);
        owner.setLastScreenedAt(java.time.LocalDateTime.now());

        beneficialOwnerRepository.save(owner);
        logger.info("Beneficial owner {} screening updated - PEP: {}, Sanctioned: {}", 
                beneficialOwnerId, isPep, isSanctioned);
    }

    /**
     * Get ownership structure
     */
    public OwnershipStructure getOwnershipStructure(Long merchantId) {
        List<BeneficialOwner> owners = beneficialOwnerRepository.findByMerchant_MerchantId(merchantId);
        
        double totalOwnership = owners.stream()
                .filter(bo -> bo.getOwnershipPercentage() != null)
                .mapToDouble(bo -> bo.getOwnershipPercentage().doubleValue())
                .sum();

        return OwnershipStructure.builder()
                .merchantId(merchantId)
                .beneficialOwners(owners)
                .totalOwnershipPercentage(totalOwnership)
                .isComplete(totalOwnership >= 100.0)
                .build();
    }

    /**
     * Ownership Structure DTO
     */
    public static class OwnershipStructure {
        private Long merchantId;
        private List<BeneficialOwner> beneficialOwners;
        private double totalOwnershipPercentage;
        private boolean isComplete;

        public static OwnershipStructureBuilder builder() {
            return new OwnershipStructureBuilder();
        }

        // Getters and Setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public List<BeneficialOwner> getBeneficialOwners() { return beneficialOwners; }
        public void setBeneficialOwners(List<BeneficialOwner> beneficialOwners) { 
            this.beneficialOwners = beneficialOwners; 
        }
        public double getTotalOwnershipPercentage() { return totalOwnershipPercentage; }
        public void setTotalOwnershipPercentage(double totalOwnershipPercentage) { 
            this.totalOwnershipPercentage = totalOwnershipPercentage; 
        }
        public boolean isComplete() { return isComplete; }
        public void setComplete(boolean complete) { isComplete = complete; }

        public static class OwnershipStructureBuilder {
            private Long merchantId;
            private List<BeneficialOwner> beneficialOwners;
            private double totalOwnershipPercentage;
            private boolean isComplete;

            public OwnershipStructureBuilder merchantId(Long merchantId) {
                this.merchantId = merchantId;
                return this;
            }

            public OwnershipStructureBuilder beneficialOwners(List<BeneficialOwner> beneficialOwners) {
                this.beneficialOwners = beneficialOwners;
                return this;
            }

            public OwnershipStructureBuilder totalOwnershipPercentage(double totalOwnershipPercentage) {
                this.totalOwnershipPercentage = totalOwnershipPercentage;
                return this;
            }

            public OwnershipStructureBuilder isComplete(boolean isComplete) {
                this.isComplete = isComplete;
                return this;
            }

            public OwnershipStructure build() {
                OwnershipStructure structure = new OwnershipStructure();
                structure.merchantId = this.merchantId;
                structure.beneficialOwners = this.beneficialOwners;
                structure.totalOwnershipPercentage = this.totalOwnershipPercentage;
                structure.isComplete = this.isComplete;
                return structure;
            }
        }
    }
}

