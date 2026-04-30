package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.InvoiceLineItem;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.BillingRateRepository;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// @RequiredArgsConstructor removed
@Service
public class BillingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BillingService.class);

    private final BillingRateRepository billingRateRepository;
    private final InvoiceRepository invoiceRepository;
    private final PspRepository pspRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;

    public BillingService(BillingRateRepository billingRateRepository, InvoiceRepository invoiceRepository,
            PspRepository pspRepository, ApiUsageLogRepository apiUsageLogRepository) {
        this.billingRateRepository = billingRateRepository;
        this.invoiceRepository = invoiceRepository;
        this.pspRepository = pspRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
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
        if (rateOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BillingRate rate = rateOpt.get();
        if ("PER_REQUEST".equals(rate.getPricingModel()) && rate.getBaseRate() != null) {
            return rate.getBaseRate().multiply(BigDecimal.valueOf(count));
        } else if ("TIERED".equals(rate.getPricingModel())) {
            // Simple tiered logic: First 10k cheaper, next expensive, etc. (Mock
            // implementation)
            // Real implementation would parse rate.getTiers() JSON or similar structure.
            // Assuming baseRate is Tier 1, and we discount for volume > 10000
            BigDecimal total = BigDecimal.ZERO;
            if (count <= 10000) {
                total = rate.getBaseRate().multiply(BigDecimal.valueOf(count));
            } else {
                // First 10000 at base rate
                total = rate.getBaseRate().multiply(BigDecimal.valueOf(10000));
                // Remaining at 80% of base rate
                BigDecimal tier2Rate = rate.getBaseRate().multiply(new BigDecimal("0.8"));
                int remaining = count - 10000;
                total = total.add(tier2Rate.multiply(BigDecimal.valueOf(remaining)));
            }
            return total;
        }

        return BigDecimal.ZERO;
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
            BigDecimal cost = (BigDecimal) row[2];

            if (cost == null)
                cost = BigDecimal.ZERO;

            InvoiceLineItem item = InvoiceLineItem.builder()
                    .invoice(invoice)
                    .lineNumber(lineNum++)
                    .serviceType(serviceType)
                    .description("Usage charges for " + serviceType)
                    .quantity(count.intValue())
                    .unitPrice(BigDecimal.ZERO) // Average/Effective rate calculation is complex, showing 0 for now
                    .lineTotal(cost)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();

            invoice.addLineItem(item);
            subtotal = subtotal.add(cost);
        }

        invoice.setSubtotal(subtotal);
        // Add tax logic here if needed
        invoice.setTotalAmount(subtotal);

        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByPsp(Long pspId) {
        return invoiceRepository.findByPsp_PspId(pspId);
    }
}
