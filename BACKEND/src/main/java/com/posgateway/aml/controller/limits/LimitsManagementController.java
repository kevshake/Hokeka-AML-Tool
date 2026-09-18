package com.posgateway.aml.controller.limits;

import com.posgateway.aml.entity.limits.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.limits.LimitsManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Controller for Limits & AML Management
 */
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/limits")
public class LimitsManagementController {

    private final LimitsManagementService limitsService;
    private final UserRepository userRepository;

    public LimitsManagementController(LimitsManagementService limitsService, UserRepository userRepository) {
        this.limitsService = limitsService;
        this.userRepository = userRepository;
    }

    private User requireCurrentUser() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    // Dashboard Stats
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        User user = requireCurrentUser();
        Long pspId = user.getPsp() == null ? null : user.getPsp().getPspId();
        return ResponseEntity.ok(limitsService.getDashboardStats(pspId));
    }

    // Merchant Limits
    @GetMapping("/merchant")
    public ResponseEntity<Page<MerchantTransactionLimit>> getAllMerchantLimits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(limitsService.getAllMerchantLimits(pageable));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<MerchantTransactionLimit> getMerchantLimit(@PathVariable Long merchantId) {
        MerchantTransactionLimit limit = limitsService.getMerchantLimit(merchantId);
        if (limit == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(limit);
    }

    @PostMapping("/merchant/{merchantId}")
    public ResponseEntity<MerchantTransactionLimit> createOrUpdateMerchantLimit(
            @PathVariable Long merchantId,
            @RequestBody MerchantTransactionLimit limit) {
        return ResponseEntity.ok(limitsService.createOrUpdateMerchantLimit(merchantId, limit, requireCurrentUser().getId()));
    }

    // Global Limits
    @GetMapping("/global")
    public ResponseEntity<?> getAllGlobalLimits() {
        return ResponseEntity.ok(limitsService.getAllGlobalLimits());
    }

    @PostMapping("/global")
    public ResponseEntity<GlobalLimit> createGlobalLimit(
            @RequestBody GlobalLimit limit) {
        return ResponseEntity.ok(limitsService.createGlobalLimit(limit, requireCurrentUser().getId()));
    }

    @PutMapping("/global/{id}")
    public ResponseEntity<GlobalLimit> updateGlobalLimit(
            @PathVariable Long id,
            @RequestBody GlobalLimit limit) {
        return ResponseEntity.ok(limitsService.updateGlobalLimit(id, limit, requireCurrentUser().getId()));
    }

    @DeleteMapping("/global/{id}")
    public ResponseEntity<Void> deleteGlobalLimit(@PathVariable Long id) {
        limitsService.deleteGlobalLimit(id);
        return ResponseEntity.ok().build();
    }

    /**
     * AML limits — single endpoint the LimitsAml page POSTs to.
     * Body: { transactionLimit?: number, dailyLimit?: number }
     * Persists as two tenant-scoped GlobalLimit rows used by transaction enforcement.
     */
    @GetMapping("/aml")
    public ResponseEntity<Map<String, java.math.BigDecimal>> getAmlLimits() {
        return ResponseEntity.ok(limitsService.getAmlLimits());
    }

    @PostMapping("/aml")
    public ResponseEntity<?> saveAmlLimits(
            @RequestBody Map<String, Object> body) {
        User user = requireCurrentUser();
        Object txn = body.get("transactionLimit");
        Object daily = body.get("dailyLimit");
        java.util.List<GlobalLimit> saved = limitsService.upsertAmlLimits(
                txn == null ? null : new java.math.BigDecimal(txn.toString()),
                daily == null ? null : new java.math.BigDecimal(daily.toString()),
                user.getId());
        return ResponseEntity.ok(Map.of("success", true, "saved", saved.size()));
    }

    // Risk Thresholds
    @GetMapping("/risk-thresholds")
    public ResponseEntity<List<RiskThreshold>> getAllRiskThresholds() {
        return ResponseEntity.ok(limitsService.getAllRiskThresholds());
    }

    @PostMapping("/risk-thresholds")
    public ResponseEntity<RiskThreshold> createOrUpdateRiskThreshold(
            @RequestBody RiskThreshold threshold) {
        try {
            return ResponseEntity.ok(limitsService.createOrUpdateRiskThreshold(threshold, requireCurrentUser().getId()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    // Velocity Rules
    @GetMapping("/velocity-rules")
    public ResponseEntity<List<VelocityRule>> getAllVelocityRules() {
        return ResponseEntity.ok(limitsService.getAllVelocityRules());
    }

    @PostMapping("/velocity-rules")
    public ResponseEntity<VelocityRule> createVelocityRule(
            @RequestBody VelocityRule rule) {
        return ResponseEntity.ok(limitsService.createVelocityRule(rule, requireCurrentUser().getId()));
    }

    @PutMapping("/velocity-rules/{id}")
    public ResponseEntity<VelocityRule> updateVelocityRule(
            @PathVariable Long id,
            @RequestBody VelocityRule rule) {
        try {
            return ResponseEntity.ok(limitsService.updateVelocityRule(id, rule, requireCurrentUser().getId()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @DeleteMapping("/velocity-rules/{id}")
    public ResponseEntity<Void> deleteVelocityRule(
            @PathVariable Long id) {
        try {
            limitsService.deleteVelocityRule(id);
            return ResponseEntity.ok().build();
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    // Country Compliance
    @GetMapping("/country-compliance")
    public ResponseEntity<List<CountryComplianceRule>> getAllCountryComplianceRules() {
        return ResponseEntity.ok(limitsService.getAllCountryComplianceRules());
    }

    @PostMapping("/country-compliance")
    public ResponseEntity<CountryComplianceRule> createOrUpdateCountryCompliance(
            @RequestBody CountryComplianceRule rule) {
        return ResponseEntity.ok(limitsService.createOrUpdateCountryCompliance(rule, requireCurrentUser().getId()));
    }
}

