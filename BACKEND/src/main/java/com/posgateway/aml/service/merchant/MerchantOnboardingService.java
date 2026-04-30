package com.posgateway.aml.service.merchant;

import com.posgateway.aml.dto.request.BeneficialOwnerRequest;
import com.posgateway.aml.dto.request.MerchantOnboardingRequest;
import com.posgateway.aml.dto.response.MerchantOnboardingResponse;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
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
    private AmlScreeningOrchestrator screeningOrchestrator;

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

    @Value("${risk.mcc.high_risk:6211,7995,7273,5993,6051}")
    private List<String> highRiskMccsList;

    /**
     * Onboard new merchant with complete screening
     */
    @Transactional
    public MerchantOnboardingResponse onboardMerchant(MerchantOnboardingRequest request) {
        log.info("Starting merchant onboarding: {}", request.getLegalName());

        // Step 1: Create merchant entity
        Merchant merchant = createMerchantFromRequest(request);
        merchant = merchantRepository.save(merchant);

        // Step 2: Screen merchant and beneficial owners
        Map<String, Object> screeningResults = screeningOrchestrator.screenMerchantWithOwners(merchant);

        ScreeningResult merchantScreeningResult = (ScreeningResult) screeningResults.get("merchantScreeningResult");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ownerResults = (List<Map<String, Object>>) screeningResults
                .get("beneficialOwnerResults");

        // Step 3: Calculate risk score (simplified - would use Risk Rules Engine in
        // full implementation)
        int riskScore = calculateRiskScore(merchant, merchantScreeningResult, ownerResults);

        // Step 4: Make decision
        String decision = makeDecision(riskScore, merchantScreeningResult, ownerResults);
        String status = determineStatus(decision);

        // Step 5: Update merchant status
        merchant.setStatus(status);
        merchantRepository.save(merchant);

        // Step 6: Create compliance case if needed
        Long complianceCaseId = null;
        if ("REVIEW".equals(decision) || "REJECT".equals(decision)) {
            ComplianceCase complianceCase = createComplianceCase(merchant, merchantScreeningResult, riskScore,
                    decision);
            complianceCaseId = complianceCase.getId(); // Changed getCaseId() to getId()
        }

        // Step 7: Create audit trail
        createAuditTrail(merchant, decision, riskScore, merchantScreeningResult);

        // Step 8: Build response
        MerchantOnboardingResponse response = buildResponse(
                merchant,
                merchantScreeningResult,
                ownerResults,
                riskScore,
                decision,
                complianceCaseId);

        // WORKFLOW AUTOMATION: Auto-approve if Eligible
        try {
            com.posgateway.aml.model.RiskLevel level = com.posgateway.aml.model.RiskLevel
                    .valueOf(response.getRiskLevel());
            workflowAutomationService.autoApproveLowRisk(merchant, level);
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

        // Get latest screening result
        var screeningRecord = screeningResultRepository.findLatestByMerchantId(id);

        ScreeningResult result = new ScreeningResult(); // Default empty
        String provider = "UNKNOWN";

        if (screeningRecord.isPresent()) {
            try {
                // Convert entity to model
                // simplified for display
                String statusStr = screeningRecord.get().getScreeningStatus();
                if (statusStr != null) {
                    result.setStatus(ScreeningResult.ScreeningStatus.valueOf(statusStr));
                }
                result.setMatchCount(screeningRecord.get().getMatchCount());
                provider = screeningRecord.get().getScreeningProvider();
            } catch (Exception e) {
                log.warn("Error parsing screening result for merchant {}: {}", id, e.getMessage());
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
                .currentUsage(merchant.getCurrentUsage())
                .riskLevel(merchant.getRiskLevel() != null ? merchant.getRiskLevel() : "UNKNOWN")
                .krs(merchant.getKrs())
                .cra(merchant.getCra());

        // Add MCC description safely
        if (merchant.getMcc() != null) {
            try {
                builder.mccDescription(mccMappingService.getDescription(merchant.getMcc()));
            } catch (Exception e) {
                log.warn("Error getting MCC description for merchant {}: {}", id, e.getMessage());
                builder.mccDescription("Unknown Category");
            }
        } else {
            builder.mccDescription("Unknown Category");
        }

        // Get beneficial owners with their screening results
        List<BeneficialOwner> owners = beneficialOwnerRepository.findByMerchant_MerchantId(id);
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
     * Create merchant entity from request
     */
    private Merchant createMerchantFromRequest(MerchantOnboardingRequest request) {
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
                .psp(request.getPspId() != null
                        ? com.posgateway.aml.entity.psp.Psp.builder().pspId(request.getPspId()).build()
                        : null) // Temporary: Trust ID from request or Context. Ideally Context.
                // Better approach: User context = SecurityContextHolder... get PSP.
                // We will rely on the Service method to set the PSP from context before calling
                // this helper or pass it in.
                .beneficialOwners(new ArrayList<>())
                .build();

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

            merchant.getBeneficialOwners().add(owner);
        }

        merchant.setKycStatus("PENDING");
        merchant.setContractStatus("NO_CONTRACT");
        merchant.setDailyLimit(java.math.BigDecimal.valueOf(10000)); // Default limit

        return merchant;
    }

    /**
     * Calculate risk score (simplified version)
     * In full implementation, this would use Risk Rules Engine
     */
    private int calculateRiskScore(Merchant merchant, ScreeningResult merchantResult,
            List<Map<String, Object>> ownerResults) {
        int score = 0;

        // Sanctions match: +50 points
        if (merchantResult.hasMatches()) {
            score += 50;
        }

        // Owner sanctions/PEP: +30 points each
        for (Map<String, Object> ownerData : ownerResults) {
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

        return Math.min(score, 100); // Cap at 100
    }

    /**
     * Make onboarding decision based on risk score
     */
    private String makeDecision(int riskScore, ScreeningResult merchantResult, List<Map<String, Object>> ownerResults) {
        if (riskScore >= rejectThreshold) {
            return "REJECT";
        } else if (riskScore >= reviewThreshold) {
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
                // .merchant(merchant) // Removed as ComplianceCase entity changed structure
                // .caseType("ONBOARDING") // Removed
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
    private void createAuditTrail(Merchant merchant, String decision, int riskScore, ScreeningResult screeningResult) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("decision", decision);
        evidence.put("riskScore", riskScore);
        evidence.put("screeningResult", screeningResult);

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
            String decision, Long complianceCaseId) {
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
                .decisionReason("Automated screening - Risk score: " + riskScore)
                .merchantScreeningResult(merchantResult)
                .beneficialOwnerResults(ownerDetails)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .complianceCaseId(complianceCaseId)
                .screenedAt(LocalDateTime.now())
                .screeningProvider(merchantResult.getScreeningProvider())
                .country(merchant.getCountry())
                .kycStatus(merchant.getKycStatus())
                .contractStatus(merchant.getContractStatus())
                .dailyLimit(merchant.getDailyLimit())
                .currentUsage(merchant.getCurrentUsage())
                .riskLevel(riskLevel)
                .riskLevel(riskLevel)
                .mccDescription(mccMappingService.getDescription(merchant.getMcc()))
                .krs(merchant.getKrs())
                .cra(merchant.getCra())
                .build();
    }
}
