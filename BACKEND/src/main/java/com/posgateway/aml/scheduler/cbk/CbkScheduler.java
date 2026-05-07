package com.posgateway.aml.scheduler.cbk;

import com.posgateway.aml.service.cbk.CbkSubmissionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring-scheduled triggers for CBK GDI regulatory report submissions.
 *
 * <p>This component is only registered when {@code cbk.enabled=true} is set in
 * application properties (global kill switch). All business logic lives in
 * {@link CbkSubmissionOrchestrator}; this class is pure scheduling glue.
 *
 * <h2>Cron schedule</h2>
 * <pre>
 *  02:00 daily       — 8 daily endpoints
 *  02:30 on day 1    — Monthly day-1 endpoints  (#10 products, #15 tariffs)
 *  02:30 on day 2    — Monthly day-2 endpoints  (#12 card brands, #14 txn details)
 *  02:30 on day 3    — Monthly day-3 endpoint   (#5 customer complaints)
 *  03:00 on Jan 4    — Annual day-4 endpoint    (#4 shareholders)
 *  03:00 on Jan 5    — Annual day-5 endpoints   (#1 senior mgmt, #2 directors, #3 trustees)
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "cbk.enabled", havingValue = "true")
public class CbkScheduler {

    private static final Logger log = LoggerFactory.getLogger(CbkScheduler.class);

    private final CbkSubmissionOrchestrator orchestrator;

    public CbkScheduler(CbkSubmissionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    // =========================================================================
    // Daily — 02:00 every day
    // =========================================================================

    /**
     * Fires all 8 daily CBK endpoints for every eligible PSP.
     * Cron: {@code 0 0 2 * * *} (02:00:00 server time, every day).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runDailyCbkSubmissions() {
        log.info("CBK scheduler: starting daily run");
        try {
            orchestrator.runDailyCbkSubmissionsForAllPsps();
            log.info("CBK scheduler: daily run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: daily run encountered unexpected error", ex);
        }
    }

    // =========================================================================
    // Monthly day 1 — 02:30 on the 1st of every month
    // =========================================================================

    /**
     * Fires monthly day-1 endpoints (#10 Products, #15 Tariffs) for every eligible PSP.
     * Cron: {@code 0 30 2 1 * *} (02:30 on day 1 of each month).
     */
    @Scheduled(cron = "0 30 2 1 * *")
    public void runMonthlyDay1CbkSubmissions() {
        log.info("CBK scheduler: starting monthly day-1 run");
        try {
            orchestrator.runMonthlyCbkSubmissionsForAllPsps(1);
            log.info("CBK scheduler: monthly day-1 run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: monthly day-1 run encountered unexpected error", ex);
        }
    }

    // =========================================================================
    // Monthly day 2 — 02:30 on the 2nd of every month
    // =========================================================================

    /**
     * Fires monthly day-2 endpoints (#12 Card Brands, #14 Transaction Details)
     * for every eligible PSP.
     * Cron: {@code 0 30 2 2 * *} (02:30 on day 2 of each month).
     */
    @Scheduled(cron = "0 30 2 2 * *")
    public void runMonthlyDay2CbkSubmissions() {
        log.info("CBK scheduler: starting monthly day-2 run");
        try {
            orchestrator.runMonthlyCbkSubmissionsForAllPsps(2);
            log.info("CBK scheduler: monthly day-2 run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: monthly day-2 run encountered unexpected error", ex);
        }
    }

    // =========================================================================
    // Monthly day 3 — 02:30 on the 3rd of every month
    // =========================================================================

    /**
     * Fires monthly day-3 endpoint (#5 Customer Complaints) for every eligible PSP.
     * Cron: {@code 0 30 2 3 * *} (02:30 on day 3 of each month).
     */
    @Scheduled(cron = "0 30 2 3 * *")
    public void runMonthlyDay3CbkSubmissions() {
        log.info("CBK scheduler: starting monthly day-3 run");
        try {
            orchestrator.runMonthlyCbkSubmissionsForAllPsps(3);
            log.info("CBK scheduler: monthly day-3 run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: monthly day-3 run encountered unexpected error", ex);
        }
    }

    // =========================================================================
    // Annual Jan 4 — 03:00 on 4 January each year
    // =========================================================================

    /**
     * Fires annual day-4 endpoint (#4 Shareholders) for every eligible PSP.
     * Cron: {@code 0 0 3 4 1 *} (03:00 on 4 January).
     */
    @Scheduled(cron = "0 0 3 4 1 *")
    public void runAnnualJan4CbkSubmissions() {
        log.info("CBK scheduler: starting annual Jan-4 run");
        try {
            orchestrator.runAnnualCbkSubmissionsForAllPsps(4);
            log.info("CBK scheduler: annual Jan-4 run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: annual Jan-4 run encountered unexpected error", ex);
        }
    }

    // =========================================================================
    // Annual Jan 5 — 03:00 on 5 January each year
    // =========================================================================

    /**
     * Fires annual day-5 endpoints (#1 Senior Management, #2 Directors, #3 Trustees)
     * for every eligible PSP.
     * Cron: {@code 0 0 3 5 1 *} (03:00 on 5 January).
     */
    @Scheduled(cron = "0 0 3 5 1 *")
    public void runAnnualJan5CbkSubmissions() {
        log.info("CBK scheduler: starting annual Jan-5 run");
        try {
            orchestrator.runAnnualCbkSubmissionsForAllPsps(5);
            log.info("CBK scheduler: annual Jan-5 run completed");
        } catch (Exception ex) {
            log.error("CBK scheduler: annual Jan-5 run encountered unexpected error", ex);
        }
    }
}
