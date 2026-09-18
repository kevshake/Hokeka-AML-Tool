package com.posgateway.aml.scheduler;

import com.posgateway.aml.entity.billing.BillingCalculation;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.service.billing.BillingCalculationEngine;
import com.posgateway.aml.service.psp.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Billing Cycle Scheduler
 * Runs the monthly billing cycle as a batch job, decoupled from transaction processing.
 * <p>
 * Each active subscription's PSP gets a real, payable {@link Invoice} for the closed period
 * (via {@link BillingService#generateMonthlyInvoice}, which is idempotent), plus a
 * {@link BillingCalculation} analytics row. Previously this job only computed the analytics row
 * and produced no invoice, so nothing was ever billed unless an admin clicked a button.
 */
@Component
public class BillingCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingCycleScheduler.class);

    private final BillingCalculationEngine billingEngine;
    private final BillingService billingService;
    private final SubscriptionRepository subscriptionRepository;

    public BillingCycleScheduler(
            BillingCalculationEngine billingEngine,
            BillingService billingService,
            SubscriptionRepository subscriptionRepository) {
        this.billingEngine = billingEngine;
        this.billingService = billingService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Run monthly billing cycle.
     * Scheduled for 2 AM on the 1st of each month.
     */
    @Scheduled(cron = "${billing.cycle.cron:0 0 2 1 * *}")
    public void runMonthlyBillingCycle() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        runBillingForPeriod(lastMonth);
    }

    /**
     * Generate invoices (and analytics rows) for every active subscription for a given period.
     * Idempotent per PSP+period, so it is safe to re-run. Returns the number of PSPs invoiced.
     */
    public int runBillingForPeriod(YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        log.info("Starting billing cycle for period: {}", period);

        List<Subscription> activeSubscriptions = subscriptionRepository.findAllActive();
        log.info("Found {} active subscriptions to process", activeSubscriptions.size());

        int invoiced = 0;
        int errorCount = 0;

        for (Subscription subscription : activeSubscriptions) {
            Long pspId = subscription.getPsp() != null ? subscription.getPsp().getPspId() : null;
            if (pspId == null) {
                continue;
            }
            try {
                // Analytics row (per-tier calculation) — best-effort, not the payable artifact.
                try {
                    BillingCalculation calc = billingEngine.calculateMonthlyBill(pspId, period);
                    log.debug("Calculated bill for PSP {}: {} {}", pspId, calc.getTotalAmount(), calc.getCurrency());
                } catch (Exception calcEx) {
                    log.warn("Analytics calculation failed for PSP {} (invoice still generated): {}",
                            pspId, calcEx.getMessage());
                }
                // The payable artifact — idempotent, so a re-run returns the existing invoice.
                Invoice invoice = billingService.generateMonthlyInvoice(pspId, periodStart);
                log.info("Billed PSP {}: invoice {} total {} {}", pspId, invoice.getInvoiceNumber(),
                        invoice.getTotalAmount(), invoice.getCurrency());
                invoiced++;
            } catch (Exception e) {
                log.error("Error billing subscription {} (PSP {})",
                        subscription.getSubscriptionId(), pspId, e);
                errorCount++;
            }
        }

        log.info("Billing cycle for {} completed. Invoiced: {}, Errors: {}", period, invoiced, errorCount);
        return invoiced;
    }

    /**
     * Manual trigger for a single PSP (admin use) — generates the invoice idempotently.
     */
    public Invoice triggerBillingForPsp(Long pspId, YearMonth period) {
        log.info("Manually triggering billing for PSP {} for period {}", pspId, period);
        billingEngine.calculateMonthlyBill(pspId, period);
        return billingService.generateMonthlyInvoice(pspId, period.atDay(1));
    }

    /**
     * Re-run billing for a specific period across all active subscriptions (admin use). Idempotent.
     */
    public int recalculateAllForPeriod(YearMonth period) {
        log.info("Re-running billing for period: {}", period);
        return runBillingForPeriod(period);
    }
}
