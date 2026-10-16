package com.posgateway.aml.service.monitoring;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.monitoring.MonitoringAlert;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.monitoring.MonitoringAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges AML monitoring_alerts into the operator fraud alerts queue with indexed FKs.
 */
@Service
public class MonitoringAlertBridgeService {

    private final AlertRepository alertRepository;
    private final MonitoringAlertRepository monitoringAlertRepository;

    public MonitoringAlertBridgeService(AlertRepository alertRepository,
                                        MonitoringAlertRepository monitoringAlertRepository) {
        this.alertRepository = alertRepository;
        this.monitoringAlertRepository = monitoringAlertRepository;
    }

    /**
     * Creates a fraud alert for CRITICAL/WARN monitoring hits and stores fraud_alert_id on the monitoring row.
     */
    @Transactional
    public Alert bridgeToFraudAlert(MonitoringAlert monitoringAlert, Merchant merchant) {
        if (monitoringAlert == null || merchant == null || monitoringAlert.getFraudAlertId() != null) {
            return null;
        }
        Alert alert = new Alert();
        alert.setMerchantId(merchant.getMerchantId());
        alert.setPspId(merchant.getPsp() != null ? merchant.getPsp().getPspId() : null);
        alert.setSourceType("ONGOING_MONITORING");
        alert.setSourceReference(String.valueOf(monitoringAlert.getAlertId()));
        alert.setAction("ALERT");
        alert.setSeverity(monitoringAlert.getAlertSeverity());
        alert.setReason("AML monitoring: " + monitoringAlert.getAlertType());
        alert.setStatus("open");
        alert.setScore("CRITICAL".equals(monitoringAlert.getAlertSeverity()) ? 0.95 : 0.65);
        Alert saved = alertRepository.save(alert);
        monitoringAlert.setFraudAlertId(saved.getAlertId());
        monitoringAlertRepository.save(monitoringAlert);
        return saved;
    }
}
