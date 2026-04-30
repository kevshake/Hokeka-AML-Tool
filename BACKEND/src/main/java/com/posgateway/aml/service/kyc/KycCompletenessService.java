package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * KYC Completeness Scoring Service
 * Calculates and tracks KYC completeness percentage for merchants
 */
@Service
public class KycCompletenessService {

    private static final Logger logger = LoggerFactory.getLogger(KycCompletenessService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantDocumentRepository documentRepository;
    private final com.posgateway.aml.service.cache.KycDataCacheService kycCacheService; // Aerospike cache

    // Required document types for KYC completeness
    private static final String[] REQUIRED_DOCUMENT_TYPES = {
        "REGISTRATION_CERTIFICATE",
        "TAX_ID",
        "BANK_STATEMENT",
        "ADDRESS_PROOF"
    };

    @Autowired
    public KycCompletenessService(
            MerchantRepository merchantRepository,
            MerchantDocumentRepository documentRepository,
            com.posgateway.aml.service.cache.KycDataCacheService kycCacheService) {
        this.merchantRepository = merchantRepository;
        this.documentRepository = documentRepository;
        this.kycCacheService = kycCacheService;
    }

    /**
     * Calculate KYC completeness score for a merchant
     * Uses Aerospike cache for fast lookups
     */
    public CompletenessScore calculateCompletenessScore(Long merchantId) {
        // Fast Aerospike cache lookup first for overall score
        Double cachedScore = kycCacheService.getCachedCompletenessScore(merchantId);
        if (cachedScore != null) {
            logger.debug("KYC completeness from cache for merchant {}: {}%", merchantId, cachedScore * 100);
            // Return a simplified score object (would need full reconstruction in production)
            // For now, continue with full calculation to get complete details
        }
        
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        List<MerchantDocument> documents = documentRepository.findByMerchantId(merchantId);
        List<BeneficialOwner> beneficialOwners = merchant.getBeneficialOwners();

        CompletenessScore score = new CompletenessScore();
        score.setMerchantId(merchantId);
        score.setMerchantName(merchant.getLegalName());

        // Calculate document completeness (40% weight)
        double documentScore = calculateDocumentCompleteness(documents);
        score.setDocumentScore(documentScore);
        score.setDocumentPercentage(documentScore * 100);

        // Calculate beneficial owner completeness (30% weight)
        double ownerScore = calculateOwnerCompleteness(beneficialOwners);
        score.setOwnerScore(ownerScore);
        score.setOwnerPercentage(ownerScore * 100);

        // Calculate basic information completeness (30% weight)
        double basicInfoScore = calculateBasicInfoCompleteness(merchant);
        score.setBasicInfoScore(basicInfoScore);
        score.setBasicInfoPercentage(basicInfoScore * 100);

        // Calculate overall score (weighted average)
        double overallScore = (documentScore * 0.4) + (ownerScore * 0.3) + (basicInfoScore * 0.3);
        score.setOverallScore(overallScore);
        score.setOverallPercentage(overallScore * 100);

        // Determine completeness level
        if (overallScore >= 0.9) {
            score.setCompletenessLevel("COMPLETE");
        } else if (overallScore >= 0.7) {
            score.setCompletenessLevel("MOSTLY_COMPLETE");
        } else if (overallScore >= 0.5) {
            score.setCompletenessLevel("PARTIAL");
        } else {
            score.setCompletenessLevel("INCOMPLETE");
        }

        // Identify missing items
        score.setMissingItems(identifyMissingItems(merchant, documents, beneficialOwners));

        logger.debug("KYC completeness for merchant {}: {}% ({})",
                merchantId, score.getOverallPercentage(), score.getCompletenessLevel());

        // Cache the overall score in Aerospike for future fast lookups
        kycCacheService.cacheCompletenessScore(merchantId, score.getOverallScore());

        return score;
    }

    /**
     * Calculate document completeness
     */
    private double calculateDocumentCompleteness(List<MerchantDocument> documents) {
        if (documents.isEmpty()) {
            return 0.0;
        }

        long verifiedCount = documents.stream()
                .filter(doc -> "VERIFIED".equals(doc.getStatus()))
                .count();

        // Check for required document types
        long requiredDocsCount = 0;
        for (String requiredType : REQUIRED_DOCUMENT_TYPES) {
            boolean hasRequired = documents.stream()
                    .anyMatch(doc -> requiredType.equals(doc.getDocumentType()) && 
                            "VERIFIED".equals(doc.getStatus()));
            if (hasRequired) {
                requiredDocsCount++;
            }
        }

        // Score: 50% for having any verified docs, 50% for having required docs
        double verifiedRatio = verifiedCount > 0 ? Math.min(1.0, verifiedCount / (double) documents.size()) : 0.0;
        double requiredRatio = requiredDocsCount / (double) REQUIRED_DOCUMENT_TYPES.length;

        return (verifiedRatio * 0.5) + (requiredRatio * 0.5);
    }

    /**
     * Calculate beneficial owner completeness
     */
    private double calculateOwnerCompleteness(List<BeneficialOwner> owners) {
        if (owners == null || owners.isEmpty()) {
            return 0.0;
        }

        long completeOwners = owners.stream()
                .filter(owner -> owner.getFullName() != null && 
                        !owner.getFullName().isEmpty() &&
                        owner.getDateOfBirth() != null &&
                        owner.getNationality() != null)
                .count();

        return owners.size() > 0 ? completeOwners / (double) owners.size() : 0.0;
    }

    /**
     * Calculate basic information completeness
     */
    private double calculateBasicInfoCompleteness(Merchant merchant) {
        int totalFields = 8;
        int completedFields = 0;

        if (merchant.getLegalName() != null && !merchant.getLegalName().isEmpty()) completedFields++;
        if (merchant.getRegistrationNumber() != null && !merchant.getRegistrationNumber().isEmpty()) completedFields++;
        if (merchant.getCountry() != null && !merchant.getCountry().isEmpty()) completedFields++;
        if (merchant.getMcc() != null && !merchant.getMcc().isEmpty()) completedFields++;
        if (merchant.getAddressStreet() != null && !merchant.getAddressStreet().isEmpty()) completedFields++;
        if (merchant.getAddressCity() != null && !merchant.getAddressCity().isEmpty()) completedFields++;
        if (merchant.getContactEmail() != null && !merchant.getContactEmail().isEmpty()) completedFields++;
        if (merchant.getRegistrationDate() != null) completedFields++;

        return completedFields / (double) totalFields;
    }

    /**
     * Identify missing items
     */
    private List<String> identifyMissingItems(Merchant merchant, List<MerchantDocument> documents, 
                                             List<BeneficialOwner> owners) {
        List<String> missing = new java.util.ArrayList<>();

        // Check missing documents
        for (String requiredType : REQUIRED_DOCUMENT_TYPES) {
            boolean hasRequired = documents.stream()
                    .anyMatch(doc -> requiredType.equals(doc.getDocumentType()) && 
                            "VERIFIED".equals(doc.getStatus()));
            if (!hasRequired) {
                missing.add("Missing verified document: " + requiredType);
            }
        }

        // Check basic information
        if (merchant.getLegalName() == null || merchant.getLegalName().isEmpty()) {
            missing.add("Missing legal name");
        }
        if (merchant.getRegistrationNumber() == null || merchant.getRegistrationNumber().isEmpty()) {
            missing.add("Missing registration number");
        }
        if (merchant.getAddressStreet() == null || merchant.getAddressStreet().isEmpty()) {
            missing.add("Missing address");
        }

        // Check beneficial owners
        if (owners == null || owners.isEmpty()) {
            missing.add("Missing beneficial owners");
        } else {
            for (int i = 0; i < owners.size(); i++) {
                BeneficialOwner owner = owners.get(i);
                if (owner.getFullName() == null || owner.getFullName().isEmpty()) {
                    missing.add("Owner " + (i + 1) + ": Missing name");
                }
                if (owner.getDateOfBirth() == null) {
                    missing.add("Owner " + (i + 1) + ": Missing date of birth");
                }
            }
        }

        return missing;
    }

    /**
     * Completeness Score DTO
     */
    public static class CompletenessScore {
        private Long merchantId;
        private String merchantName;
        private double documentScore;
        private double documentPercentage;
        private double ownerScore;
        private double ownerPercentage;
        private double basicInfoScore;
        private double basicInfoPercentage;
        private double overallScore;
        private double overallPercentage;
        private String completenessLevel;
        private List<String> missingItems;

        // Getters and Setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public double getDocumentScore() { return documentScore; }
        public void setDocumentScore(double documentScore) { this.documentScore = documentScore; }
        public double getDocumentPercentage() { return documentPercentage; }
        public void setDocumentPercentage(double documentPercentage) { this.documentPercentage = documentPercentage; }
        public double getOwnerScore() { return ownerScore; }
        public void setOwnerScore(double ownerScore) { this.ownerScore = ownerScore; }
        public double getOwnerPercentage() { return ownerPercentage; }
        public void setOwnerPercentage(double ownerPercentage) { this.ownerPercentage = ownerPercentage; }
        public double getBasicInfoScore() { return basicInfoScore; }
        public void setBasicInfoScore(double basicInfoScore) { this.basicInfoScore = basicInfoScore; }
        public double getBasicInfoPercentage() { return basicInfoPercentage; }
        public void setBasicInfoPercentage(double basicInfoPercentage) { this.basicInfoPercentage = basicInfoPercentage; }
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        public double getOverallPercentage() { return overallPercentage; }
        public void setOverallPercentage(double overallPercentage) { this.overallPercentage = overallPercentage; }
        public String getCompletenessLevel() { return completenessLevel; }
        public void setCompletenessLevel(String completenessLevel) { this.completenessLevel = completenessLevel; }
        public List<String> getMissingItems() { return missingItems; }
        public void setMissingItems(List<String> missingItems) { this.missingItems = missingItems; }
    }
}

