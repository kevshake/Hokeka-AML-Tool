package com.posgateway.aml.service.merchant;

import com.posgateway.aml.dto.request.BeneficialOwnerRequest;
import com.posgateway.aml.dto.request.MerchantOnboardingRequest;
import com.posgateway.aml.dto.response.MerchantOnboardingResponse;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceCheck;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import com.posgateway.aml.service.corporate.CorporateIntelligenceService;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.workflow.WorkflowAutomationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merchant Onboarding Service
 * Handles complete onboarding workflow including screening and risk assessment
 */
@Service
public class MerchantOnboardingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MerchantOnboardingService.class);

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ComplianceCaseRepository complianceCaseRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private MerchantScreeningResultRepository screeningResultRepository;

    @Autowired
    private BeneficialOwnerRepository beneficialOwnerRepository;

    @Autowired
    private com.posgateway.aml.service.security.PiiLookupHasher piiLookupHasher;

    @Autowired
    private AmlScreeningOrchestrator screeningOrchestrator;

    @Autowired
    private CorporateIntelligenceService corporateIntelligenceService;

    @Autowired
    private PspIsolationService pspIsolationService;

    @Autowired
    private PspRepository pspRepository;

    @Value("${risk.threshold.approve:30}")
    private int approveThreshold;

    @Value("${risk.threshold.review:50}")
    private int reviewThreshold;

    @Value("${risk.threshold.reject:80}")
    private int rejectThreshold;

    @Autowired
    private MccMappingService mccMappingService;

    @Autowired
    private HighRiskCountryRepository highRiskCountryRepository;

    @Autowired
    private WorkflowAutomationService workflowAutomationService;

    @Autowired
    private com.posgateway.aml.service.underwriting.MerchantVerificationOrchestrator verificationOrchestrator;

    @Autowired
    private com.posgateway.aml.service.underwriting.ProgressiveActivationService progressiveActivationService;

    @Autowired
    private com.posgateway.aml.repository.limits.RiskThresholdRepository riskThresholdRepository;

    @Value("${risk.mcc.high_risk:6211,7995,7273,5993,6051}")
    private List<String> highRiskMccsList;

    /** Final fallback when no risk_thresholds row exists for the calculated level. */
    @Value("${merchant.onboarding.fallback_daily_limit:10000}")
    private java.math.BigDecimal fallbackDailyLimit;

    /**
     * Onboard new merchant with complete screening
     */
    @Transactional
    public MerchantOnboardingResponse onboardMerchant(MerchantOnboardingRequest request) {
        log.info("Starting merchant onboarding: {}", request.getLegalName());

        validateOwnershipDeclaration(request);
        Long pspId = pspIsolationService.sanitizePspId(request.getPspId());
        if (pspId == null || pspId == 0L) {
            throw new IllegalArgumentException("A valid PSP must be selected for merchant onboarding");
        }
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found: " + pspId));
        merchantRepository.findByPspPspIdAndCountryAndRegistrationNumber(
                pspId, request.getCountry(), request.getRegistrationNumber()).ifPresent(existing -> {
                    throw new IllegalArgumentException("A merchant with this registration number already exists in the PSP");
                });

        // Step 1: Create merchant entity
        Merchant merchant = createMerchantFromRequest(request, psp);
        merchant = merchantRepository.save(merchant);

        // KYC toggle: if an admin has waived KYC for this PSP, skip screening/underwriting
        // and activate the merchant so transactions flow. Audited explicitly.
        if (!psp.isKycRequired()) {
            return onboardWithKycWaived(merchant, psp);
        }

        // Step 2: Screen merchant and beneficial owners
        Map<String, Object> screeningResults = screeningOrchestrator.screenMerchantWithOwners(merchant);

        ScreeningResult merchantScreeningResult = (ScreeningResult) screeningResults.get("merchantScreeningResult");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ownerResults = (List<Map<String, Object>>) screeningResults
                .get("beneficialOwnerResults");

        // Step 3: Verify the legal entity and collect adverse-media evidence.
        CorporateIntelligenceCheck corporateCheck = corporateIntelligenceService.performCheck(
                merchant, "ONBOARDING", "SYSTEM_ONBOARDING");

        // Step 4: Calculate the combined onboarding risk.
        int riskScore = calculateRiskScore(merchant, merchantScreeningResult, ownerResults, corporateCheck);

        // Step 5: Make decision
        String decision = makeDecision(riskScore, merchantScreeningResult, ownerResults, corporateCheck);
        String status = determineStatus(decision);
        String riskLevel = riskScore >= 80 ? "CRITICAL"
                : riskScore >= 60 ? "HIGH"
                : riskScore >= 30 ? "MEDIUM"
                : "LOW";

        // Step 5b: Deep underwriting verification (internal checks + linkage + fail-closed
        // vendor adapters). Persists evidence signals and can ESCALATE the decision (never
        // downgrade): a hard stop or EDD outcome pushes an otherwise-APPROVE to REVIEW.
        com.posgateway.aml.model.underwriting.UnderwritingOutcome uw = null;
        try {
            uw = verificationOrchestrator.verify(merchant);
            boolean escalate = !uw.getHardStops().isEmpty()
                    || uw.getDecision() == com.posgateway.aml.model.underwriting.UnderwritingDecision.ENHANCED_DUE_DILIGENCE
                    || uw.getDecision() == com.posgateway.aml.model.underwriting.UnderwritingDecision.REJECT;
            if (escalate && "APPROVE".equals(decision)) {
                log.warn("Underwriting escalation for merchant {}: {} (hardStops={}) — APPROVE→REVIEW",
                        merchant.getMerchantId(), uw.getDecision(), uw.getHardStops());
                decision = "REVIEW";
                status = determineStatus(decision);
            }
        } catch (Exception e) {
            log.error("Underwriting verification failed for merchant {}: {}",
                    merchant.getMerchantId(), e.getMessage());
        }

        // Step 6: Update merchant status + per-risk-level daily cap
        merchant.setStatus(status);
        merchant.setRiskLevel(riskLevel);
        merchant.setDailyLimit(resolveDailyLimit(merchant, riskLevel));
        merchantRepository.save(merchant);

        // Step 6b: Progressive/conditional activation — a conditional-approval or EDD
        // underwriting outcome applies a REAL reduced daily limit for a monitoring window.
        if (uw != null) {
            try {
                progressiveActivationService.applyControls(merchant, uw);
            } catch (Exception e) {
                log.error("Progressive activation failed for merchant {}: {}",
                        merchant.getMerchantId(), e.getMessage());
            }
        }

        // Step 7: Create compliance case if needed
        Long complianceCaseId = null;
        if ("REVIEW".equals(decision) || "REJECT".equals(decision)) {
            ComplianceCase complianceCase = createComplianceCase(merchant, merchantScreeningResult, riskScore,
                    decision);
            complianceCaseId = complianceCase.getId(); // Changed getCaseId() to getId()
        }

        // Step 8: Create audit trail
        createAuditTrail(merchant, decision, riskScore, merchantScreeningResult, corporateCheck);

        // Step 9: Build response
        MerchantOnboardingResponse response = buildResponse(
                merchant,
                merchantScreeningResult,
                ownerResults,
                riskScore,
                decision,
                complianceCaseId,
                corporateCheck);

        // WORKFLOW AUTOMATION: Auto-approve if Eligible
        try {
            com.posgateway.aml.model.RiskLevel level = com.posgateway.aml.model.RiskLevel
                    .valueOf(response.getRiskLevel());
            if ("APPROVE".equals(decision)) {
                workflowAutomationService.autoApproveLowRisk(merchant, level);
            }
            // Refresh merchant status if updated
            if (level == com.posgateway.aml.model.RiskLevel.LOW) {
                response.setStatus(merchant.getStatus());
            }
        } catch (Exception e) {
            log.error("Error in workflow automation", e);
        }

        log.info("Merchant onboarding complete: {} - Decision: {}, Risk Score: {}",
                merchant.getLegalName(), decision, riskScore);

        return response;
    }

    /**
     * Get merchant by ID
     */
    public MerchantOnboardingResponse getMerchantById(Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + id));
        var screeningRecord = screeningResultRepository.findLatestByMerchantId(id);
        List<BeneficialOwner> owners = beneficialOwnerRepository.findByMerchant_MerchantId(id);
        return buildMerchantResponse(merchant, screeningRecord, owners);
    }

    /**
     * Build a full response for one already-loaded merchant from its (optional) latest
     * screening record and its beneficial owners. Shared by {@link #getMerchantById(Long)}
     * and the batch listing path {@link #getMerchantResponses(List)} so both produce an
     * identical response — the batch path just supplies pre-fetched data instead of
     * re-querying per merchant (N+1 fix).
     */
    private MerchantOnboardingResponse buildMerchantResponse(Merchant merchant,
            java.util.Optional<com.posgateway.aml.entity.merchant.MerchantScreeningResult> screeningRecord,
            List<BeneficialOwner> owners) {

        ScreeningResult result = new ScreeningResult(); // Default empty
        String provider = "UNKNOWN";

        if (screeningRecord.isPresent()) {
            try {
                // Convert the persisted summary into the API screening model.
                String statusStr = screeningRecord.get().getScreeningStatus();
                if (statusStr != null) {
                    result.setStatus(ScreeningResult.ScreeningStatus.valueOf(statusStr));
                }
                result.setMatchCount(screeningRecord.get().getMatchCount());
                provider = screeningRecord.get().getScreeningProvider();
            } catch (Exception e) {
                log.warn("Error parsing screening result for merchant {}: {}", merchant.getMerchantId(),
                        e.getMessage());
            }
        }

        // Build response with all fields needed by frontend
        MerchantOnboardingResponse.MerchantOnboardingResponseBuilder builder = MerchantOnboardingResponse.builder()
                .merchantId(merchant.getMerchantId())
                .legalName(merchant.getLegalName())
                .status(merchant.getStatus() != null ? merchant.getStatus() : "PENDING_SCREENING")
                .merchantScreeningResult(result)
                .screeningProvider(provider)
                .screenedAt(screeningRecord
                        .map(com.posgateway.aml.entity.merchant.MerchantScreeningResult::getScreenedAt).orElse(null))
                .country(merchant.getCountry())
                .kycStatus(merchant.getKycStatus() != null ? merchant.getKycStatus() : "PENDING")
                .contractStatus(merchant.getContractStatus() != null ? merchant.getContractStatus() : "NO_CONTRACT")
                .dailyLimit(merchant.getDailyLimit())
                .currentUsage(merchant.getCurrentUsage())
                .riskLevel(merchant.getRiskLevel() != null ? merchant.getRiskLevel() : "UNKNOWN")
                .krs(merchant.getKrs())
                .cra(merchant.getCra())
                .cbkEconomicSectorCode(merchant.getCbkEconomicSectorCode())
                .cbkSettlementAccountConfigured(merchant.isCbkSettlementAccountConfigured());

        // Add MCC description safely
        if (merchant.getMcc() != null) {
            try {
                builder.mccDescription(mccMappingService.getDescription(merchant.getMcc()));
            } catch (Exception e) {
                log.warn("Error getting MCC description for merchant {}: {}", merchant.getMerchantId(),
                        e.getMessage());
                builder.mccDescription("Unknown Category");
            }
        } else {
            builder.mccDescription("Unknown Category");
        }

        // Build owner screening details from the supplied beneficial owners.
        List<MerchantOnboardingResponse.OwnerScreeningDetail> ownerDetails = new ArrayList<>();
        
        for (BeneficialOwner owner : owners) {
            ScreeningResult ownerResult = new ScreeningResult();
            ownerResult.setStatus(owner.getIsSanctioned() != null && owner.getIsSanctioned() 
                ? ScreeningResult.ScreeningStatus.MATCH 
                : ScreeningResult.ScreeningStatus.CLEAR);
            ownerResult.setMatchCount(owner.getIsSanctioned() != null && owner.getIsSanctioned() ? 1 : 0);
            
            MerchantOnboardingResponse.OwnerScreeningDetail detail = 
                MerchantOnboardingResponse.OwnerScreeningDetail.builder()
                    .ownerId(owner.getOwnerId())
                    .fullName(owner.getFullName())
                    .screeningResult(ownerResult)
                    .isSanctioned(owner.getIsSanctioned() != null ? owner.getIsSanctioned() : false)
                    .isPep(owner.getIsPep() != null ? owner.getIsPep() : false)
                    .build();
            ownerDetails.add(detail);
        }
        
        builder.beneficialOwnerResults(ownerDetails);
        builder.tradingName(merchant.getTradingName());
        builder.contactEmail(merchant.getContactEmail());
        builder.mcc(merchant.getMcc());
        builder.businessType(merchant.getBusinessType() != null ? merchant.getBusinessType().toString() : null);
        
        // Calculate risk score if not already set
        if (merchant.getRiskLevel() == null || "UNKNOWN".equals(merchant.getRiskLevel())) {
            // Simple risk calculation based on screening results
            int riskScore = 0;
            if (result.getStatus() == ScreeningResult.ScreeningStatus.MATCH) riskScore += 50;
            if (ownerDetails.stream().anyMatch(o -> o.getIsSanctioned())) riskScore += 30;
            if (ownerDetails.stream().anyMatch(o -> o.getIsPep())) riskScore += 20;
            builder.riskScore(riskScore);
            builder.riskLevel(riskScore >= 80 ? "CRITICAL" : riskScore >= 60 ? "HIGH" : riskScore >= 30 ? "MEDIUM" : "LOW");
        } else {
            // Try to get risk score from merchant
            // Risk score might not be stored directly, so we'll calculate it
            int riskScore = 0;
            if (result.getStatus() == ScreeningResult.ScreeningStatus.MATCH) riskScore += 50;
            if (ownerDetails.stream().anyMatch(o -> o.getIsSanctioned())) riskScore += 30;
            if (ownerDetails.stream().anyMatch(o -> o.getIsPep())) riskScore += 20;
            builder.riskScore(riskScore);
        }

        MerchantOnboardingResponse response = builder.build();
        return response;
    }

    /**
     * Build responses for a page of already-loaded merchants without re-querying each one.
     * Collapses the old per-merchant path (findById + findLatestByMerchantId + owners per
     * merchant = 1+3N queries) into two batch queries (latest screening + owners) plus the
     * caller's single page query. Output is identical to mapping each merchant through
     * {@link #getMerchantById(Long)}.
     */
    @Transactional(readOnly = true)
    public List<MerchantOnboardingResponse> getMerchantResponses(List<Merchant> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = merchants.stream()
                .map(Merchant::getMerchantId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        // Latest screening per merchant: rows come back newest-first, so the first seen wins.
        java.util.Map<Long, com.posgateway.aml.entity.merchant.MerchantScreeningResult> latestScreening =
                new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (var row : screeningResultRepository.findByMerchant_MerchantIdInOrderByScreenedAtDesc(ids)) {
                Long mid = (row.getMerchant() != null) ? row.getMerchant().getMerchantId() : null;
                if (mid != null) {
                    latestScreening.putIfAbsent(mid, row);
                }
            }
        }

        // Beneficial owners grouped by merchant.
        java.util.Map<Long, List<BeneficialOwner>> ownersByMerchant = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (BeneficialOwner owner : beneficialOwnerRepository.findByMerchant_MerchantIdIn(ids)) {
                Long mid = (owner.getMerchant() != null) ? owner.getMerchant().getMerchantId() : null;
                if (mid != null) {
                    ownersByMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(owner);
                }
            }
        }

        List<MerchantOnboardingResponse> out = new ArrayList<>(merchants.size());
        for (Merchant m : merchants) {
            Long mid = m.getMerchantId();
            var screening = java.util.Optional.ofNullable(mid != null ? latestScreening.get(mid) : null);
            List<BeneficialOwner> owners = ownersByMerchant.getOrDefault(mid, java.util.Collections.emptyList());
            out.add(buildMerchantResponse(m, screening, owners));
        }
        return out;
    }

    /**
     * Create merchant entity from request
     */
    private Merchant createMerchantFromRequest(MerchantOnboardingRequest request, Psp psp) {
        Merchant merchant = Merchant.builder()
                .legalName(request.getLegalName())
                .tradingName(request.getTradingName())
                .country(request.getCountry())
                .registrationNumber(request.getRegistrationNumber())
                .taxId(request.getTaxId())
                .mcc(request.getMcc())
                .businessType(request.getBusinessType())
                .expectedMonthlyVolume(request.getExpectedMonthlyVolume())
                .transactionChannel(request.getTransactionChannel())
                .website(request.getWebsite())
                .addressStreet(request.getAddressStreet())
                .addressCity(request.getAddressCity())
                .addressState(request.getAddressState())
                .addressPostalCode(request.getAddressPostalCode())
                .addressCountry(request.getAddressCountry())
                .operatingCountries(
                        request.getOperatingCountries() != null ? request.getOperatingCountries().toArray(new String[0])
                                : null)
                .registrationDate(request.getRegistrationDate())
                .status("PENDING_SCREENING")
                .psp(psp)
                .beneficialOwners(new ArrayList<>())
                .build();
        merchant.setContactEmail(request.getContactEmail());
        merchant.setCbkSettlementAccountNumber(request.getCbkSettlementAccountNumber());
        merchant.setCbkEconomicSectorCode(request.getCbkEconomicSectorCode());

        // Add beneficial owners
        for (BeneficialOwnerRequest ownerReq : request.getBeneficialOwners()) {
            BeneficialOwner owner = BeneficialOwner.builder()
                    .merchant(merchant)
                    .fullName(ownerReq.getFullName())
                    .dateOfBirth(ownerReq.getDateOfBirth())
                    .nationality(ownerReq.getNationality())
                    .countryOfResidence(ownerReq.getCountryOfResidence())
                    .passportNumber(ownerReq.getPassportNumber())
                    .nationalId(ownerReq.getNationalId())
                    .ownershipPercentage(ownerReq.getOwnershipPercentage())
                    .build();
            owner.setPassportHash(piiLookupHasher.hashIdentifier(ownerReq.getPassportNumber()));
            owner.setNationalIdHash(piiLookupHasher.hashIdentifier(ownerReq.getNationalId()));

            merchant.getBeneficialOwners().add(owner);
        }

        merchant.setKycStatus("PENDING");
        merchant.setContractStatus("NO_CONTRACT");
        // Daily limit deliberately left null here — the onboardMerchant flow
        // computes a risk score, then setDailyLimitForRiskLevel below stamps the
        // limit configured for that level in risk_thresholds. Hardcoding 10,000
        // at this point would mask any per-PSP override.
        return merchant;
    }

    private void validateOwnershipDeclaration(MerchantOnboardingRequest request) {
        if (request.getBeneficialOwners() == null || request.getBeneficialOwners().isEmpty()) {
            throw new IllegalArgumentException("At least one beneficial owner is required");
        }
        if (!request.isOwnershipDeclarationValid()) {
            throw new IllegalArgumentException(
                    "Beneficial ownership percentages must be positive and total no more than 100");
        }
        for (BeneficialOwnerRequest owner : request.getBeneficialOwners()) {
            if (owner == null || !owner.isIdentityDocumentProvided()) {
                throw new IllegalArgumentException(
                        "Each beneficial owner requires either a national ID or passport number");
            }
        }
    }

    /**
     * Pull the configured per-day cap for a given risk level from {@code risk_thresholds}.
     * Falls back to {@link #fallbackDailyLimit} when no row is configured.
     */
    private java.math.BigDecimal resolveDailyLimit(Merchant merchant, String riskLevel) {
        if (riskLevel == null) return fallbackDailyLimit;
        Long pspId = merchant.getPsp() == null ? null : merchant.getPsp().getPspId();
        java.util.Optional<com.posgateway.aml.entity.limits.RiskThreshold> threshold = pspId == null
                ? riskThresholdRepository.findByRiskLevelAndPspIdIsNull(riskLevel)
                : riskThresholdRepository.findByRiskLevelAndPspId(riskLevel, pspId)
                        .or(() -> riskThresholdRepository.findByRiskLevelAndPspIdIsNull(riskLevel));
        return threshold
                .map(rt -> rt.getDailyLimit() != null ? rt.getDailyLimit() : fallbackDailyLimit)
                .orElse(fallbackDailyLimit);
    }

    /**
     * Calculate the combined onboarding risk from sanctions, ownership, country,
     * MCC, and corporate-intelligence evidence.
     */
    private int calculateRiskScore(Merchant merchant, ScreeningResult merchantResult,
            List<Map<String, Object>> ownerResults, CorporateIntelligenceCheck corporateCheck) {
        int score = 0;

        if (merchantResult.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            score = Math.max(score, reviewThreshold);
        }

        // Sanctions match: +50 points
        if (merchantResult.hasMatches()) {
            score += 50;
        }

        // Owner sanctions/PEP: +30 points each
        for (Map<String, Object> ownerData : ownerResults) {
            ScreeningResult ownerScreening = (ScreeningResult) ownerData.get("screeningResult");
            if (ownerScreening != null
                    && ownerScreening.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
                score = Math.max(score, reviewThreshold);
            }
            Boolean isSanctioned = (Boolean) ownerData.get("isSanctioned");
            Boolean isPep = (Boolean) ownerData.get("isPep");

            if (Boolean.TRUE.equals(isSanctioned)) {
                score += 30;
            }
            if (Boolean.TRUE.equals(isPep)) {
                score += 15;
            }
        }

        // High-risk country: +20 points
        if (merchant.getCountry() != null && highRiskCountryRepository.existsByCountryCode(merchant.getCountry())) {
            score += 20;
        }

        // High-risk MCC: +15 points
        if (highRiskMccsList != null && highRiskMccsList.contains(merchant.getMcc())) {
            score += 15;
        }

        // Corporate intelligence is an investigator-led input, so it contributes
        // to risk without allowing media leads alone to cause automatic rejection.
        score += Math.min(40, (int) Math.round(corporateCheck.getRiskScore() * 0.4));

        return Math.min(score, 100); // Cap at 100
    }

    /**
     * Make onboarding decision based on risk score
     */
    private String makeDecision(int riskScore, ScreeningResult merchantResult, List<Map<String, Object>> ownerResults,
            CorporateIntelligenceCheck corporateCheck) {
        boolean screeningUnavailable = merchantResult.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE
                || ownerResults.stream()
                .map(result -> (ScreeningResult) result.get("screeningResult"))
                .anyMatch(result -> result != null
                        && result.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE);
        if (screeningUnavailable || corporateCheck.getStatus() == CorporateIntelligenceStatus.UNAVAILABLE) {
            return "REVIEW";
        }
        if (riskScore >= rejectThreshold) {
            return "REJECT";
        } else if (corporateCheck.getStatus() == CorporateIntelligenceStatus.REVIEW
                || riskScore >= reviewThreshold) {
            return "REVIEW";
        } else if (merchantResult.hasMatches()) {
            return "REVIEW"; // Always review if there are any matches
        } else {
            return "APPROVE";
        }
    }

    /**
     * Determine merchant status from decision
     */
    private String determineStatus(String decision) {
        return switch (decision) {
            case "APPROVE" -> "ACTIVE";
            case "REVIEW" -> "UNDER_REVIEW";
            case "REJECT" -> "BLOCKED";
            default -> "PENDING_SCREENING";
        };
    }

    /**
     * Create compliance case for manual review
     */
    private ComplianceCase createComplianceCase(Merchant merchant, ScreeningResult screeningResult,
            int riskScore, String decision) {
        String priority = riskScore >= 80 ? "HIGH" : riskScore >= 50 ? "MEDIUM" : "LOW";

        ComplianceCase complianceCase = ComplianceCase.builder()
                .caseReference("ONBOARD-" + merchant.getMerchantId()) // Added case reference
                .description("Onboarding Compliance Case for " + merchant.getLegalName()) // Description
                .merchantId(merchant.getMerchantId())
                .pspId(merchant.getPsp().getPspId())
                .status(com.posgateway.aml.model.CaseStatus.NEW) // Updated status enum
                .priority(com.posgateway.aml.model.CasePriority.valueOf(priority)) // Updated priority enum
                // .createdBy("SYSTEM") // Removed
                .slaDeadline(LocalDateTime.now().plusHours(48)) // Updated field name
                .build();

        return complianceCaseRepository.save(complianceCase);
    }

    /**
     * Create audit trail entry
     */
    /**
     * Onboarding path when KYC is waived for the merchant's PSP (kycEnabled=false).
     * The merchant is activated immediately with kycStatus=NOT_REQUIRED and no screening
     * or underwriting is performed. The waiver is recorded in the audit trail so it is
     * always reconstructable who onboarded without KYC and why.
     */
    private MerchantOnboardingResponse onboardWithKycWaived(Merchant merchant, Psp psp) {
        log.warn("KYC waived for merchant '{}' — PSP {} has kycEnabled=false; activating without screening",
                merchant.getLegalName(), psp.getPspId());

        merchant.setKycStatus("NOT_REQUIRED");
        merchant.setStatus("ACTIVE");
        merchant.setRiskLevel("LOW");
        merchant.setDailyLimit(resolveDailyLimit(merchant, "LOW"));
        merchantRepository.save(merchant);

        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("kycWaived", true);
            evidence.put("pspId", psp.getPspId());
            evidence.put("pspKycEnabled", psp.getKycEnabled());
            AuditTrail audit = AuditTrail.builder()
                    .merchantId(merchant.getMerchantId())
                    .action("ONBOARDED_KYC_WAIVED")
                    .performedBy("SYSTEM")
                    .decision("APPROVE")
                    .decisionReason("KYC/KYB waived: PSP " + psp.getPspId() + " has kycEnabled=false")
                    .evidence(evidence)
                    .build();
            auditTrailRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to write KYC-waiver audit for merchant {}: {}",
                    merchant.getMerchantId(), e.getMessage());
        }

        ScreeningResult waived = new ScreeningResult();
        waived.setStatus(ScreeningResult.ScreeningStatus.CLEAR);
        waived.setMatchCount(0);
        waived.setScreeningProvider("KYC_WAIVED");

        String mccDescription;
        try {
            mccDescription = merchant.getMcc() != null
                    ? mccMappingService.getDescription(merchant.getMcc()) : "Unknown Category";
        } catch (Exception e) {
            mccDescription = "Unknown Category";
        }

        return MerchantOnboardingResponse.builder()
                .merchantId(merchant.getMerchantId())
                .legalName(merchant.getLegalName())
                .status(merchant.getStatus())
                .decision("APPROVE")
                .decisionReason("KYC waived for this PSP (kycEnabled=false)")
                .merchantScreeningResult(waived)
                .beneficialOwnerResults(new ArrayList<>())
                .riskScore(0)
                .riskLevel("LOW")
                .screenedAt(LocalDateTime.now())
                .screeningProvider("KYC_WAIVED")
                .country(merchant.getCountry())
                .tradingName(merchant.getTradingName())
                .contactEmail(merchant.getContactEmail())
                .mcc(merchant.getMcc())
                .businessType(merchant.getBusinessType() != null ? merchant.getBusinessType().toString() : null)
                .kycStatus(merchant.getKycStatus())
                .contractStatus(merchant.getContractStatus())
                .dailyLimit(merchant.getDailyLimit())
                .currentUsage(merchant.getCurrentUsage())
                .mccDescription(mccDescription)
                .krs(merchant.getKrs())
                .cra(merchant.getCra())
                .cbkEconomicSectorCode(merchant.getCbkEconomicSectorCode())
                .cbkSettlementAccountConfigured(merchant.isCbkSettlementAccountConfigured())
                .build();
    }

    private void createAuditTrail(Merchant merchant, String decision, int riskScore,
            ScreeningResult screeningResult, CorporateIntelligenceCheck corporateCheck) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("decision", decision);
        evidence.put("riskScore", riskScore);
        evidence.put("screeningResult", screeningResult);
        evidence.put("corporateIntelligenceCheckId", corporateCheck.getId());
        evidence.put("corporateIntelligenceStatus", corporateCheck.getStatus());
        evidence.put("corporateIntelligenceEvidenceHash", corporateCheck.getEvidenceHash());

        AuditTrail audit = AuditTrail.builder()
                .merchantId(merchant.getMerchantId())
                .action("ONBOARDED")
                .performedBy("SYSTEM")
                .decision(decision)
                .decisionReason("Risk score: " + riskScore)
                .evidence(evidence)
                .build();

        auditTrailRepository.save(audit);
    }

    /**
     * Build response DTO
     */
    private MerchantOnboardingResponse buildResponse(Merchant merchant, ScreeningResult merchantResult,
            List<Map<String, Object>> ownerResults, int riskScore,
            String decision, Long complianceCaseId, CorporateIntelligenceCheck corporateCheck) {
        List<MerchantOnboardingResponse.OwnerScreeningDetail> ownerDetails = new ArrayList<>();

        for (Map<String, Object> ownerData : ownerResults) {
            MerchantOnboardingResponse.OwnerScreeningDetail detail = MerchantOnboardingResponse.OwnerScreeningDetail
                    .builder()
                    .ownerId((Long) ownerData.get("ownerId"))
                    .fullName((String) ownerData.get("fullName"))
                    .screeningResult((ScreeningResult) ownerData.get("screeningResult"))
                    .isSanctioned((Boolean) ownerData.get("isSanctioned"))
                    .isPep((Boolean) ownerData.get("isPep"))
                    .build();

            ownerDetails.add(detail);
        }

        String riskLevel = riskScore >= 80 ? "CRITICAL" : riskScore >= 60 ? "HIGH" : riskScore >= 30 ? "MEDIUM" : "LOW";

        return MerchantOnboardingResponse.builder()
                .merchantId(merchant.getMerchantId())
                .legalName(merchant.getLegalName())
                .status(merchant.getStatus())
                .decision(decision)
                .decisionReason("Combined screening risk score: " + riskScore + "; "
                        + corporateCheck.getDecisionReason())
                .merchantScreeningResult(merchantResult)
                .beneficialOwnerResults(ownerDetails)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .complianceCaseId(complianceCaseId)
                .screenedAt(LocalDateTime.now())
                .screeningProvider(merchantResult.getScreeningProvider())
                .country(merchant.getCountry())
                .tradingName(merchant.getTradingName())
                .contactEmail(merchant.getContactEmail())
                .mcc(merchant.getMcc())
                .businessType(merchant.getBusinessType() != null ? merchant.getBusinessType().toString() : null)
                .kycStatus(merchant.getKycStatus())
                .contractStatus(merchant.getContractStatus())
                .dailyLimit(merchant.getDailyLimit())
                .currentUsage(merchant.getCurrentUsage())
                .mccDescription(mccMappingService.getDescription(merchant.getMcc()))
                .krs(merchant.getKrs())
                .cra(merchant.getCra())
                .cbkEconomicSectorCode(merchant.getCbkEconomicSectorCode())
                .cbkSettlementAccountConfigured(merchant.isCbkSettlementAccountConfigured())
                .build();
    }
}
