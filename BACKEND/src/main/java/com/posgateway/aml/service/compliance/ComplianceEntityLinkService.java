package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.AlertTransactionLink;
import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SarAlertLink;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.compliance.SarAlertLinkRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the indexed bridges between fraud alerts, cases, SARs and transactions. Transactions are
 * referenced by ID only (V264, residency contract I4): decision record id, txnRef and site, plus the
 * legacy cloud txn id for rows from legacy ingest. No card identity is copied onto workflow rows.
 */
@Service
public class ComplianceEntityLinkService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEntityLinkService.class);

    private final AlertRepository alertRepository;
    private final AlertTransactionLinkRepository alertTransactionLinkRepository;
    private final SarAlertLinkRepository sarAlertLinkRepository;
    private final CaseTransactionRepository caseTransactionRepository;
    private final TransactionReferenceResolver references;

    public ComplianceEntityLinkService(AlertRepository alertRepository,
                                         AlertTransactionLinkRepository alertTransactionLinkRepository,
                                         SarAlertLinkRepository sarAlertLinkRepository,
                                         CaseTransactionRepository caseTransactionRepository,
                                         TransactionReferenceResolver references) {
        this.alertRepository = alertRepository;
        this.alertTransactionLinkRepository = alertTransactionLinkRepository;
        this.sarAlertLinkRepository = sarAlertLinkRepository;
        this.caseTransactionRepository = caseTransactionRepository;
        this.references = references;
    }

    /**
     * After a legacy-ingest alert is created: stamp the transaction's ID reference on the alert (when
     * the caller has not already) and register the primary alert_transactions row.
     */
    @Transactional
    public void registerNewFraudAlert(Alert alert, TransactionEntity transaction) {
        if (alert == null || alert.getAlertId() == null) {
            return;
        }
        TransactionReference reference = alert.getTransactionReference();
        if (reference.edgeAssessmentId() == null && reference.txnRef() == null) {
            TransactionReference resolved = transaction != null
                    ? references.forLegacyTransaction(transaction)
                    : references.forLegacyTransactionId(alert.getPspId(), alert.getTxnId());
            if (resolved != null) {
                alert.applyTransactionReference(resolved);
                reference = resolved;
            }
        }
        if (!reference.isEmpty()) {
            linkAlertToTransaction(alert.getAlertId(), reference, true);
        }
        alertRepository.save(alert);
    }

    /**
     * Idempotently inserts an alert_transactions row (by reference) for consolidated or primary
     * transaction associations.
     */
    @Transactional
    public void linkAlertToTransaction(Long alertId, TransactionReference reference, boolean primary) {
        if (alertId == null || reference == null || reference.isEmpty()) {
            return;
        }
        boolean exists = alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(alertId).stream()
                .anyMatch(row -> reference.sameTransactionAs(row.getTransactionReference()));
        if (exists) {
            return;
        }
        AlertTransactionLink link = new AlertTransactionLink();
        link.setAlertId(alertId);
        link.applyTransactionReference(reference);
        link.setPrimaryLink(primary);
        alertTransactionLinkRepository.save(link);
    }

    /**
     * Sets the alert's compliance_case_id FK, wires the case_alerts.source_alert_id back-pointer and
     * links the alert's transaction reference to the case.
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
        TransactionReference reference = alert.getTransactionReference();
        if (!reference.isEmpty()) {
            ensureCaseTransaction(complianceCase, reference, "PRIMARY");
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
     * Ensures a case_transactions row (by reference) exists so the case timeline and network views
     * include the transaction. Idempotent per case and transaction.
     */
    @Transactional
    public void ensureCaseTransaction(ComplianceCase complianceCase, TransactionReference reference,
                                      String relationshipType) {
        if (complianceCase == null || complianceCase.getId() == null || reference == null || reference.isEmpty()) {
            return;
        }
        boolean linked = caseTransactionRepository.findByComplianceCase_Id(complianceCase.getId()).stream()
                .anyMatch(ct -> reference.sameTransactionAs(ct.getTransactionReference()));
        if (linked) {
            return;
        }
        caseTransactionRepository.save(new CaseTransaction(complianceCase, reference, relationshipType, null));
    }

    /**
     * The ID references of a case's linked transactions, in link order, for a SAR draft of that case.
     * Tenant-scoped: an empty list unless the case belongs to {@code pspId} (a null {@code pspId}, the
     * platform scope, accepts the case's own PSP).
     */
    @Transactional(readOnly = true)
    public java.util.List<TransactionReference> caseTransactionReferences(ComplianceCase complianceCase, Long pspId) {
        if (complianceCase == null || complianceCase.getId() == null
                || (pspId != null && complianceCase.getPspId() != null && !pspId.equals(complianceCase.getPspId()))) {
            return java.util.List.of();
        }
        return caseTransactionRepository.findByComplianceCase_Id(complianceCase.getId()).stream()
                .map(CaseTransaction::getTransactionReference)
                .filter(reference -> !reference.isEmpty())
                .toList();
    }

    /** Inserts one sar_alerts row unless it exists. */
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
