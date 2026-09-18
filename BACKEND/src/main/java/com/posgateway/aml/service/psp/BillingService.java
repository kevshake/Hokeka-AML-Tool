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

    /**
     * Currency of the effective rate for this PSP + service type, falling back to
     * {@code USD} when no rate is configured. Used so usage-log rows are stamped
     * with the rate's real currency rather than a hardcoded value.
     */
    @Transactional(readOnly = true)
    public String getEffectiveCurrency(Long pspId, String serviceType) {
        return getEffectiveRate(pspId, serviceType)
                .map(BillingRate::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .orElse("USD");
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateUsageCost(Long pspId, String serviceType, int count) {
        Optional<BillingRate> rateOpt = getEffectiveRate(pspId, serviceType);
        if (rateOpt.isEmpty() || count <= 0) {
            return BigDecimal.ZERO;
        }

        BillingRate rate = rateOpt.get();
        String model = rate.getPricingModel();
        if (model == null) return BigDecimal.ZERO;

        if ("PER_REQUEST".equals(model) && rate.getBaseRate() != null) {
            return rate.getBaseRate().multiply(BigDecimal.valueOf(count));
        }
        if ("SUBSCRIPTION".equals(model)) {
            BigDecimal monthly = rate.getMonthlyFee() == null ? BigDecimal.ZERO : rate.getMonthlyFee();
            int included = rate.getIncludedRequests() == null ? 0 : rate.getIncludedRequests();
            BigDecimal overage = rate.getOverageRate() == null ? BigDecimal.ZERO : rate.getOverageRate();
            int over = Math.max(0, count - included);
            return monthly.add(overage.multiply(BigDecimal.valueOf(over)));
        }
        if ("TIERED".equals(model)) {
            return computeTieredCost(rate, count);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Tier config JSON shape:
     * <pre>{@code
     * { "tiers": [
     *     { "up_to": 10000, "rate": "0.0050" },
     *     { "up_to": 100000, "rate": "0.0040" },
     *     { "up_to": null,   "rate": "0.0030" }   // last tier: null/missing up_to = unlimited
     * ] }
     * }</pre>
     * Each request is billed at the rate of the tier its count falls into
     * (cumulative across tiers). If {@code tier_config} is missing or malformed
     * we fall back to baseRate × count so billing never returns silently zero.
     */
    private BigDecimal computeTieredCost(BillingRate rate, int count) {
        java.util.Map<String, Object> cfg = rate.getTierConfig();
        if (cfg == null || !(cfg.get("tiers") instanceof java.util.List<?> tiers) || tiers.isEmpty()) {
            BigDecimal base = rate.getBaseRate() == null ? BigDecimal.ZERO : rate.getBaseRate();
            return base.multiply(BigDecimal.valueOf(count));
        }
        BigDecimal total = BigDecimal.ZERO;
        int remaining = count;
        long consumed = 0;
        for (Object t : tiers) {
            if (!(t instanceof java.util.Map<?, ?> tier) || remaining <= 0) continue;
            Object upToRaw = tier.get("up_to");
            Object rateRaw = tier.get("rate");
            if (rateRaw == null) continue;
            BigDecimal tierRate;
            try {
                tierRate = new BigDecimal(rateRaw.toString());
            } catch (NumberFormatException nfe) {
                log.warn("Skipping malformed tier rate in tier_config: {}", rateRaw);
                continue;
            }
            // W37-4 fix: up_to used to be cast directly with ((Number) upToRaw).longValue(),
            // throwing ClassCastException if the JSON value came through as a String (e.g.
            // "10000" instead of 10000) -- unlike the adjacent tierRate parse just above, which
            // already handles that via new BigDecimal(rateRaw.toString()). Parse the same
            // defensive way instead of assuming Jackson always deserializes it as a Number.
            long upTo;
            if (upToRaw == null) {
                upTo = Long.MAX_VALUE;
            } else if (upToRaw instanceof Number n) {
                upTo = n.longValue();
            } else {
                try {
                    upTo = Long.parseLong(upToRaw.toString().trim());
                } catch (NumberFormatException nfe) {
                    log.warn("Skipping tier with malformed up_to in tier_config: {}", upToRaw);
                    continue;
                }
            }
            long sliceCap = Math.max(0L, upTo - consumed);
            long slice = Math.min(remaining, sliceCap);
            total = total.add(tierRate.multiply(BigDecimal.valueOf(slice)));
            consumed += slice;
            remaining -= (int) slice;
        }
        // If config didn't cover the full count (no unlimited last tier), bill remainder at the last seen rate.
        if (remaining > 0) {
            BigDecimal lastRate;
            try {
                lastRate = new BigDecimal(((java.util.Map<?, ?>) tiers.get(tiers.size() - 1)).get("rate").toString());
            } catch (Exception e) {
                lastRate = rate.getBaseRate() == null ? BigDecimal.ZERO : rate.getBaseRate();
            }
            total = total.add(lastRate.multiply(BigDecimal.valueOf(remaining)));
        }
        return total;
    }

    @Transactional
    public Invoice generateMonthlyInvoice(Long pspId, LocalDate periodStart) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        LocalDate periodEnd = periodStart.with(TemporalAdjusters.lastDayOfMonth());
        LocalDateTime startDateTime = periodStart.atStartOfDay();
        LocalDateTime endDateTime = periodEnd.atTime(23, 59, 59);

        // Idempotency: one invoice per PSP per billing period. A re-run of the monthly cycle (or a
        // second manual trigger) must return the existing invoice, never create a duplicate payable
        // one. This is the primary guard — usage rows are also stamped with invoice_id below.
        Optional<Invoice> existing = invoiceRepository.findByPspAndPeriod(pspId, periodStart, periodEnd);
        if (existing.isPresent()) {
            log.info("Invoice already exists for PSP {} period {} — returning existing #{}",
                    psp.getPspCode(), periodStart, existing.get().getInvoiceNumber());
            return existing.get();
        }

        log.info("Generating invoice for PSP {} for period {} to {}", psp.getPspCode(), periodStart, periodEnd);

        // Only usage not already rolled into a prior invoice — prevents double-billing.
        List<Object[]> usageSummary =
                apiUsageLogRepository.getUninvoicedUsageSummaryByService(pspId, startDateTime, endDateTime);

        Invoice invoice = Invoice.builder()
                .psp(psp)
                .invoiceNumber("INV-" + psp.getPspCode() + "-" + periodStart.toString().replace("-", "") + "-"
                        + UUID.randomUUID().toString().substring(0, 4))
                .billingPeriodStart(periodStart)
                .billingPeriodEnd(periodEnd)
                // Issued as SENT (finalized + payable — PaymentController accepts SENT/OVERDUE).
                // Email delivery below is best-effort and independent of this status: when SMTP is
                // not configured the invoice is still a real payable liability.
                .status("SENT")
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

        // Stamp the consumed usage rows with this invoice so they can never be billed again.
        int marked = apiUsageLogRepository.markUsageInvoiced(pspId, saved.getInvoiceId(), startDateTime, endDateTime);
        log.info("Invoice {} generated for PSP {} ({} usage rows consumed, total {})",
                saved.getInvoiceNumber(), psp.getPspCode(), marked, saved.getTotalAmount());

        // Send invoice email asynchronously — fail-soft, never throws
        billingEmailService.sendInvoiceEmail(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByPsp(Long pspId) {
        return invoiceRepository.findByPsp_PspId(pspId);
    }
}
