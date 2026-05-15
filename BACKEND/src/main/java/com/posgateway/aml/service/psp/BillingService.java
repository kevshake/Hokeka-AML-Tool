package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.InvoiceLineItem;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.BillingRateRepository;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.billing.BillingEmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BillingService.class);

    private final BillingRateRepository billingRateRepository;
    private final InvoiceRepository invoiceRepository;
    private final PspRepository pspRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final BillingEmailService billingEmailService;
    private final ObjectMapper objectMapper;

    public BillingService(BillingRateRepository billingRateRepository, InvoiceRepository invoiceRepository,
            PspRepository pspRepository, ApiUsageLogRepository apiUsageLogRepository,
            BillingEmailService billingEmailService, ObjectMapper objectMapper) {
        this.billingRateRepository = billingRateRepository;
        this.invoiceRepository = invoiceRepository;
        this.pspRepository = pspRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.billingEmailService = billingEmailService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<BillingRate> getEffectiveRate(Long pspId, String serviceType) {
        LocalDate today = LocalDate.now();
        // Try to find PSP specific rate first
        Optional<BillingRate> pspRate = billingRateRepository.findActiveRateForPsp(pspId, serviceType, today);
        if (pspRate.isPresent()) {
            return pspRate;
        }
        // Fallback to default
        return billingRateRepository.findDefaultRate(serviceType, today);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateUsageCost(Long pspId, String serviceType, int count) {
        Optional<BillingRate> rateOpt = getEffectiveRate(pspId, serviceType);
        if (rateOpt.isEmpty() || count <= 0) {
            return BigDecimal.ZERO;
        }

        BillingRate rate = rateOpt.get();
        if ("PER_REQUEST".equals(rate.getPricingModel()) && rate.getBaseRate() != null) {
            return rate.getBaseRate().multiply(BigDecimal.valueOf(count));
        } else if ("TIERED".equals(rate.getPricingModel())) {
            return calculateTieredPrice(rate, count);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Calculate cost using tiered pricing based on the rate's tier_config JSON.
     *
     * Expected JSON shape (stored as a Map under tier_config jsonb column):
     *   { "tiers": [
     *       { "upTo": 1000,  "rate": 0.50 },
     *       { "upTo": 10000, "rate": 0.40 },
     *       { "upTo": null,  "rate": 0.30 }   // null upTo = unlimited (catch-all)
     *     ]
     *   }
     *
     * The "upTo" value is a cumulative threshold — units between the previous
     * threshold and this one are billed at this tier's "rate". A null or absent
     * "upTo" on the final tier consumes all remaining units.
     *
     * Falls back to flat baseRate * count if the JSON is missing, malformed, or
     * the resulting tier list is empty.
     */
    BigDecimal calculateTieredPrice(BillingRate rate, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseRate = rate.getBaseRate();
        BigDecimal flatFallback = (baseRate != null)
                ? baseRate.multiply(BigDecimal.valueOf(count))
                : BigDecimal.ZERO;

        Map<String, Object> tierConfig = rate.getTierConfig();
        if (tierConfig == null || tierConfig.isEmpty()) {
            return flatFallback;
        }

        try {
            // tier_config may either be the tier array directly under "tiers",
            // or already be a List serialized as a Map (defensive — re-marshal).
            Object tiersObj = tierConfig.get("tiers");
            List<Map<String, Object>> tiers;
            if (tiersObj instanceof List<?>) {
                String reserialized = objectMapper.writeValueAsString(tiersObj);
                tiers = objectMapper.readValue(reserialized,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.warn("tier_config for rate {} has no 'tiers' array — falling back to flat rate",
                        rate.getRateId());
                return flatFallback;
            }

            if (tiers == null || tiers.isEmpty()) {
                return flatFallback;
            }

            BigDecimal total = BigDecimal.ZERO;
            long remaining = count;
            long processed = 0;

            for (Map<String, Object> tier : tiers) {
                if (remaining <= 0) break;

                Object upToObj = tier.get("upTo");
                Object rateObj = tier.get("rate");
                if (rateObj == null) {
                    continue;
                }
                BigDecimal tierRate = new BigDecimal(rateObj.toString());

                Long upTo = (upToObj == null) ? null : ((Number) upToObj).longValue();

                long applicable;
                if (upTo == null) {
                    applicable = remaining;
                } else {
                    long capacity = upTo - processed;
                    if (capacity <= 0) {
                        continue;
                    }
                    applicable = Math.min(remaining, capacity);
                }
                if (applicable <= 0) continue;

                total = total.add(tierRate.multiply(BigDecimal.valueOf(applicable)));
                remaining -= applicable;
                processed += applicable;
            }

            // If remaining units were not absorbed (no catch-all tier), bill them
            // at the last tier's rate to avoid silently under-billing.
            if (remaining > 0) {
                Map<String, Object> lastTier = tiers.get(tiers.size() - 1);
                Object lastRateObj = lastTier.get("rate");
                if (lastRateObj != null) {
                    BigDecimal lastRate = new BigDecimal(lastRateObj.toString());
                    total = total.add(lastRate.multiply(BigDecimal.valueOf(remaining)));
                } else if (baseRate != null) {
                    total = total.add(baseRate.multiply(BigDecimal.valueOf(remaining)));
                }
            }

            return total;
        } catch (Exception e) {
            log.warn("Failed to parse tier_config for rate {} — falling back to flat rate: {}",
                    rate.getRateId(), e.getMessage());
            return flatFallback;
        }
    }

    @Transactional
    public Invoice generateMonthlyInvoice(Long pspId, LocalDate periodStart) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        LocalDate periodEnd = periodStart.with(TemporalAdjusters.lastDayOfMonth());
        LocalDateTime startDateTime = periodStart.atStartOfDay();
        LocalDateTime endDateTime = periodEnd.atTime(23, 59, 59);

        log.info("Generating invoice for PSP {} for period {} to {}", psp.getPspCode(), periodStart, periodEnd);

        // Get usage summary
        List<Object[]> usageSummary = apiUsageLogRepository.getUsageSummaryByService(pspId, startDateTime, endDateTime);

        Invoice invoice = Invoice.builder()
                .psp(psp)
                .invoiceNumber("INV-" + psp.getPspCode() + "-" + periodStart.toString().replace("-", "") + "-"
                        + UUID.randomUUID().toString().substring(0, 4))
                .billingPeriodStart(periodStart)
                .billingPeriodEnd(periodEnd)
                .status("DRAFT")
                .dueDate(periodEnd.plusDays(psp.getPaymentTerms()))
                .currency(psp.getCurrency())
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        int lineNum = 1;

        for (Object[] row : usageSummary) {
            String serviceType = (String) row[0];
            Long count = (Long) row[1];
            if (count == null) count = 0L;

            // Recompute the line total against the current effective rate so the
            // invoice reflects active tiered pricing (not whatever per-request
            // cost was stamped on each log row at call time).
            BigDecimal lineTotal = calculateUsageCost(pspId, serviceType, count.intValue());

            // Effective unit price = lineTotal / quantity. Avoids re-walking tiers
            // and naturally reflects blended tiered pricing on the invoice.
            BigDecimal unitPrice = (count > 0)
                    ? lineTotal.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            InvoiceLineItem item = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .lineNumber(lineNum++)
                    .serviceType(serviceType)
                    .description("Usage charges for " + serviceType)
                    .quantity(count.intValue())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();

            invoice.addLineItem(item);
            subtotal = subtotal.add(lineTotal);
        }

        invoice.setSubtotal(subtotal);
        // Add tax logic here if needed
        invoice.setTotalAmount(subtotal);

        Invoice saved = invoiceRepository.save(invoice);

        // Send invoice email asynchronously — fail-soft, never throws
        billingEmailService.sendInvoiceEmail(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByPsp(Long pspId) {
        return invoiceRepository.findByPsp_PspId(pspId);
    }
}
