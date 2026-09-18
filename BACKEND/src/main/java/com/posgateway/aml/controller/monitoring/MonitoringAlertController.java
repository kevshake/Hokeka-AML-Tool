package com.posgateway.aml.controller.monitoring;

import com.posgateway.aml.entity.monitoring.MonitoringAlert;
import com.posgateway.aml.service.monitoring.MonitoringAlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Read API for periodic-rescreening monitoring alerts ({@code monitoring_alerts} table).
 */
@RestController
@RequestMapping("/monitoring/alerts")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST', 'PSP_ADMIN')")
public class MonitoringAlertController {

    private final MonitoringAlertService monitoringAlertService;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final com.posgateway.aml.service.security.PspIsolationService pspIsolationService;

    public MonitoringAlertController(MonitoringAlertService monitoringAlertService,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            com.posgateway.aml.service.security.PspIsolationService pspIsolationService) {
        this.monitoringAlertService = monitoringAlertService;
        this.merchantRepository = merchantRepository;
        this.pspIsolationService = pspIsolationService;
    }

    @GetMapping
    public ResponseEntity<Page<MonitoringAlert>> listAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return ResponseEntity.ok(
                monitoringAlertService.listForCurrentUser(PageRequest.of(Math.max(0, page), safeSize)));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        return ResponseEntity.ok(Map.of("unacknowledged", monitoringAlertService.countUnacknowledged()));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<List<MonitoringAlert>> listForMerchant(@PathVariable Long merchantId) {
        // Tenant isolation: only the merchant's own PSP (or a platform admin) may read its alerts.
        com.posgateway.aml.entity.merchant.Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Merchant not found"));
        pspIsolationService.validateMerchantAccess(merchant);
        return ResponseEntity.ok(monitoringAlertService.listForMerchant(merchantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MonitoringAlert> getAlert(@PathVariable Long id) {
        return monitoringAlertService.findAccessibleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'PSP_ADMIN')")
    public ResponseEntity<MonitoringAlert> acknowledge(@PathVariable Long id, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return monitoringAlertService.acknowledge(id, username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
