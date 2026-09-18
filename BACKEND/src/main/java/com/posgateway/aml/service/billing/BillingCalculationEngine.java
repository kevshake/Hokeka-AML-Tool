package com.posgateway.aml.service.billing;

import com.posgateway.aml.entity.billing.BillingCalculation;
import com.posgateway.aml.entity.billing.CostMetrics;
import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.BillingCalculationRepository;
import com.posgateway.aml.repository.CostMetricsRepository;
import com.posgateway.aml.repository.CurrencyRateRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Billing Calculation Engine
 * Core service for calculating bills based on database metrics.
 * Runs separately from transaction processing (decoupled architecture).
 */
@Service
public class BillingCalculationEngine {

    private static final Logger log = LoggerFactory.getLogger(BillingCalculationEngine.class);

    private final PricingTierRepository pricingTierRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CostMetricsRepository costMetricsRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final BillingCalculationRepository billingCalculationRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;

    public BillingCalculationEngine(
            PricingTierRepository pricingTierRepository,
            SubscriptionRepository subscriptionRepository,
            CostMetricsRepository costMetricsRepository,
            CurrencyRateRepository currencyRateRepository,
            BillingCalculationRepository billingCalculationRepository,
            ApiUsageLogRepository apiUsageLogRepository) {
        this.pricingTierRepository = pricingTierRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.costMetricsRepository = costMetricsRepository;
        this.currencyRateRepository = currencyRateRepository;
        this.billingCalculationRepository = billingCalculationRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
    }

    /**
     * Calculate monthly bill for a PSP based on database metrics.
     * Called by scheduled job, NOT during transactions.
     */
    @Transactional
    public BillingCalculation calculateMonthlyBill(Long pspId, YearMonth period) {
        log.info("Calculating monthly bill for PSP {} for period {}", pspId, period);

        // 1. Get subscription and tier from DB
        Subscription subscription = subscriptionRepository.findActiveByPspId(pspId)
                .orElseThrow(() -> new IllegalArgumentException("No active subscription for PSP: " + pspId));
        PricingTier tier = subscription.getPricingTier();

        // 2. Count usage from event store (ApiUsageLog)
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        LocalDateTime startDateTime = periodStart.atStartOfDay();
        LocalDateTime endDateTime = periodEnd.atTime(23, 59, 59);

        int checkCount = apiUsageLogRepository.countBillableByPspAndPeriod(
                pspId, startDateTime, endDateTime);
        log.debug("PSP {} had {} billable checks in period {}", pspId, checkCount, period);

        // 3. Get cost metrics from DB
        CostMetrics metrics = costMetricsRepository.findLatest()
                .orElse(createDefaultCostMetrics());

        // 4. Calculate subscription fee
        BigDecimal subscriptionFee = tier.getMonthlyFeeUsd() != null
                ? tier.getMonthlyFeeUsd()
                : BigDecimal.ZERO;

        // 5. Calculate usage with volume discounts
        int billableChecks = Math.max(0,
                checkCount - (tier.getIncludedChecks() != null ? tier.getIncludedChecks() : 0));
        BigDecimal baseUsageCost = tier.getPerCheckPriceUsd()
                .multiply(BigDecimal.valueOf(billableChecks));

        BigDecimal discountPercentage = tier.getApplicableDiscount(billableChecks);
        BigDecimal volumeDiscountAmount = baseUsageCost.multiply(discountPercentage);
        BigDecimal totalUsageCost = baseUsageCost.subtract(volumeDiscountAmount);

        // 6. Apply custom discount if any
        if (subscription.getDiscountPercentage() != null &&
                subscription.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal customDiscount = totalUsageCost.multiply(
                    subscription.getDiscountPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            totalUsageCost = totalUsageCost.subtract(customDiscount);
        }

        // 7. Calculate total before minimum
        BigDecimal totalBeforeMin = subscriptionFee.add(totalUsageCost);

        // 8. Apply monthly minimum
        BigDecimal minimumAdjustment = BigDecimal.ZERO;
        BigDecimal monthlyMin = tier.getMonthlyMinimumUsd() != null
                ? tier.getMonthlyMinimumUsd()
                : BigDecimal.ZERO;

        if (totalBeforeMin.compareTo(monthlyMin) < 0) {
            minimumAdjustment = monthlyMin.subtract(totalBeforeMin);
        }
        BigDecimal totalUsd = totalBeforeMin.add(minimumAdjustment);

        // 9. Convert currency if needed
        String currency = subscription.getBillingCurrency();
        BigDecimal totalAmount = totalUsd;
        if (!"USD".equals(currency)) {
            totalAmount = convertCurrency(totalUsd, "USD", currency);
        }

        // 10. Create calculation record
        Map<String, Object> costSnapshot = new HashMap<>();
        costSnapshot.put("fixedCosts", metrics.getFixedCostsMonthly());
        costSnapshot.put("variableCost", metrics.getVariableCostPerCheck());
        costSnapshot.put("targetMargin", metrics.getTargetMargin());

        Map<String, Object> details = new HashMap<>();
        details.put("billableChecks", billableChecks);
        details.put("includedChecks", tier.getIncludedChecks());
        details.put("discountPercentage", discountPercentage);

        BillingCalculation calculation = BillingCalculation.builder()
                .pspId(pspId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .tierCode(tier.getTierCode())
                .subscriptionFee(subscriptionFee)
                .checkCount(checkCount)
                .baseUsageCost(baseUsageCost)
                .volumeDiscountAmount(volumeDiscountAmount)
                .totalUsageCost(totalUsageCost)
                .minimumAdjustment(minimumAdjustment)
                .totalAmount(totalAmount)
                .currency(currency)
                .costMetricsSnapshot(costSnapshot)
                .calculationDetails(details)
                .build();

        BillingCalculation saved = billingCalculationRepository.save(calculation);
        log.info("Calculated bill for PSP {}: {} {}", pspId, totalAmount, currency);

        return saved;
    }

    /**
     * Estimate monthly cost for given tier and expected checks
     */
    public Map<String, Object> estimateCost(String tierCode, int expectedChecks, String currency) {
        PricingTier tier = pricingTierRepository.findByTierCode(tierCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier: " + tierCode));

        BigDecimal subscriptionFee = tier.getMonthlyFeeUsd() != null
                ? tier.getMonthlyFeeUsd()
                : BigDecimal.ZERO;

        int billableChecks = Math.max(0,
                expectedChecks - (tier.getIncludedChecks() != null ? tier.getIncludedChecks() : 0));
        BigDecimal baseUsage = tier.getPerCheckPriceUsd().multiply(BigDecimal.valueOf(billableChecks));
        BigDecimal discount = tier.getApplicableDiscount(billableChecks);
        BigDecimal discountAmount = baseUsage.multiply(discount);
        BigDecimal usageCost = baseUsage.subtract(discountAmount);
        BigDecimal total = subscriptionFee.add(usageCost);

        // Apply minimum
        BigDecimal minimum = tier.getMonthlyMinimumUsd() != null ? tier.getMonthlyMinimumUsd() : BigDecimal.ZERO;
        if (total.compareTo(minimum) < 0) {
            total = minimum;
        }

        // Convert currency
        if (currency != null && !"USD".equals(currency)) {
            total = convertCurrency(total, "USD", currency);
            subscriptionFee = convertCurrency(subscriptionFee, "USD", currency);
            usageCost = convertCurrency(usageCost, "USD", currency);
        } else {
            currency = "USD";
        }

        Map<String, Object> estimate = new HashMap<>();
        estimate.put("tierCode", tierCode);
        estimate.put("tierName", tier.getTierName());
        estimate.put("expectedChecks", expectedChecks);
        estimate.put("billableChecks", billableChecks);
        estimate.put("subscriptionFee", subscriptionFee);
        estimate.put("usageCost", usageCost);
        estimate.put("discountApplied",
                discount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%");
        estimate.put("estimatedTotal", total);
        estimate.put("currency", currency);
        estimate.put("effectivePerCheck", billableChecks > 0
                ? usageCost.divide(BigDecimal.valueOf(billableChecks), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return estimate;
    }

    /**
     * Calculate break-even point using stored cost metrics
     */
    public Map<String, Object> calculateBreakEven(String tierCode) {
        CostMetrics metrics = costMetricsRepository.findLatest()
                .orElse(createDefaultCostMetrics());
        PricingTier tier = pricingTierRepository.findByTierCode(tierCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier: " + tierCode));

        BigDecimal pricePerCheck = tier.getPerCheckPriceUsd();
        BigDecimal variableCost = metrics.getTotalVariableCost();
        BigDecimal contribution = pricePerCheck.subtract(variableCost);

        int breakEvenChecks = 0;
        if (contribution.compareTo(BigDecimal.ZERO) > 0 && metrics.getFixedCostsMonthly() != null) {
            breakEvenChecks = metrics.getFixedCostsMonthly()
                    .divide(contribution, 0, RoundingMode.CEILING).intValue();
        }

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("tierCode", tierCode);
        analysis.put("pricePerCheck", pricePerCheck);
        analysis.put("variableCostPerCheck", variableCost);
        analysis.put("contributionMargin", contribution);
        analysis.put("fixedCostsMonthly", metrics.getFixedCostsMonthly());
        analysis.put("breakEvenChecks", breakEvenChecks);
        analysis.put("targetMargin", metrics.getTargetMargin());
        analysis.put("minimumViablePrice", metrics.getMinimumViablePrice());

        return analysis;
    }

    /**
     * Convert amount between currencies using stored rates
     */
    public BigDecimal convertCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        // Convert to USD first
        BigDecimal usdAmount = amount;
        if (!"USD".equals(fromCurrency)) {
            Optional<CurrencyRate> fromRate = currencyRateRepository.findById(fromCurrency);
            if (fromRate.isPresent()) {
                usdAmount = fromRate.get().toUsd(amount);
            }
        }

        // Convert from USD to target
        if ("USD".equals(toCurrency)) {
            return usdAmount.setScale(2, RoundingMode.HALF_UP);
        }

        Optional<CurrencyRate> toRate = currencyRateRepository.findById(toCurrency);
        if (toRate.isPresent()) {
            return toRate.get().fromUsd(usdAmount);
        }

        return usdAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get current cost metrics
     */
    public CostMetrics getCurrentCostMetrics() {
        return costMetricsRepository.findLatest().orElse(createDefaultCostMetrics());
    }

    private CostMetrics createDefaultCostMetrics() {
        CostMetrics defaults = new CostMetrics();
        defaults.setMetricDate(LocalDate.now());
        defaults.setFixedCostsMonthly(BigDecimal.valueOf(10000));
        defaults.setVariableCostPerCheck(BigDecimal.valueOf(0.05));
        defaults.setManualReviewCost(BigDecimal.valueOf(0.02));
        defaults.setDataFeedCost(BigDecimal.valueOf(0.01));
        defaults.setTargetMargin(BigDecimal.valueOf(0.65));
        return defaults;
    }
}
