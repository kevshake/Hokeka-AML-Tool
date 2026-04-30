package com.posgateway.aml.controller.risk;

import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Customer Risk Controller
 * Provides endpoints for customer risk profiling
 */
@RestController
@RequestMapping("/risk/customer")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'INVESTIGATOR')")
public class CustomerRiskController {

    private final CustomerRiskProfilingService riskProfilingService;

    @Autowired
    public CustomerRiskController(CustomerRiskProfilingService riskProfilingService) {
        this.riskProfilingService = riskProfilingService;
    }

    @GetMapping("/{merchantId}/rating")
    public ResponseEntity<CustomerRiskProfilingService.CustomerRiskRating> getRiskRating(
            @PathVariable String merchantId) {
        return ResponseEntity.ok(riskProfilingService.calculateRiskRating(merchantId));
    }

    @GetMapping("/{merchantId}/edd-required")
    public ResponseEntity<Boolean> isEddRequired(@PathVariable String merchantId) {
        return ResponseEntity.ok(riskProfilingService.isEddRequired(merchantId));
    }

    @GetMapping("/pep-score")
    public ResponseEntity<Double> calculatePepScore(
            @RequestParam boolean isPep,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(riskProfilingService.calculatePepRiskScore(isPep, country));
    }

    @GetMapping("/geographic-risk")
    public ResponseEntity<Double> getGeographicRiskScore(@RequestParam String country) {
        return ResponseEntity.ok(riskProfilingService.calculateGeographicRiskScore(country));
    }
}

