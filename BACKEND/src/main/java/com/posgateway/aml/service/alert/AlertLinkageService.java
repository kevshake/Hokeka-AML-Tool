package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.alert.AlertFindingLink;
import com.posgateway.aml.repository.alert.AlertFindingLinkRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Maintains alert_findings and resolves alert ids for a transaction via V239 alert_transactions.
 */
@Service
public class AlertLinkageService {

    private final AlertTransactionLinkRepository alertTransactionLinkRepository;
    private final AlertFindingLinkRepository alertFindingLinkRepository;
    private final FindingRepository findingRepository;

    public AlertLinkageService(AlertTransactionLinkRepository alertTransactionLinkRepository,
                               AlertFindingLinkRepository alertFindingLinkRepository,
                               FindingRepository findingRepository) {
        this.alertTransactionLinkRepository = alertTransactionLinkRepository;
        this.alertFindingLinkRepository = alertFindingLinkRepository;
        this.findingRepository = findingRepository;
    }

    /**
     * Stores optional rule provenance columns on the alert row (txn link is handled by ComplianceEntityLinkService).
     */
    @Transactional
    public void applyRuleProvenance(Alert alert, Long ruleInstanceId, Long ruleVersionId) {
        if (alert == null) {
            return;
        }
        if (ruleInstanceId != null) {
            alert.setRuleInstanceId(ruleInstanceId);
        }
        if (ruleVersionId != null) {
            alert.setRuleVersionId(ruleVersionId);
        }
    }

    /**
     * Links persisted assessment findings for a transaction to the alert for investigation provenance.
     */
    @Transactional
    public void linkFindingsForTransaction(Long alertId, Long txnId) {
        if (alertId == null || txnId == null) {
            return;
        }
        findingRepository.findByTxnIdOrderByCreatedAtAsc(txnId).forEach(finding -> {
            AlertFindingLink link = new AlertFindingLink();
            link.setAlertId(alertId);
            link.setFindingId(finding.getId());
            alertFindingLinkRepository.save(link);
        });
    }

    /**
     * Returns alert ids associated with a transaction via alert_transactions join rows.
     */
    public List<Long> findAlertIdsByTransaction(Long txnId) {
        return alertTransactionLinkRepository.findByTxnIdOrderByIdDesc(txnId).stream()
                .map(link -> link.getAlertId())
                .distinct()
                .toList();
    }
}
