package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.alert.AlertFindingLink;
import com.posgateway.aml.entity.alert.AlertTransactionLink;
import com.posgateway.aml.repository.alert.AlertFindingLinkRepository;
import com.posgateway.aml.repository.alert.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Maintains alert_transactions and alert_findings when alerts are created from orchestrator findings.
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
     * Links the primary transaction to the alert and records optional rule provenance fields.
     */
    @Transactional
    public void linkPrimaryTransaction(Alert alert, TransactionEntity transaction,
                                       Long ruleInstanceId, Long ruleVersionId) {
        if (alert == null || alert.getAlertId() == null || transaction == null) {
            return;
        }
        if (ruleInstanceId != null) {
            alert.setRuleInstanceId(ruleInstanceId);
        }
        if (ruleVersionId != null) {
            alert.setRuleVersionId(ruleVersionId);
        }
        linkTransaction(alert.getAlertId(), transaction.getTxnId());
    }

    /**
     * Idempotently links a transaction id to an alert id.
     */
    @Transactional
    public void linkTransaction(Long alertId, Long txnId) {
        if (alertId == null || txnId == null) {
            return;
        }
        AlertTransactionLink link = new AlertTransactionLink();
        link.setAlertId(alertId);
        link.setTxnId(txnId);
        alertTransactionLinkRepository.save(link);
    }

    /**
     * Links persisted findings for the transaction's latest assessment to the alert when present.
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
     * Returns alert ids associated with a transaction via alert_transactions (includes legacy txn_id column).
     */
    public List<Long> findAlertIdsByTransaction(Long txnId) {
        return alertTransactionLinkRepository.findByTxnIdOrderByLinkedAtDesc(txnId).stream()
                .map(AlertTransactionLink::getAlertId)
                .distinct()
                .toList();
    }
}
