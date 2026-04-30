package com.posgateway.aml.scheduler;

import com.posgateway.aml.entity.billing.BillingCalculation;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.service.billing.BillingCalculationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * Billing Cycle Scheduler
 * Runs monthly billing calculations as a batch job.
 * Completely decoupled from transaction processing.
 */
@Component
public class BillingCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingCycleScheduler.class);

    private final BillingCalculationEngine billingEngine;
    private final SubscriptionRepository subscriptionRepository;

    public BillingCycleScheduler(
            BillingCalculationEngine billingEngine,
            SubscriptionRepository subscriptionRepository) {
        this.billingEngine = billingEngine;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Run monthly billing cycle.
     * Scheduled for 2 AM on the 1st of each month.
     */
    @Scheduled(cron = "${billing.cycle.cron:0 0 2 1 * *}")
    public void runMonthlyBillingCycle() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("Starting monthly billing cycle for period: {}", lastMonth);

        List<Subscription> activeSubscriptions = subscriptionRepository.findAllActive();
        log.info("Found {} active subscriptions to process", activeSubscriptions.size());

        int successCount = 0;
        int errorCount = 0;

        for (Subscription subscription : activeSubscriptions) {
            try {
                Long pspId = subscription.getPsp().getPspId();
                BillingCalculation calculation = billingEngine.calculateMonthlyBill(pspId, lastMonth);
                log.debug("Calculated bill for PSP {}: {} {}",
                        pspId, calculation.getTotalAmount(), calculation.getCurrency());
                successCount++;
            } catch (Exception e) {
                log.error("Error calculating bill for subscription {}",
                        subscription.getSubscriptionId(), e);
                errorCount++;
            }
        }

        log.info("Monthly billing cycle completed. Success: {}, Errors: {}", successCount, errorCount);
    }

    /**
     * Manual trigger for billing calculation (for testing/admin use)
     */
    public void triggerBillingForPsp(Long pspId, YearMonth period) {
        log.info("Manually triggering billing for PSP {} for period {}", pspId, period);
        billingEngine.calculateMonthlyBill(pspId, period);
    }

    /**
     * Recalculate all bills for a specific period (admin use)
     */
    public void recalculateAllForPeriod(YearMonth period) {
        log.info("Recalculating all bills for period: {}", period);
        List<Subscription> activeSubscriptions = subscriptionRepository.findAllActive();

        for (Subscription subscription : activeSubscriptions) {
            try {
                Long pspId = subscription.getPsp().getPspId();
                billingEngine.calculateMonthlyBill(pspId, period);
            } catch (Exception e) {
                log.error("Error recalculating bill for subscription {}",
                        subscription.getSubscriptionId(), e);
            }
        }
    }
}
