package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspFraudIncident;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.psp.cbk.PspFraudIncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Bridges resolved alerts into CBK fraud incident records so the daily GDI
 * {@code FRAUD_INCIDENTS} endpoint reports real data (rather than relying on
 * manual data entry alone).
 */
@Service
public class AlertFraudIncidentBridge {

    private static final Logger log = LoggerFactory.getLogger(AlertFraudIncidentBridge.class);

    private final AlertRepository alertRepository;
    private final MerchantRepository merchantRepository;
    private final PspFraudIncidentRepository fraudIncidentRepository;
    private final com.posgateway.aml.service.fraud.CrossPspFraudIntelligenceService crossPspFraudService;

    public AlertFraudIncidentBridge(AlertRepository alertRepository,
                                     MerchantRepository merchantRepository,
                                     PspFraudIncidentRepository fraudIncidentRepository,
                                     com.posgateway.aml.service.fraud.CrossPspFraudIntelligenceService crossPspFraudService) {
        this.alertRepository = alertRepository;
        this.merchantRepository = merchantRepository;
        this.fraudIncidentRepository = fraudIncidentRepository;
        this.crossPspFraudService = crossPspFraudService;
    }

    /**
     * Called after an alert is disposed with a true-positive disposition.
     * Creates a PspFraudIncident record from the alert data.
     */
    @Transactional
    public Optional<PspFraudIncident> createFraudIncidentFromAlert(Long alertId,
                                                                     AlertDisposition disposition,
                                                                     String notes,
                                                                     User disposedBy) {
        if (disposition == null || !disposition.isTruePositive()) {
            return Optional.empty();
        }

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        Long merchantId = alert.getMerchantId();
        Long pspId = resolvePspId(merchantId);
        if (pspId == null) {
            log.debug("Cannot create fraud incident for alert {}: no PSP context", alertId);
            return Optional.empty();
        }

        String fraudCategoryFlag = switch (disposition) {
            case TRUE_POSITIVE_SAR_FILED -> "SAR_FILED";
            case TRUE_POSITIVE_BLOCKED -> "TRANSACTION_BLOCKED";
            case TRUE_POSITIVE_REPORTED -> "REPORTED_TO_LE";
            default -> "SUSPICIOUS_ACTIVITY";
        };

        String description = buildDescription(alert, disposition, notes);
        String action = switch (disposition) {
            case TRUE_POSITIVE_SAR_FILED -> "SAR filed with regulator";
            case TRUE_POSITIVE_BLOCKED -> "Transaction/merchant blocked";
            case TRUE_POSITIVE_REPORTED -> "Reported to law enforcement";
            default -> "Under investigation";
        };

        PspFraudIncident incident = PspFraudIncident.builder()
                .pspId(pspId)
                .reportingDate(LocalDate.now())
                .fraudCategoryFlag(fraudCategoryFlag)
                .victimCategory("MERCHANT")
                .victimInformation(merchantId != null ? "Merchant ID: " + merchantId : description)
                .dateOfOccurrence(LocalDate.now())
                .numberOfIncidences(1)
                .amountLost(BigDecimal.ZERO)
                .actionTaken(action)
                .recoveryDetails(notes)
                .alertIdLink(alertId)
                .build();

        PspFraudIncident saved = fraudIncidentRepository.save(incident);
        log.info("Created fraud incident #{} from alert #{}: type={}, pspId={}",
                saved.getId(), alertId, fraudCategoryFlag, pspId);

        // Auto-populate cross-PSP fraud intelligence
        try {
            crossPspFraudService.flagEntitiesFromAlert(alertId, pspId, disposition);
        } catch (Exception e) {
            log.warn("Cross-PSP flagging failed for alert {}: {}", alertId, e.getMessage());
        }

        return Optional.of(saved);
    }

    private String buildDescription(Alert alert, AlertDisposition disposition, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Alert #").append(alert.getAlertId());
        sb.append(" disposed as ").append(disposition.name());
        if (alert.getReason() != null) {
            sb.append(" — ").append(alert.getReason());
        }
        if (notes != null && !notes.isBlank()) {
            sb.append(" | ").append(notes);
        }
        return sb.toString();
    }

    private Long resolvePspId(Long merchantId) {
        if (merchantId == null) return null;
        try {
            return merchantRepository.findById(merchantId)
                    .map(m -> m.getPsp() != null ? m.getPsp().getPspId() : null)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve PSP for merchant {}: {}", merchantId, e.getMessage());
            return null;
        }
    }
}