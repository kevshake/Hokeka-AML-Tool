package com.posgateway.aml.controller.limits;

import com.posgateway.aml.entity.limits.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.limits.LimitsManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for Limits & AML Management
 */
@RestController
@RequestMapping("/limits")
public class LimitsManagementController {

    private final LimitsManagementService limitsService;
    private final UserRepository userRepository;

    public LimitsManagementController(LimitsManagementService limitsService, UserRepository userRepository) {
        this.limitsService = limitsService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // Dashboard Stats
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        return ResponseEntity.ok(limitsService.getDashboardStats(pspId));
    }

    // Merchant Limits
    @GetMapping("/merchant")
    public ResponseEntity<List<MerchantTransactionLimit>> getAllMerchantLimits() {
        return ResponseEntity.ok(limitsService.getAllMerchantLimits());
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
            @RequestBody MerchantTransactionLimit limit,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername());
        return ResponseEntity.ok(limitsService.createOrUpdateMerchantLimit(merchantId, limit, userId));
    }

    // Global Limits
    @GetMapping("/global")
    public ResponseEntity<?> getAllGlobalLimits() {
        try {
            List<GlobalLimit> limits = limitsService.getAllGlobalLimits();
            return ResponseEntity.ok(limits != null ? limits : new java.util.ArrayList<>());
        } catch (Exception e) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LimitsManagementController.class);
            log.error("Error loading global limits: {}", e.getMessage(), e);
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", "Error loading transaction limits");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/global")
    public ResponseEntity<GlobalLimit> createGlobalLimit(
            @RequestBody GlobalLimit limit,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername());
        return ResponseEntity.ok(limitsService.createGlobalLimit(limit, userId));
    }

    @PutMapping("/global/{id}")
    public ResponseEntity<GlobalLimit> updateGlobalLimit(
            @PathVariable Long id,
            @RequestBody GlobalLimit limit,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername());
        return ResponseEntity.ok(limitsService.updateGlobalLimit(id, limit, userId));
    }

    @DeleteMapping("/global/{id}")
    public ResponseEntity<Void> deleteGlobalLimit(@PathVariable Long id) {
        limitsService.deleteGlobalLimit(id);
        return ResponseEntity.ok().build();
    }

    // Risk Thresholds
    @GetMapping("/risk-thresholds")
    public ResponseEntity<List<RiskThreshold>> getAllRiskThresholds() {
        return ResponseEntity.ok(limitsService.getAllRiskThresholds());
    }

    @PostMapping("/risk-thresholds")
    public ResponseEntity<RiskThreshold> createOrUpdateRiskThreshold(
            @RequestBody RiskThreshold threshold,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        try {
            Long userId = Long.parseLong(user.getUsername());
            return ResponseEntity.ok(limitsService.createOrUpdateRiskThreshold(threshold, userId));
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
            @RequestBody VelocityRule rule,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername());
        return ResponseEntity.ok(limitsService.createVelocityRule(rule, userId));
    }

    @PutMapping("/velocity-rules/{id}")
    public ResponseEntity<VelocityRule> updateVelocityRule(
            @PathVariable Long id,
            @RequestBody VelocityRule rule,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        try {
            Long userId = Long.parseLong(user.getUsername());
            return ResponseEntity.ok(limitsService.updateVelocityRule(id, rule, userId));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @DeleteMapping("/velocity-rules/{id}")
    public ResponseEntity<Void> deleteVelocityRule(
            @PathVariable Long id,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
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
            @RequestBody CountryComplianceRule rule,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername());
        return ResponseEntity.ok(limitsService.createOrUpdateCountryCompliance(rule, userId));
    }
}

