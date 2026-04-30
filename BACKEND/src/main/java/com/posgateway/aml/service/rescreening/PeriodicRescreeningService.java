package com.posgateway.aml.service.rescreening;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Periodic Rescreening Service
 * Automatically rescreens active merchants weekly (every 7 days)
 */
@Service
public class PeriodicRescreeningService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PeriodicRescreeningService.class);

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantScreeningResultRepository screeningResultRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AmlScreeningOrchestrator screeningOrchestrator;

    @Value("${rescreening.enabled:true}")
    private boolean rescreeningEnabled;

    @Value("${rescreening.frequency.days:7}")
    private int rescreeningFrequencyDays;

    /**
     * Scheduled rescreening job
     * Runs daily at 3:00 AM to check for merchants needing rescreening
     */
    @Scheduled(cron = "${rescreening.cron:0 0 3 * * *}")
    @Transactional
    public void performScheduledRescreening() {
        if (!rescreeningEnabled) {
            log.info("Rescreening is disabled, skipping scheduled run");
            return;
        }

        log.info("Starting scheduled merchant rescreening...");

        try {
            // Find merchants needing rescreening
            List<Merchant> merchantsToScreen = merchantRepository.findMerchantsNeedingRescreening(LocalDate.now());

            log.info("Found {} merchants requiring rescreening", merchantsToScreen.size());

            int successCount = 0;
            int failureCount = 0;

            for (Merchant merchant : merchantsToScreen) {
                try {
                    log.info("Rescreening merchant: {} (ID: {})", merchant.getLegalName(), merchant.getMerchantId());

                    // Perform screening
                    ScreeningResult result = screeningOrchestrator.screenMerchant(merchant);

                    // Check if screening result changed
                    boolean riskChanged = evaluateRiskChange(merchant, result);

                    if (riskChanged) {
                        log.warn("Risk status changed for merchant {}: {}",
                                merchant.getLegalName(), result.getStatus());
                        // Create monitoring alert
                        createMonitoringAlert(merchant, result);
                    }

                    // Update next screening due date
                    merchant.updateNextScreeningDue();
                    merchantRepository.save(merchant);

                    successCount++;

                } catch (Exception e) {
                    log.error("Failed to rescreen merchant {}: {}",
                            merchant.getMerchantId(), e.getMessage(), e);
                    failureCount++;
                }
            }

            log.info("Rescreening complete: {} successful, {} failed", successCount, failureCount);

        } catch (Exception e) {
            log.error("Error during scheduled rescreening: {}", e.getMessage(), e);
        }
    }

    /**
     * Manually trigger rescreening for a specific merchant
     */
    public ScreeningResult rescreenMerchant(Long merchantId) {
        log.info("Manual rescreening triggered for merchant ID: {}", merchantId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        ScreeningResult result = screeningOrchestrator.screenMerchant(merchant);

        // Update next screening due
        merchant.updateNextScreeningDue();
        merchantRepository.save(merchant);

        return result;
    }

    /**
     * Evaluate if risk status has changed since last screening
     */
    private boolean evaluateRiskChange(Merchant merchant, ScreeningResult currentResult) {
        // Get previous screening result
        var previousResult = screeningResultRepository.findLatestByMerchantId(merchant.getMerchantId());

        if (previousResult.isEmpty()) {
            return false; // No previous result to compare
        }

        String previousStatus = previousResult.get().getScreeningStatus();
        String currentStatus = currentResult.getStatus().name();

        // Risk changed if status went from CLEAR to MATCH/POTENTIAL_MATCH
        return !previousStatus.equals(currentStatus) &&
                ("MATCH".equals(currentStatus) || "POTENTIAL_MATCH".equals(currentStatus));
    }

    /**
     * Create monitoring alert for merchant with changed risk status
     */
    /**
     * Create monitoring alert for merchant with changed risk status
     */
    private void createMonitoringAlert(Merchant merchant, ScreeningResult result) {
        log.warn("MONITORING ALERT: Merchant {} now has status {}",
                merchant.getLegalName(), result.getStatus());

        Alert alert = new Alert();
        alert.setAction("MONITORING_ALERT");
        alert.setReason("Risk status changed to " + result.getStatus());
        alert.setStatus("open");
        alert.setScore(result.getHighestMatchScore());
        alert.setNotes("Merchant: " + merchant.getLegalName() + " (ID: " + merchant.getMerchantId() +
                "). Matches found: " + result.getMatchCount());

        alertRepository.save(alert);
    }

    /**
     * Get rescreening statistics
     */
    public RescreeningStats getStats() {
        LocalDate today = LocalDate.now();
        List<Merchant> needsRescreening = merchantRepository.findMerchantsNeedingRescreening(today);
        long totalActive = merchantRepository.countByStatus("ACTIVE");

        return RescreeningStats.builder()
                .totalActiveMerchants(totalActive)
                .merchantsNeedingRescreening(needsRescreening.size())
                .rescreeningFrequencyDays(rescreeningFrequencyDays)
                .enabled(rescreeningEnabled)
                .build();
    }

    public static class RescreeningStats {
        private long totalActiveMerchants;
        private int merchantsNeedingRescreening;
        private int rescreeningFrequencyDays;
        private boolean enabled;

        public RescreeningStats(long totalActiveMerchants, int merchantsNeedingRescreening,
                int rescreeningFrequencyDays, boolean enabled) {
            this.totalActiveMerchants = totalActiveMerchants;
            this.merchantsNeedingRescreening = merchantsNeedingRescreening;
            this.rescreeningFrequencyDays = rescreeningFrequencyDays;
            this.enabled = enabled;
        }

        public long getTotalActiveMerchants() {
            return totalActiveMerchants;
        }

        public int getMerchantsNeedingRescreening() {
            return merchantsNeedingRescreening;
        }

        public int getRescreeningFrequencyDays() {
            return rescreeningFrequencyDays;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public static RescreeningStatsBuilder builder() {
            return new RescreeningStatsBuilder();
        }

        public static class RescreeningStatsBuilder {
            private long totalActiveMerchants;
            private int merchantsNeedingRescreening;
            private int rescreeningFrequencyDays;
            private boolean enabled;

            public RescreeningStatsBuilder totalActiveMerchants(long totalActiveMerchants) {
                this.totalActiveMerchants = totalActiveMerchants;
                return this;
            }

            public RescreeningStatsBuilder merchantsNeedingRescreening(int merchantsNeedingRescreening) {
                this.merchantsNeedingRescreening = merchantsNeedingRescreening;
                return this;
            }

            public RescreeningStatsBuilder rescreeningFrequencyDays(int rescreeningFrequencyDays) {
                this.rescreeningFrequencyDays = rescreeningFrequencyDays;
                return this;
            }

            public RescreeningStatsBuilder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public RescreeningStats build() {
                return new RescreeningStats(totalActiveMerchants, merchantsNeedingRescreening, rescreeningFrequencyDays,
                        enabled);
            }
        }
    }
}
