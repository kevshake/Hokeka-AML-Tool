package com.posgateway.aml.controller.monitoring;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.monitoring.G2ContentScanEvent;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.monitoring.ContentMonitoringService;
import com.posgateway.aml.service.monitoring.ContentMonitoringService.G2ScanResult;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * G2-style merchant website content monitoring (transaction-laundering keyword scan).
 * Scheduled scans run via {@link ContentMonitoringService#performContentMonitoring()};
 * this controller exposes manual on-demand scans for the dashboard.
 */
@RestController
@RequestMapping("/monitoring/g2")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','PSP_ADMIN','INVESTIGATOR','SCREENING_ANALYST')")
public class ContentMonitoringController {

    private final ContentMonitoringService contentMonitoringService;
    private final MerchantRepository merchantRepository;
    private final PspIsolationService isolationService;

    public ContentMonitoringController(ContentMonitoringService contentMonitoringService,
                                       MerchantRepository merchantRepository,
                                       PspIsolationService isolationService) {
        this.contentMonitoringService = contentMonitoringService;
        this.merchantRepository = merchantRepository;
        this.isolationService = isolationService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "enabled", contentMonitoringService.isEnabled(),
                "provider", "G2_CONTENT_MONITORING",
                "description", "Keyword scan of merchant websites for undeclared high-risk business lines",
                "transactionLaunderingRules", contentMonitoringService.transactionLaunderingRuleCodes(),
                "envKeys", "G2_MONITORING_ENABLED"));
    }

    @GetMapping("/merchants/{merchantId}/scans")
    public ResponseEntity<List<G2ContentScanEvent>> merchantScans(@PathVariable Long merchantId,
                                                                  @RequestParam(defaultValue = "20") int limit) {
        requireMerchantAccess(merchantId);
        int capped = Math.min(Math.max(limit, 1), 100);
        return ResponseEntity.ok(contentMonitoringService.recentScansForMerchant(merchantId, capped));
    }

    @PostMapping("/merchants/{merchantId}/scan")
    public ResponseEntity<G2ScanResult> scanMerchant(@PathVariable Long merchantId) {
        Merchant merchant = requireMerchantAccess(merchantId);
        User user = isolationService.getCurrentUser();
        String actor = user != null ? user.getUsername() : "UNKNOWN";
        return ResponseEntity.ok(contentMonitoringService.scanMerchantWebsite(merchant, actor));
    }

    private Merchant requireMerchantAccess(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        User user = isolationService.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        if (!isolationService.isPlatformAdministrator(user)
                && (user.getPsp() == null || merchant.getPsp() == null
                || !user.getPsp().getPspId().equals(merchant.getPsp().getPspId()))) {
            throw new AccessDeniedException("Cannot access another PSP's merchant");
        }
        return merchant;
    }
}
