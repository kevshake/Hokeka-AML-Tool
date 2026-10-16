package com.posgateway.aml.service.alert;

import com.posgateway.aml.dto.alert.AlertDetailResponseDTO;
import com.posgateway.aml.dto.assessment.FindingResponseDTO;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.alert.AlertFindingLink;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.alert.AlertFindingLinkRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds alert detail payloads with linked transactions, findings, and rule version provenance.
 */
@Service
public class AlertDetailQueryService {

    private final AlertRepository alertRepository;
    private final AlertTransactionLinkRepository alertTransactionLinkRepository;
    private final AlertFindingLinkRepository alertFindingLinkRepository;
    private final FindingRepository findingRepository;

    public AlertDetailQueryService(AlertRepository alertRepository,
                                   AlertTransactionLinkRepository alertTransactionLinkRepository,
                                   AlertFindingLinkRepository alertFindingLinkRepository,
                                   FindingRepository findingRepository) {
        this.alertRepository = alertRepository;
        this.alertTransactionLinkRepository = alertTransactionLinkRepository;
        this.alertFindingLinkRepository = alertFindingLinkRepository;
        this.findingRepository = findingRepository;
    }

    /**
     * Loads an alert by id with transaction links and finding provenance for investigation UI.
     */
    public AlertDetailResponseDTO getAlertDetail(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        AlertDetailResponseDTO dto = new AlertDetailResponseDTO();
        dto.setAlertId(alert.getAlertId());
        dto.setAction(alert.getAction());
        dto.setStatus(alert.getStatus());
        dto.setSeverity(alert.getSeverity());
        dto.setReason(alert.getReason());
        dto.setRuleInstanceId(alert.getRuleInstanceId());
        dto.setRuleVersionId(alert.getRuleVersionId());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setClosedAt(alert.getClosedAt());

        // Linked transactions by ID reference only (R8); details come from the Edge (R6)
        List<TransactionReference> refs = new ArrayList<>();
        TransactionReference own = alert.getTransactionReference();
        if (!own.isEmpty()) {
            refs.add(own);
        }
        alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(alertId).stream()
                .map(link -> link.getTransactionReference())
                .filter(ref -> !ref.isEmpty() && refs.stream().noneMatch(ref::sameTransactionAs))
                .forEach(refs::add);
        dto.setTransactionRefs(refs);
        dto.setTransactionIds(refs.stream()
                .map(TransactionReference::legacyTransactionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        dto.setTransactionDetails(null);

        List<FindingResponseDTO> findings = new ArrayList<>();
        alertFindingLinkRepository.findByAlertIdOrderByLinkedAtAsc(alertId).forEach(link ->
                findingRepository.findById(link.getFindingId()).ifPresent(f -> findings.add(toFindingDto(f))));
        if (findings.isEmpty() && alert.getTxnId() != null) {
            findingRepository.findByTxnIdOrderByCreatedAtAsc(alert.getTxnId())
                    .forEach(f -> findings.add(toFindingDto(f)));
        }
        dto.setFindings(findings);
        return dto;
    }

    private static FindingResponseDTO toFindingDto(Finding finding) {
        FindingResponseDTO dto = new FindingResponseDTO();
        dto.setFindingId(finding.getId());
        dto.setAssessmentId(finding.getAssessmentId());
        dto.setSourceType(finding.getSourceType() != null ? finding.getSourceType().name() : null);
        dto.setSourceId(finding.getSourceId());
        dto.setSourceVersion(finding.getSourceVersion());
        dto.setProposedAction(finding.getProposedAction());
        dto.setTriggered(finding.isTriggered());
        dto.setExplanation(finding.getExplanation());
        if (finding.getCreatedAt() != null) {
            dto.setCreatedAt(finding.getCreatedAt());
        }
        return dto;
    }
}
