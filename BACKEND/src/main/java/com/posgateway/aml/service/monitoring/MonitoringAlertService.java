package com.posgateway.aml.service.monitoring;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.monitoring.MonitoringAlert;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.monitoring.MonitoringAlertRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MonitoringAlertService {

    private final MonitoringAlertRepository monitoringAlertRepository;
    private final PspIsolationService pspIsolationService;

    public MonitoringAlertService(MonitoringAlertRepository monitoringAlertRepository,
                                  PspIsolationService pspIsolationService) {
        this.monitoringAlertRepository = monitoringAlertRepository;
        this.pspIsolationService = pspIsolationService;
    }

    @Transactional
    public MonitoringAlert recordRiskChange(Merchant merchant, ScreeningResult result) {
        MonitoringAlert alert = new MonitoringAlert();
        alert.setMerchant(merchant);
        alert.setAlertType("RISK_CHANGE");
        alert.setAlertSeverity(severityFor(result));
        alert.setAlertDetails(buildDetails(merchant, result));
        return monitoringAlertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public Page<MonitoringAlert> listForCurrentUser(Pageable pageable) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return monitoringAlertRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        Long pspId = pspIsolationService.getCurrentUserPspId();
        if (pspId != null && pspId > 0) {
            return monitoringAlertRepository.findByMerchant_Psp_PspIdOrderByCreatedAtDesc(pspId, pageable);
        }
        return monitoringAlertRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<MonitoringAlert> listForMerchant(Long merchantId) {
        return monitoringAlertRepository.findByMerchant_MerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public Optional<MonitoringAlert> findAccessibleById(Long id) {
        return monitoringAlertRepository.findById(id).filter(this::canAccess);
    }

    @Transactional
    public Optional<MonitoringAlert> acknowledge(Long id, String acknowledgedBy) {
        return monitoringAlertRepository.findById(id)
                .filter(this::canAccess)
                .map(alert -> {
                    alert.setAcknowledged(true);
                    alert.setAcknowledgedBy(acknowledgedBy);
                    alert.setAcknowledgedAt(LocalDateTime.now());
                    return monitoringAlertRepository.save(alert);
                });
    }

    private boolean canAccess(MonitoringAlert alert) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return true;
        }
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        if (userPspId == null || alert.getMerchant() == null || alert.getMerchant().getPsp() == null) {
            return false;
        }
        return userPspId.equals(alert.getMerchant().getPsp().getPspId());
    }

    @Transactional(readOnly = true)
    public long countUnacknowledged() {
        Long pspId = pspIsolationService.isPlatformAdministrator()
                ? null
                : pspIsolationService.getCurrentUserPspId();
        return monitoringAlertRepository.countUnacknowledged(pspId);
    }

    private static String severityFor(ScreeningResult result) {
        if (result == null || result.getStatus() == null) {
            return "INFO";
        }
        return switch (result.getStatus()) {
            case MATCH -> "CRITICAL";
            case POTENTIAL_MATCH -> "WARN";
            default -> "INFO";
        };
    }

    private static Map<String, Object> buildDetails(Merchant merchant, ScreeningResult result) {
        Map<String, Object> details = new HashMap<>();
        details.put("merchantId", merchant.getMerchantId());
        details.put("merchantName", merchant.getLegalName());
        details.put("status", result.getStatus() != null ? result.getStatus().name() : null);
        details.put("matchCount", result.getMatchCount());
        details.put("highestMatchScore", result.getHighestMatchScore());
        return details;
    }
}
