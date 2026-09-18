package com.posgateway.aml.controller.compliance;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.posgateway.aml.service.analytics.CorporateStructureService;
import com.posgateway.aml.service.analytics.CorporateStructureService.CorporateGraph;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.security.PspIsolationService;

// @Slf4j removed
// @RequiredArgsConstructor removed
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/compliance/kyc")
public class KycReportController {

    private static final Logger log = LoggerFactory.getLogger(KycReportController.class);

    private final CorporateStructureService corporateStructureService;
    private final MerchantRepository merchantRepository;
    private final PspIsolationService isolationService;

    public KycReportController(CorporateStructureService corporateStructureService,
                               MerchantRepository merchantRepository,
                               PspIsolationService isolationService) {
        this.corporateStructureService = corporateStructureService;
        this.merchantRepository = merchantRepository;
        this.isolationService = isolationService;
    }


    /**
     * Get Corporate Structure Graph
     * Returns nodes and edges representing UBOs and related companies
     */
    @GetMapping("/corporate-graph/{merchantId}")
    public ResponseEntity<CorporateGraph> getCorporateGraph(@PathVariable Long merchantId) {
        log.info("Requesting Corporate Graph for Merchant ID: {}", merchantId);
        try {
            Merchant merchant = merchantRepository.findById(merchantId)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
            User user = isolationService.getCurrentUser();
            if (user == null) throw new AccessDeniedException("Authenticated user is required");
            Long allowedPspId = null;
            if (!isolationService.isPlatformAdministrator(user)) {
                if (user.getPsp() == null || merchant.getPsp() == null
                        || !user.getPsp().getPspId().equals(merchant.getPsp().getPspId())) {
                    throw new AccessDeniedException("Cannot access another PSP's corporate graph");
                }
                allowedPspId = user.getPsp().getPspId();
            }
            CorporateGraph graph = corporateStructureService.buildCorporateGraph(merchantId, allowedPspId);
            return ResponseEntity.ok(graph);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Merchant not found: {}", merchantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating graph", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
