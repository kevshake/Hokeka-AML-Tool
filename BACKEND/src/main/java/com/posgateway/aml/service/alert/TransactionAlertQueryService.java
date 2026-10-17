package com.posgateway.aml.service.alert;

import com.posgateway.aml.dto.alert.AlertSummaryDTO;
import com.posgateway.aml.dto.alert.TransactionAlertsResponseDTO;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read model for alerts linked to a transaction (direct txn_id and alert_transactions).
 */
@Service
public class TransactionAlertQueryService {

    private final AlertRepository alertRepository;
    private final AlertLinkageService alertLinkageService;

    public TransactionAlertQueryService(AlertRepository alertRepository,
                                          AlertLinkageService alertLinkageService) {
        this.alertRepository = alertRepository;
        this.alertLinkageService = alertLinkageService;
    }

    /**
     * Loads alert summaries for a transaction id, merging legacy single-txn alerts and join-table links.
     */
    public TransactionAlertsResponseDTO getAlertsForTransaction(Long txnId) {
        if (txnId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "txnId is required");
        }
        Set<Long> alertIds = new LinkedHashSet<>(alertLinkageService.findAlertIdsByTransaction(txnId));
        alertRepository.findByTxnId(txnId).forEach(a -> alertIds.add(a.getAlertId()));

        List<AlertSummaryDTO> summaries = new ArrayList<>();
        for (Long alertId : alertIds) {
            alertRepository.findById(alertId).ifPresent(alert -> summaries.add(toSummary(alert)));
        }
        TransactionAlertsResponseDTO dto = new TransactionAlertsResponseDTO();
        dto.setTxnId(txnId);
        dto.setAlerts(summaries);
        return dto;
    }

    /**
     * Alert summaries of a decision record (R6, by ID reference): alerts that name it directly plus
     * those linked through {@code alert_transactions}. {@code pspId} scopes them to one PSP;
     * {@code null} is the platform-administrator view across PSPs.
     */
    public TransactionAlertsResponseDTO getAlertsForDecisionRecord(Long pspId, java.util.UUID edgeAssessmentId) {
        if (edgeAssessmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "edgeAssessmentId is required");
        }
        java.util.Map<Long, Alert> byId = new java.util.LinkedHashMap<>();
        (pspId == null ? alertRepository.findByEdgeAssessmentIdOrderByCreatedAtDesc(edgeAssessmentId)
                : alertRepository.findByPspIdAndEdgeAssessmentIdOrderByCreatedAtDesc(pspId, edgeAssessmentId))
                .forEach(a -> byId.putIfAbsent(a.getAlertId(), a));
        alertLinkageService.findAlertIdsByDecisionRecord(edgeAssessmentId).forEach(id ->
                alertRepository.findById(id).filter(a -> pspId == null || pspId.equals(a.getPspId()))
                        .ifPresent(a -> byId.putIfAbsent(a.getAlertId(), a)));
        TransactionAlertsResponseDTO dto = new TransactionAlertsResponseDTO();
        dto.setEdgeAssessmentId(edgeAssessmentId);
        dto.setAlerts(byId.values().stream().map(TransactionAlertQueryService::toSummary).toList());
        return dto;
    }

    private static AlertSummaryDTO toSummary(Alert alert) {
        AlertSummaryDTO dto = new AlertSummaryDTO();
        dto.setAlertId(alert.getAlertId());
        dto.setAction(alert.getAction());
        dto.setStatus(alert.getStatus());
        dto.setSeverity(alert.getSeverity());
        dto.setReason(alert.getReason());
        dto.setRuleInstanceId(alert.getRuleInstanceId());
        dto.setRuleVersionId(alert.getRuleVersionId());
        dto.setCreatedAt(alert.getCreatedAt());
        return dto;
    }
}
