package com.posgateway.aml.service.corporate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CorporateIntelligenceRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(CorporateIntelligenceRefreshJob.class);
    private final CorporateIntelligenceService service;

    public CorporateIntelligenceRefreshJob(CorporateIntelligenceService service) {
        this.service = service;
    }

    @Scheduled(cron = "${corporate.intelligence.refresh-cron:0 30 2 * * *}")
    public void refreshDueMerchants() {
        for (Long merchantId : service.dueMerchantIds()) {
            try {
                service.runScheduledCheck(merchantId);
            } catch (Exception exception) {
                log.error("Corporate-intelligence refresh failed for merchant {}: {}",
                        merchantId, exception.getMessage(), exception);
            }
        }
    }
}
