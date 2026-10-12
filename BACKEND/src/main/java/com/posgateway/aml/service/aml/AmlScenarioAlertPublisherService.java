package com.posgateway.aml.service.aml;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns offline AML scenario detections into operator-visible fraud alerts with stable source references.
 */
@Service
public class AmlScenarioAlertPublisherService {

    private final AlertRepository alertRepository;

    public AmlScenarioAlertPublisherService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Creates a deduplicated AML alert for a merchant when structuring is detected in the scan window.
     */
    @Transactional
    public void publishStructuringHit(Merchant merchant, AmlScenarioDetectionService.StructuringDetection detection) {
        String ref = merchant.getMerchantId() + ":" + detection.getAccountId() + ":" + detection.getWindowStart();
        String reason = "Structuring pattern: " + detection.getTransactionCount() + " txn(s), total USD "
                + detection.getTotalAmount();
        publishIfNew(merchant, "AML_STRUCTURING", ref, reason, 0.85);
    }

    /**
     * Creates a deduplicated AML alert for rapid fund movement patterns.
     */
    @Transactional
    public void publishRapidMovementHit(Merchant merchant, AmlScenarioDetectionService.RapidMovementDetection detection) {
        String ref = merchant.getMerchantId() + ":rapid:" + detection.getWindowStart();
        String reason = "Rapid fund movement: " + detection.getTransactionCount() + " txn(s) between "
                + detection.getWindowStart() + " and " + detection.getWindowEnd();
        publishIfNew(merchant, "AML_RAPID_MOVEMENT", ref, reason, 0.75);
    }

    private void publishIfNew(Merchant merchant, String sourceType, String sourceReference, String reason, double score) {
        if (merchant == null || merchant.getPsp() == null) {
            return;
        }
        Long pspId = merchant.getPsp().getPspId();
        if (alertRepository.existsByPspIdAndSourceTypeAndSourceReference(pspId, sourceType, sourceReference)) {
            return;
        }
        Alert alert = new Alert();
        alert.setMerchantId(merchant.getMerchantId());
        alert.setPspId(pspId);
        alert.setSourceType(sourceType);
        alert.setSourceReference(sourceReference);
        alert.setAction("ALERT");
        alert.setSeverity(score >= 0.8 ? "CRITICAL" : "WARN");
        alert.setScore(score);
        alert.setReason(reason);
        alert.setStatus("open");
        alertRepository.save(alert);
    }
}
