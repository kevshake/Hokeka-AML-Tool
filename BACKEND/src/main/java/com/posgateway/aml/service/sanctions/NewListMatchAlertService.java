package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * New List Match Alert Service
 * Alerts when customer appears on new sanctions list
 */
@Service
public class NewListMatchAlertService {

    private static final Logger logger = LoggerFactory.getLogger(NewListMatchAlertService.class);

    @SuppressWarnings("unused")
    private final MerchantRepository merchantRepository;
    private final MerchantScreeningResultRepository screeningResultRepository;
    private final AlertRepository alertRepository;

    @Autowired
    public NewListMatchAlertService(
            MerchantRepository merchantRepository,
            MerchantScreeningResultRepository screeningResultRepository,
            AlertRepository alertRepository) {
        this.merchantRepository = merchantRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * Check for new list matches after rescreening
     */
    @Transactional
    public void checkForNewListMatches(Merchant merchant, ScreeningResult newScreeningResult) {
        // Get previous screening result
        List<MerchantScreeningResult> previousResults = screeningResultRepository
                .findByMerchant_MerchantIdOrderByScreenedAtDesc(merchant.getMerchantId());

        if (previousResults.isEmpty()) {
            // First screening, no previous to compare
            return;
        }

        MerchantScreeningResult lastResult = previousResults.get(0);
        
        // Compare matches
        if (lastResult.getMatchCount() != null && lastResult.getMatchCount() == 0 &&
            newScreeningResult.hasMatches()) {
            // New match appeared!
            createNewListMatchAlert(merchant, newScreeningResult);
        } else if (lastResult.getMatchCount() != null && lastResult.getMatchCount() > 0 &&
                   newScreeningResult.getMatchCount() > lastResult.getMatchCount()) {
            // Additional matches found
            createNewListMatchAlert(merchant, newScreeningResult);
        }
    }

    /**
     * Create alert for new list match
     */
    private void createNewListMatchAlert(Merchant merchant, ScreeningResult screeningResult) {
        Alert alert = new Alert();
        alert.setMerchantId(merchant.getMerchantId());
        alert.setAction("ALERT");
        alert.setReason("New sanctions list match detected for merchant: " + merchant.getLegalName() +
                ". Match count: " + screeningResult.getMatchCount());
        alert.setStatus("open");
        alert.setSeverity("CRITICAL");
        alert.setCreatedAt(LocalDateTime.now());

        alertRepository.save(alert);
        logger.warn("Created alert for new list match: Merchant {} has {} new matches",
                merchant.getMerchantId(), screeningResult.getMatchCount());
    }
}

