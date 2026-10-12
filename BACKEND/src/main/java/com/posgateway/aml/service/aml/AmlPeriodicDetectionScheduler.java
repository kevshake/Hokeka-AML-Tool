package com.posgateway.aml.service.aml;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled AML scenario scans (structuring + rapid movement) that publish deduplicated operator alerts.
 */
@Component
@ConditionalOnProperty(name = "aml.periodic-detection.enabled", havingValue = "true", matchIfMissing = true)
public class AmlPeriodicDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AmlPeriodicDetectionScheduler.class);

    private final MerchantRepository merchantRepository;
    private final AmlScenarioDetectionService amlScenarioDetectionService;
    private final AmlScenarioAlertPublisherService alertPublisherService;

    @Value("${aml.periodic-detection.merchant-batch-size:50}")
    private int merchantBatchSize;

    public AmlPeriodicDetectionScheduler(MerchantRepository merchantRepository,
                                         AmlScenarioDetectionService amlScenarioDetectionService,
                                         AmlScenarioAlertPublisherService alertPublisherService) {
        this.merchantRepository = merchantRepository;
        this.amlScenarioDetectionService = amlScenarioDetectionService;
        this.alertPublisherService = alertPublisherService;
    }

    /** Runs structuring and rapid-movement detection for recently active merchants. */
    @Scheduled(cron = "${aml.periodic-detection.cron:0 15 */6 * * *}")
    public void scanMerchantsForAmlScenarios() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        merchantRepository.findAll(PageRequest.of(0, merchantBatchSize)).forEach(merchant -> runForMerchant(merchant, start, end));
    }

    private void runForMerchant(Merchant merchant, LocalDateTime start, LocalDateTime end) {
        String merchantId = String.valueOf(merchant.getMerchantId());
        try {
            amlScenarioDetectionService.detectStructuring(merchantId, start, end)
                    .forEach(hit -> alertPublisherService.publishStructuringHit(merchant, hit));
            amlScenarioDetectionService.detectRapidMovement(merchantId, start, end)
                    .forEach(hit -> alertPublisherService.publishRapidMovementHit(merchant, hit));
        } catch (Exception ex) {
            log.debug("AML periodic scan skipped for merchant {}: {}", merchantId, ex.getMessage());
        }
    }
}
