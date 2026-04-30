package com.posgateway.aml.service.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.ExternalAmlResponse;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.ExternalAmlResponseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.repository.UserRepository; // Added UserRepository import
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AML Screening Orchestrator
 * Routes screening requests to appropriate tier based on merchant type
 * 
 * Strategy:
 * - New merchants → Tier 1 (Sumsub) for comprehensive screening
 * - Existing merchants → Tier 2 (Aerospike) for fast, free screening
 * - Fallback: If Sumsub fails → Aerospike
 * - All results stored in PostgreSQL for audit
 */
// @RequiredArgsConstructor removed
@Service
public class AmlScreeningOrchestrator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AmlScreeningOrchestrator.class);

    private final SumsubAmlService sumsubService;
    private final AerospikeSanctionsScreeningService aerospikeService;
    private final MerchantRepository merchantRepository;
    private final MerchantScreeningResultRepository screeningResultRepository;
    private final ExternalAmlResponseRepository externalResponseRepository;
    private final AuditTrailRepository auditTrailRepository;
    @SuppressWarnings("unused")
    private final UserRepository userRepository; // Added UserRepository field
    private final ObjectMapper objectMapper;

    public AmlScreeningOrchestrator(SumsubAmlService sumsubService, AerospikeSanctionsScreeningService aerospikeService,
            MerchantRepository merchantRepository, MerchantScreeningResultRepository screeningResultRepository,
            ExternalAmlResponseRepository externalResponseRepository, AuditTrailRepository auditTrailRepository,
            UserRepository userRepository, // Added UserRepository to constructor
            ObjectMapper objectMapper) {
        this.sumsubService = sumsubService;
        this.aerospikeService = aerospikeService;
        this.merchantRepository = merchantRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.externalResponseRepository = externalResponseRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.userRepository = userRepository; // Initialized UserRepository
        this.objectMapper = objectMapper;
    }

    /**
     * Screen merchant using two-tier strategy
     */
    @Transactional
    public ScreeningResult screenMerchant(Merchant merchant) {
        log.info("Orchestrating screening for merchant: {} (new: {})",
                merchant.getLegalName(), merchant.isNew());

        ScreeningResult result;
        String screeningProvider;

        // Determine which tier to use
        if (merchant.isNew() && sumsubService.isEnabled()) {
            log.info("Using Tier 1 (Sumsub) for new merchant '{}'", merchant.getLegalName());
            try {
                // Tier 1: Comprehensive Sumsub screening
                result = sumsubService.screenMerchantWithSumsub(merchant);
                screeningProvider = "SUMSUB";

                // Save external response for audit
                saveExternalResponse(merchant, result, sumsubService.getCostPerCheck());

            } catch (Exception e) {
                log.error("Sumsub screening failed, falling back to Aerospike: {}", e.getMessage());
                // Fallback to Tier 2
                result = aerospikeService.screenMerchant(merchant.getLegalName(), merchant.getTradingName());
                screeningProvider = "AEROSPIKE_FALLBACK";
            }
        } else {
            log.info("Using Tier 2 (Aerospike) for existing merchant '{}'", merchant.getLegalName());
            // Tier 2: Fast local screening
            result = aerospikeService.screenMerchant(merchant.getLegalName(), merchant.getTradingName());
            screeningProvider = "AEROSPIKE";
        }

        // Save screening result to PostgreSQL
        saveScreeningResult(merchant, result, screeningProvider);

        // Update merchant's last screened timestamp
        merchant.updateNextScreeningDue();
        merchantRepository.save(merchant);

        // Create audit trail
        createAuditTrail(merchant, result, screeningProvider);

        log.info("Screening complete for '{}': status={}, matches={}, provider={}",
                merchant.getLegalName(), result.getStatus(), result.getMatchCount(), screeningProvider);

        return result;
    }

    /**
     * Screen beneficial owner
     */
    @Transactional
    public ScreeningResult screenBeneficialOwner(BeneficialOwner owner, Merchant merchant) {
        log.info("Orchestrating screening for UBO: {}", owner.getFullName());

        ScreeningResult result;

        if (merchant.isNew() && sumsubService.isEnabled()) {
            log.info("Using Tier 1 (Sumsub) for new merchant's UBO '{}'", owner.getFullName());
            try {
                result = sumsubService.screenBeneficialOwnerWithSumsub(owner);

                // Save external response
                saveExternalResponseForOwner(owner, result, sumsubService.getCostPerCheck());

            } catch (Exception e) {
                log.error("Sumsub UBO screening failed, falling back to Aerospike: {}", e.getMessage());
                result = aerospikeService.screenBeneficialOwner(owner.getFullName(), owner.getDateOfBirth());
            }
        } else {
            log.info("Using Tier 2 (Aerospike) for existing merchant's UBO '{}'", owner.getFullName());
            result = aerospikeService.screenBeneficialOwner(owner.getFullName(), owner.getDateOfBirth());
        }

        // Update owner flags based on results
        if (result.hasMatches()) {
            owner.setIsSanctioned(true);
            // Check if any match is PEP
            boolean hasPepMatch = result.getMatches().stream()
                    .anyMatch(m -> "PEP".equals(m.getListName()) || m.getPepLevel() != null);
            owner.setIsPep(hasPepMatch);
        }

        owner.setLastScreenedAt(LocalDateTime.now());

        log.info("UBO screening complete: status={}, sanctioned={}, PEP={}",
                result.getStatus(), owner.getIsSanctioned(), owner.getIsPep());

        return result;
    }

    /**
     * Screen merchant with all beneficial owners
     */
    @Transactional
    public Map<String, Object> screenMerchantWithOwners(Merchant merchant) {
        log.info("Screening merchant '{}' with {} beneficial owners",
                merchant.getLegalName(), merchant.getBeneficialOwners().size());

        // Screen merchant
        ScreeningResult merchantResult = screenMerchant(merchant);

        // Screen all beneficial owners
        List<Map<String, Object>> ownerResults = new ArrayList<>();
        for (BeneficialOwner owner : merchant.getBeneficialOwners()) {
            ScreeningResult ownerResult = screenBeneficialOwner(owner, merchant);

            Map<String, Object> ownerData = new HashMap<>();
            ownerData.put("ownerId", owner.getOwnerId());
            ownerData.put("fullName", owner.getFullName());
            ownerData.put("screeningResult", ownerResult);
            ownerData.put("isSanctioned", owner.getIsSanctioned());
            ownerData.put("isPep", owner.getIsPep());

            ownerResults.add(ownerData);
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("merchantId", merchant.getMerchantId());
        response.put("merchantScreeningResult", merchantResult);
        response.put("beneficialOwnerResults", ownerResults);
        response.put("screenedAt", LocalDateTime.now());

        return response;
    }

    /**
     * Save screening result to database
     */
    private MerchantScreeningResult saveScreeningResult(Merchant merchant, ScreeningResult result, String provider) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> matchDetails = objectMapper.convertValue(result.getMatches(), Map.class);

            MerchantScreeningResult record = MerchantScreeningResult.builder()
                    .merchant(merchant)
                    .screeningType("ONBOARDING")
                    .screeningStatus(result.getStatus().name())
                    .matchScore(BigDecimal
                            .valueOf(result.getHighestMatchScore() != null ? result.getHighestMatchScore() : 0.0))
                    .matchCount(result.getMatchCount())
                    .matchDetails(matchDetails)
                    .screeningProvider(provider)
                    .screenedAt(LocalDateTime.now())
                    .screenedBy("SYSTEM")
                    .build();

            MerchantScreeningResult savedRecord = screeningResultRepository.save(record);
            java.util.Objects.requireNonNull(savedRecord, "Saved screening record cannot be null");
            return savedRecord;

        } catch (Exception e) {
            log.error("Failed to save screening result: {}", e.getMessage());
            throw new RuntimeException("Failed to save screening result", e);
        }
    }

    /**
     * Save external AML provider response (Sumsub)
     */
    @SuppressWarnings("unchecked")
    private void saveExternalResponse(Merchant merchant, ScreeningResult result, double cost) {
        try {
            ExternalAmlResponse response = ExternalAmlResponse.builder()
                    .merchant(merchant)
                    .providerName("SUMSUB")
                    .screeningType("MERCHANT")
                    .requestPayload(Map.of("name", merchant.getLegalName()))
                    .responsePayload(objectMapper.convertValue(result, Map.class))
                    .responseStatus("SUCCESS")
                    .httpStatusCode(200)
                    .sanctionsMatch(result.getMatches().stream()
                            .anyMatch(m -> m.getSanctionType() != null))
                    .pepMatch(result.getMatches().stream()
                            .anyMatch(m -> m.getPepLevel() != null))
                    .adverseMediaMatch(result.getMatches().stream()
                            .anyMatch(m -> "ADVERSE_MEDIA".equals(m.getListName())))
                    .overallRiskLevel(result.getStatus().name())
                    .costAmount(BigDecimal.valueOf(cost))
                    .costCurrency("USD")
                    .screenedBy("SYSTEM")
                    .build();

            externalResponseRepository.save(response);

            log.info("Saved external AML response for merchant '{}' (cost: ${})",
                    merchant.getLegalName(), cost);

        } catch (Exception e) {
            log.error("Failed to save external response: {}", e.getMessage());
        }
    }

    /**
     * Save external response for beneficial owner
     */
    @SuppressWarnings("unchecked")
    private void saveExternalResponseForOwner(BeneficialOwner owner, ScreeningResult result, double cost) {
        try {
            ExternalAmlResponse response = ExternalAmlResponse.builder()
                    .owner(owner)
                    .providerName("SUMSUB")
                    .screeningType("BENEFICIAL_OWNER")
                    .requestPayload(Map.of("name", owner.getFullName()))
                    .responsePayload(objectMapper.convertValue(result, Map.class))
                    .responseStatus("SUCCESS")
                    .httpStatusCode(200)
                    .sanctionsMatch(result.hasMatches())
                    .pepMatch(result.getMatches().stream()
                            .anyMatch(m -> m.getPepLevel() != null))
                    .costAmount(BigDecimal.valueOf(cost))
                    .costCurrency("USD")
                    .screenedBy("SYSTEM")
                    .build();

            externalResponseRepository.save(response);

        } catch (Exception e) {
            log.error("Failed to save external response for UBO: {}", e.getMessage());
        }
    }

    /**
     * Create audit trail entry
     */
    private void createAuditTrail(Merchant merchant, ScreeningResult result, String provider) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("screeningResult", result);
            evidence.put("provider", provider);
            evidence.put("matchCount", result.getMatchCount());
            evidence.put("status", result.getStatus());

            AuditTrail audit = AuditTrail.builder()
                    .merchantId(merchant.getMerchantId())
                    .action("SCREENED")
                    .performedBy("SYSTEM")
                    .evidence(evidence)
                    .decision(result.getStatus().name())
                    .decisionReason(result.hasMatches() ? result.getMatchCount() + " potential matches found"
                            : "No matches found")
                    .build();

            AuditTrail savedAudit = auditTrailRepository.save(audit);
            java.util.Objects.requireNonNull(savedAudit, "Saved audit trail cannot be null");

        } catch (Exception e) {
            log.error("Failed to create audit trail: {}", e.getMessage());
        }
    }
}
