package com.posgateway.aml.controller;

import com.posgateway.aml.entity.billing.BillingCalculation;
import com.posgateway.aml.entity.billing.CostMetrics;
import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.repository.BillingCalculationRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.service.billing.BillingCalculationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pricing Controller
 * APIs for pricing, billing calculations, and cost estimates
 */
@RestController
@RequestMapping("/pricing")
public class PricingController {

    private static final Logger log = LoggerFactory.getLogger(PricingController.class);

    private final BillingCalculationEngine billingEngine;
    private final PricingTierRepository pricingTierRepository;
    private final BillingCalculationRepository billingCalculationRepository;

    public PricingController(
            BillingCalculationEngine billingEngine,
            PricingTierRepository pricingTierRepository,
            BillingCalculationRepository billingCalculationRepository) {
        this.billingEngine = billingEngine;
        this.pricingTierRepository = pricingTierRepository;
        this.billingCalculationRepository = billingCalculationRepository;
    }

    // ==================== Pricing Tiers ====================

    /**
     * Get all active pricing tiers
     */
    @GetMapping("/tiers")
    public ResponseEntity<List<Map<String, Object>>> getPricingTiers() {
        List<PricingTier> tiers = pricingTierRepository.findAllActive();

        List<Map<String, Object>> result = tiers.stream().map(tier -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tierCode", tier.getTierCode());
            map.put("tierName", tier.getTierName());
            map.put("monthlyFeeUsd", tier.getMonthlyFeeUsd());
            map.put("perCheckPriceUsd", tier.getPerCheckPriceUsd());
            map.put("monthlyMinimumUsd", tier.getMonthlyMinimumUsd());
            map.put("maxChecksPerMonth", tier.getMaxChecksPerMonth());
            map.put("includedChecks", tier.getIncludedChecks());
            map.put("volumeDiscounts", tier.getVolumeDiscounts());
            map.put("features", tier.getFeatures());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Get specific pricing tier
     */
    @GetMapping("/tiers/{tierCode}")
    public ResponseEntity<PricingTier> getPricingTier(@PathVariable String tierCode) {
        return pricingTierRepository.findByTierCode(tierCode.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Cost Estimation ====================

    /**
     * Estimate monthly cost for given tier and expected usage
     */
    @GetMapping("/estimate")
    public ResponseEntity<Map<String, Object>> estimateCost(
            @RequestParam String tier,
            @RequestParam int checks,
            @RequestParam(required = false, defaultValue = "USD") String currency) {
        try {
            Map<String, Object> estimate = billingEngine.estimateCost(tier.toUpperCase(), checks, currency);
            return ResponseEntity.ok(estimate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Compare costs across all tiers
     */
    @GetMapping("/compare")
    public ResponseEntity<List<Map<String, Object>>> compareTiers(
            @RequestParam int checks,
            @RequestParam(required = false, defaultValue = "USD") String currency) {
        List<PricingTier> tiers = pricingTierRepository.findAllPaidTiers();

        List<Map<String, Object>> comparisons = tiers.stream()
                .map(tier -> billingEngine.estimateCost(tier.getTierCode(), checks, currency))
                .collect(Collectors.toList());

        return ResponseEntity.ok(comparisons);
    }

    // ==================== Cost Metrics ====================

    /**
     * Get current cost metrics
     */
    @GetMapping("/metrics/current")
    public ResponseEntity<CostMetrics> getCurrentCostMetrics() {
        return ResponseEntity.ok(billingEngine.getCurrentCostMetrics());
    }

    /**
     * Calculate break-even analysis for a tier
     */
    @GetMapping("/metrics/break-even/{tierCode}")
    public ResponseEntity<Map<String, Object>> getBreakEvenAnalysis(@PathVariable String tierCode) {
        try {
            Map<String, Object> analysis = billingEngine.calculateBreakEven(tierCode.toUpperCase());
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== Billing Calculations ====================

    /**
     * Calculate bill for a PSP (admin use)
     */
    @PostMapping("/calculate")
    public ResponseEntity<BillingCalculation> calculateBill(
            @RequestParam Long pspId,
            @RequestParam String period) {
        try {
            YearMonth yearMonth = YearMonth.parse(period);
            BillingCalculation calculation = billingEngine.calculateMonthlyBill(pspId, yearMonth);
            return ResponseEntity.ok(calculation);
        } catch (Exception e) {
            log.error("Error calculating bill for PSP {}", pspId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get billing history for a PSP
     */
    @GetMapping("/history/{pspId}")
    public ResponseEntity<List<BillingCalculation>> getBillingHistory(@PathVariable Long pspId) {
        List<BillingCalculation> history = billingCalculationRepository.findByPspId(pspId);
        return ResponseEntity.ok(history);
    }

    // ==================== Currency ====================

    /**
     * Convert amount between currencies
     */
    @GetMapping("/currency/convert")
    public ResponseEntity<Map<String, Object>> convertCurrency(
            @RequestParam double amount,
            @RequestParam String from,
            @RequestParam String to) {
        java.math.BigDecimal converted = billingEngine.convertCurrency(
                java.math.BigDecimal.valueOf(amount),
                from.toUpperCase(),
                to.toUpperCase());

        return ResponseEntity.ok(Map.of(
                "originalAmount", amount,
                "originalCurrency", from.toUpperCase(),
                "convertedAmount", converted,
                "targetCurrency", to.toUpperCase()));
    }
}
