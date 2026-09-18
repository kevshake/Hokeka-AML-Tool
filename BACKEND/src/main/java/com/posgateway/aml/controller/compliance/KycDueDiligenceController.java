package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.dto.request.BeneficialOwnerRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.compliance.AuditService;
import com.posgateway.aml.dto.corporate.CorporateIntelligenceDtos.CheckResponse;
import com.posgateway.aml.service.corporate.CorporateIntelligenceService;
import com.posgateway.aml.service.edd.EnhancedDueDiligenceService;
import com.posgateway.aml.service.kyc.BeneficialOwnershipService;
import com.posgateway.aml.service.kyc.KycCompletenessService;
import com.posgateway.aml.service.kyc.RiskBasedCddService;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/compliance/kyc/merchants/{merchantId}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN','PSP_ANALYST','PSP_USER','SCREENING_ANALYST','INVESTIGATOR','AUDITOR','VIEWER')")
public class KycDueDiligenceController {

    private final MerchantRepository merchantRepository;
    private final PspIsolationService isolationService;
    private final BeneficialOwnershipService ownershipService;
    private final EnhancedDueDiligenceService eddService;
    private final RiskBasedCddService cddService;
    private final KycCompletenessService completenessService;
    private final AuditService auditService;
    private final CorporateIntelligenceService corporateIntelligenceService;

    public KycDueDiligenceController(MerchantRepository merchantRepository,
                                     PspIsolationService isolationService,
                                     BeneficialOwnershipService ownershipService,
                                     EnhancedDueDiligenceService eddService,
                                     RiskBasedCddService cddService,
                                     KycCompletenessService completenessService,
                                     AuditService auditService,
                                     CorporateIntelligenceService corporateIntelligenceService) {
        this.merchantRepository = merchantRepository;
        this.isolationService = isolationService;
        this.ownershipService = ownershipService;
        this.eddService = eddService;
        this.cddService = cddService;
        this.completenessService = completenessService;
        this.auditService = auditService;
        this.corporateIntelligenceService = corporateIntelligenceService;
    }

    @GetMapping("/overview")
    public DueDiligenceOverview overview(@PathVariable Long merchantId) {
        Merchant merchant = requireMerchantAccess(merchantId);
        return new DueDiligenceOverview(merchantId, merchant.getLegalName(),
                cddService.assessCustomerRisk(merchant),
                completenessService.calculateCompletenessScore(merchantId),
                ownership(ownershipService.getOwnershipStructure(merchantId)),
                eddService.getEddStatus(merchantId));
    }

    @GetMapping("/beneficial-owners")
    public OwnershipResponse beneficialOwners(@PathVariable Long merchantId) {
        requireMerchantAccess(merchantId);
        return ownership(ownershipService.getOwnershipStructure(merchantId));
    }

    @PostMapping("/beneficial-owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN')")
    public ResponseEntity<BeneficialOwnerResponse> createOwner(@PathVariable Long merchantId,
                                                               @Valid @RequestBody BeneficialOwnerRequest request) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        BeneficialOwner owner = ownershipService.create(merchantId, request);
        audit("UBO_CREATED", merchantId, user, Map.of("ownerId", owner.getOwnerId(),
                "ownershipPercentage", owner.getOwnershipPercentage()));
        return ResponseEntity.ok(owner(owner));
    }

    @PutMapping("/beneficial-owners/{ownerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN')")
    public BeneficialOwnerResponse updateOwner(@PathVariable Long merchantId, @PathVariable Long ownerId,
                                               @Valid @RequestBody BeneficialOwnerRequest request) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        BeneficialOwner owner = ownershipService.update(merchantId, ownerId, request);
        audit("UBO_UPDATED", merchantId, user, Map.of("ownerId", ownerId,
                "ownershipPercentage", owner.getOwnershipPercentage()));
        return owner(owner);
    }

    @DeleteMapping("/beneficial-owners/{ownerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN')")
    public ResponseEntity<Void> deleteOwner(@PathVariable Long merchantId, @PathVariable Long ownerId) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        ownershipService.delete(merchantId, ownerId);
        audit("UBO_DELETED", merchantId, user, Map.of("ownerId", ownerId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/beneficial-owners/{ownerId}/screen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN','SCREENING_ANALYST')")
    public OwnerScreeningResponse screenOwner(@PathVariable Long merchantId, @PathVariable Long ownerId) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        ScreeningResult result = ownershipService.screen(merchantId, ownerId);
        BeneficialOwner owner = ownershipService.requireOwner(merchantId, ownerId);
        audit("UBO_SCREENED", merchantId, user, Map.of("ownerId", ownerId, "status", result.getStatus().name(),
                "provider", result.getScreeningProvider() == null ? "" : result.getScreeningProvider(),
                "matchCount", result.getMatchCount() == null ? 0 : result.getMatchCount()));
        return new OwnerScreeningResponse(owner(owner), result);
    }

    @GetMapping("/corporate-intelligence")
    public List<CheckResponse> corporateIntelligence(@PathVariable Long merchantId) {
        requireMerchantAccess(merchantId);
        return corporateIntelligenceService.history(merchantId);
    }

    @PostMapping("/corporate-intelligence/check")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN','SCREENING_ANALYST')")
    public CheckResponse runCorporateIntelligence(@PathVariable Long merchantId) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        CheckResponse response = corporateIntelligenceService.runManualCheck(merchantId);
        audit("CORPORATE_INTELLIGENCE_CHECKED", merchantId, user, Map.of(
                "checkId", response.id(),
                "status", response.status().name(),
                "riskScore", response.riskScore(),
                "evidenceHash", response.evidenceHash()));
        return response;
    }

    @GetMapping("/edd")
    public EnhancedDueDiligenceService.EddStatus edd(@PathVariable Long merchantId) {
        requireMerchantAccess(merchantId);
        return eddService.getEddStatus(merchantId);
    }

    @PostMapping("/edd")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN')")
    public EnhancedDueDiligenceService.EddStatus initiateEdd(@PathVariable Long merchantId,
                                                             @RequestBody(required = false) InitiateEddRequest request) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        InitiateEddRequest body = request == null ? new InitiateEddRequest(false, null) : request;
        EnhancedDueDiligenceService.EddStatus status = eddService.initiateEdd(
                merchantId, body.siteVisitRequired(), user.getUsername(), body.notes());
        audit("EDD_INITIATED", merchantId, user, Map.of("siteVisitRequired", body.siteVisitRequired()));
        return status;
    }

    @PutMapping("/edd/items/{itemCode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN')")
    public EnhancedDueDiligenceService.EddStatus updateEddItem(@PathVariable Long merchantId,
                                                               @PathVariable String itemCode,
                                                               @RequestBody UpdateEddItemRequest request) {
        requireMerchantAccess(merchantId);
        User user = requireUser();
        EnhancedDueDiligenceService.EddStatus status = eddService.updateItemStatus(
                merchantId, itemCode, request.completed(), user.getUsername(), request.notes());
        audit("EDD_ITEM_UPDATED", merchantId, user, Map.of("itemCode", itemCode,
                "completed", request.completed()));
        return status;
    }

    private Merchant requireMerchantAccess(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        User user = requireUser();
        if (!isolationService.isPlatformAdministrator(user)
                && (user.getPsp() == null || merchant.getPsp() == null
                || !user.getPsp().getPspId().equals(merchant.getPsp().getPspId()))) {
            throw new AccessDeniedException("Cannot access another PSP's KYC records");
        }
        return merchant;
    }

    private User requireUser() {
        User user = isolationService.getCurrentUser();
        if (user == null) throw new AccessDeniedException("Authenticated user is required");
        return user;
    }

    private void audit(String action, Long merchantId, User user, Map<String, Object> evidence) {
        auditService.logAction(action, merchantId, user.getUsername(), action, evidence);
    }

    private OwnershipResponse ownership(BeneficialOwnershipService.OwnershipStructure structure) {
        return new OwnershipResponse(structure.merchantId(), structure.totalOwnershipPercentage(), structure.complete(),
                structure.beneficialOwners().stream().map(this::owner).toList(),
                structure.ultimateBeneficialOwners().stream().map(BeneficialOwner::getOwnerId).toList());
    }

    private BeneficialOwnerResponse owner(BeneficialOwner owner) {
        return new BeneficialOwnerResponse(owner.getOwnerId(), owner.getFullName(), owner.getDateOfBirth(),
                owner.getNationality(), owner.getCountryOfResidence(), ending(owner.getPassportNumber()),
                ending(owner.getNationalId()), owner.getOwnershipPercentage(), Boolean.TRUE.equals(owner.getIsPep()),
                Boolean.TRUE.equals(owner.getIsSanctioned()), owner.getLastScreenedAt(), owner.getCreatedAt(),
                owner.getUpdatedAt());
    }

    private String ending(String value) {
        if (value == null || value.isBlank()) return null;
        return value.substring(Math.max(0, value.length() - 4));
    }

    public record DueDiligenceOverview(Long merchantId, String merchantName,
                                       RiskBasedCddService.CddAssessment cdd,
                                       KycCompletenessService.CompletenessScore completeness,
                                       OwnershipResponse ownership,
                                       EnhancedDueDiligenceService.EddStatus edd) {}
    public record OwnershipResponse(Long merchantId, int totalOwnershipPercentage, boolean complete,
                                    List<BeneficialOwnerResponse> owners, List<Long> ultimateBeneficialOwnerIds) {}
    public record BeneficialOwnerResponse(Long id, String fullName, LocalDate dateOfBirth, String nationality,
                                          String countryOfResidence, String passportEnding, String nationalIdEnding,
                                          Integer ownershipPercentage, boolean pep, boolean sanctioned,
                                          LocalDateTime lastScreenedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record OwnerScreeningResponse(BeneficialOwnerResponse owner, ScreeningResult result) {}
    public record InitiateEddRequest(boolean siteVisitRequired, String notes) {}
    public record UpdateEddItemRequest(boolean completed, String notes) {}
}
