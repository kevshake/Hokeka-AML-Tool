package com.posgateway.aml.service.risk;

import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes merchant CRA rows from behaviour-driven {@link CustomerRiskProfilingService} output.
 */
@Component
@ConditionalOnProperty(name = "risk.behaviour-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class MerchantRiskRatingRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MerchantRiskRatingRefreshScheduler.class);

    private final MerchantRepository merchantRepository;
    private final MerchantRiskRatingPersistenceService persistenceService;

    @Value("${risk.behaviour-refresh.merchant-batch-size:100}")
    private int merchantBatchSize;

    public MerchantRiskRatingRefreshScheduler(MerchantRepository merchantRepository,
                                              MerchantRiskRatingPersistenceService persistenceService) {
        this.merchantRepository = merchantRepository;
        this.persistenceService = persistenceService;
    }

    /** Recomputes merchant risk scores on a fixed cadence so CRA/risk_level stay current. */
    @Scheduled(cron = "${risk.behaviour-refresh.cron:0 30 */4 * * *}")
    public void refreshMerchantRiskRatings() {
        merchantRepository.findAll(PageRequest.of(0, merchantBatchSize)).forEach(merchant -> {
            try {
                persistenceService.refreshMerchantRisk(merchant.getMerchantId());
            } catch (Exception ex) {
                log.debug("Risk refresh skipped for merchant {}: {}", merchant.getMerchantId(), ex.getMessage());
            }
        });
    }
}
