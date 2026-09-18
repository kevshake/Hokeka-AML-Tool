package com.posgateway.aml.service.aml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
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
 *
 * <p>Screening runs entirely on the platform's own independent sanctions engine
 * ({@link AerospikeSanctionsScreeningService}, an HTTP proxy to the
 * {@code aml-microservice}). There is no external KYC vendor in the live path.
 *
 * <p>Contract:
 * <ul>
 *   <li>Every merchant and beneficial owner is screened through the independent
 *       engine — no vendor branch, no fabricated external-provider evidence.</li>
 *   <li>When the engine is unavailable it returns
 *       {@link ScreeningResult.ScreeningStatus#UNAVAILABLE}; the orchestrator does
 *       NOT advance the next-screening-due clock and records the outage in the
 *       audit trail rather than treating it as clearance (fail-closed).</li>
 *   <li>All results are persisted to PostgreSQL for audit.</li>
 * </ul>
 */
@Service
public class AmlScreeningOrchestrator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AmlScreeningOrchestrator.class);

    /** Provider tag persisted with every screening result — the independent engine. */
    private static final String PROVIDER = "AML_MICROSERVICE";

    private final AerospikeSanctionsScreeningService screeningEngine;
    private final MerchantRepository merchantRepository;
    private final MerchantScreeningResultRepository screeningResultRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ObjectMapper objectMapper;

    public AmlScreeningOrchestrator(AerospikeSanctionsScreeningService screeningEngine,
            MerchantRepository merchantRepository, MerchantScreeningResultRepository screeningResultRepository,
            AuditTrailRepository auditTrailRepository,
            ObjectMapper objectMapper) {
        this.screeningEngine = screeningEngine;
        this.merchantRepository = merchantRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Screen merchant using two-tier strategy
     */
    @Transactional
    public ScreeningResult screenMerchant(Merchant merchant) {
        log.info("Orchestrating screening for merchant: {} (new: {})",
                merchant.getLegalName(), merchant.isNew());

        // Screen through the platform's own independent sanctions engine.
        ScreeningResult result = screeningEngine.screenMerchant(
                merchant.getLegalName(), merchant.getTradingName());

        // Save screening result to PostgreSQL
        saveScreeningResult(merchant, result, PROVIDER);

        if (result.getStatus() != ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            merchant.updateNextScreeningDue();
            merchantRepository.save(merchant);
        }

        // Create audit trail
        createAuditTrail(merchant, result, PROVIDER);

        log.info("Screening complete for '{}': status={}, matches={}, provider={}",
                merchant.getLegalName(), result.getStatus(), result.getMatchCount(), PROVIDER);

        return result;
    }

    /**
     * Screen beneficial owner
     */
    @Transactional
    public ScreeningResult screenBeneficialOwner(BeneficialOwner owner, Merchant merchant) {
        log.info("Orchestrating screening for UBO: {}", owner.getFullName());

        // Screen the beneficial owner through the platform's own independent engine.
        ScreeningResult result = screeningEngine.screenBeneficialOwner(
                owner.getFullName(), owner.getDateOfBirth());

        // Update owner flags based on results
        if (result.hasMatches()) {
            owner.setIsSanctioned(true);
            // Check if any match is PEP
            boolean hasPepMatch = result.getMatches().stream()
                    .anyMatch(m -> "PEP".equals(m.getListName()) || m.getPepLevel() != null);
            owner.setIsPep(hasPepMatch);
        }

        if (result.getStatus() != ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            owner.setLastScreenedAt(LocalDateTime.now());
        }

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
            // W14-6 fix: objectMapper.convertValue(List, Map.class) throws whenever the list is
            // non-empty (Jackson can't convert a List to a Map), which meant this method threw --
            // caught below and rethrown -- for every merchant that actually had a sanctions match,
            // failing the save exactly when there was something worth persisting. getMatches() is a
            // List<Match>; convert it to a List<Map<String,Object>> first, then wrap it under a key
            // so the result is an actual Map, matching what matchDetails (Map<String,Object>) needs.
            List<Map<String, Object>> matchesAsMaps = objectMapper.convertValue(
                    result.getMatches(), new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> matchDetails = new HashMap<>();
            matchDetails.put("matches", matchesAsMaps);
            matchDetails.put("matchCount", result.getMatchCount());

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
                    .decisionReason(result.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE
                            ? "Screening provider unavailable; no clearance decision made"
                            : result.hasMatches() ? result.getMatchCount() + " potential matches found"
                            : "No matches found")
                    .build();

            AuditTrail savedAudit = auditTrailRepository.save(audit);
            java.util.Objects.requireNonNull(savedAudit, "Saved audit trail cannot be null");

        } catch (Exception e) {
            log.error("Failed to create audit trail: {}", e.getMessage());
        }
    }
}
