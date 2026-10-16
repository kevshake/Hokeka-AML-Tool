package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.AlertTransactionLink;
import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SarAlertLink;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.compliance.SarAlertLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains indexed foreign-key bridges between fraud alerts, cases, SARs, transactions, and card identity (pan_hash).
 */
@Service
public class ComplianceEntityLinkService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEntityLinkService.class);

    private final AlertRepository alertRepository;
    private final AlertTransactionLinkRepository alertTransactionLinkRepository;
    private final SarAlertLinkRepository sarAlertLinkRepository;
    private final CaseTransactionRepository caseTransactionRepository;
    private final TransactionRepository transactionRepository;

    public ComplianceEntityLinkService(AlertRepository alertRepository,
                                         AlertTransactionLinkRepository alertTransactionLinkRepository,
                                         SarAlertLinkRepository sarAlertLinkRepository,
                                         CaseTransactionRepository caseTransactionRepository,
                                         TransactionRepository transactionRepository) {
        this.alertRepository = alertRepository;
        this.alertTransactionLinkRepository = alertTransactionLinkRepository;
        this.sarAlertLinkRepository = sarAlertLinkRepository;
        this.caseTransactionRepository = caseTransactionRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * After an alert is created, denormalize card identity and register the primary txn link row.
     */
    @Transactional
    public void registerNewFraudAlert(Alert alert, TransactionEntity transaction) {
        if (alert == null || alert.getAlertId() == null) {
            return;
        }
        if (transaction != null) {
            // Legacy transaction rows may still carry a raw PAN until the startup scrub has run;
            // never copy such a value onto the alert.
            if (transaction.getPanHash() != null && !transaction.getPanHash().isBlank()
                    && !com.posgateway.aml.util.PanHashGuard.looksLikeRawPan(transaction.getPanHash())) {
                alert.setPanHash(transaction.getPanHash());
            }
            linkAlertToTransaction(alert.getAlertId(), transaction.getTxnId(), true);
        } else if (alert.getTxnId() != null) {
            linkAlertToTransaction(alert.getAlertId(), alert.getTxnId(), true);
        }
        alertRepository.save(alert);
    }

    /**
     * Idempotently inserts an alert_transactions row for consolidated or primary txn associations.
     */
    @Transactional
    public void linkAlertToTransaction(Long alertId, Long txnId, boolean primary) {
        if (alertId == null || txnId == null) {
            return;
        }
        boolean exists = alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(alertId).stream()
                .anyMatch(row -> txnId.equals(row.getTxnId()));
        if (exists) {
            return;
        }
        AlertTransactionLink link = new AlertTransactionLink();
        link.setAlertId(alertId);
        link.setTxnId(txnId);
        link.setPrimaryLink(primary);
        alertTransactionLinkRepository.save(link);
    }

    /**
     * Sets the alert's compliance_case_id FK and wires the case_alerts.source_alert_id back-pointer.
     */
    @Transactional
    public void linkAlertToCase(Alert alert, ComplianceCase complianceCase, CaseAlert caseAlert) {
        if (alert == null || complianceCase == null || complianceCase.getId() == null) {
            return;
        }
        alert.setComplianceCaseId(complianceCase.getId());
        alertRepository.save(alert);
        if (caseAlert != null && alert.getAlertId() != null) {
            caseAlert.setSourceAlertId(alert.getAlertId());
        }
        if (alert.getTxnId() != null) {
            ensureCaseTransaction(complianceCase, alert.getTxnId());
        }
    }

    /**
     * Links a SAR to every alert tied to its compliance case (and explicit alert id when provided).
     */
    @Transactional
    public void linkSarToCaseAlerts(Long sarId, Long complianceCaseId, Long explicitAlertId) {
        if (sarId == null) {
            return;
        }
        if (explicitAlertId != null) {
            persistSarAlertLink(sarId, explicitAlertId);
        }
        if (complianceCaseId == null) {
            return;
        }
        alertRepository.findByComplianceCaseId(complianceCaseId)
                .forEach(a -> persistSarAlertLink(sarId, a.getAlertId()));
    }

    /**
     * Ensures a case_transactions row exists so case network graphs include the triggering txn.
     */
    @Transactional
    public void ensureCaseTransaction(ComplianceCase complianceCase, Long txnId) {
        if (complianceCase == null || txnId == null) {
            return;
        }
        boolean linked = caseTransactionRepository.findByComplianceCase_Id(complianceCase.getId()).stream()
                .anyMatch(ct -> ct.getTransaction() != null && txnId.equals(ct.getTransaction().getTxnId()));
        if (linked) {
            return;
        }
        TransactionEntity txnRef = transactionRepository.getReferenceById(txnId);
        CaseTransaction caseTransaction = new CaseTransaction();
        caseTransaction.setComplianceCase(complianceCase);
        caseTransaction.setTransaction(txnRef);
        caseTransaction.setRelationshipType("PRIMARY");
        caseTransactionRepository.save(caseTransaction);
    }

    private void persistSarAlertLink(Long sarId, Long alertId) {
        if (alertId == null) {
            return;
        }
        boolean exists = sarAlertLinkRepository.findBySarId(sarId).stream()
                .anyMatch(row -> alertId.equals(row.getAlertId()));
        if (exists) {
            return;
        }
        SarAlertLink link = new SarAlertLink();
        link.setSarId(sarId);
        link.setAlertId(alertId);
        sarAlertLinkRepository.save(link);
        log.debug("Linked SAR {} to alert {}", sarId, alertId);
    }
}
