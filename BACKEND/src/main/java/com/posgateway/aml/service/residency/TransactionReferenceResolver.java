package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.service.assessment.AssessmentRecordingScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the {@link TransactionReference} that workflow records store instead of a link to the cloud
 * {@code transactions} table (plan work package R8, contract invariant I4), and looks up the decision
 * records those references name.
 *
 * <p>Legacy rule (the same one V264 used to back-fill): a legacy transaction's assessment is the
 * TXN-triggered {@code assessments} row of the same PSP and txn id, preferring one adopted from the
 * Edge, then the oldest. Its {@code edge_assessment_id}, else its own id, is the reference (contract
 * section 11: the cloud assessment id becomes the Edge id at back-fill). The txnRef is the
 * assessment's, else the PSP's {@code client_reference} when it is a valid txnRef.
 *
 * <p>Every lookup is PSP-scoped: a reference never resolves across tenants.
 */
@Service
public class TransactionReferenceResolver {

    private final AssessmentRepository assessments;
    private final TransactionRepository transactions;

    public TransactionReferenceResolver(AssessmentRepository assessments, TransactionRepository transactions) {
        this.assessments = assessments;
        this.transactions = transactions;
    }

    /** The reference to an Edge decision record (an {@code assessments} row with an Edge id). */
    public static TransactionReference forDecisionRecord(Assessment record) {
        if (record == null) {
            return null;
        }
        return new TransactionReference(record.getTxnId(), record.getEdgeAssessmentId(), record.getTxnRef(),
                record.getSiteId());
    }

    /** The reference of a legacy cloud-ingested transaction, built from the row already in memory. */
    @Transactional(readOnly = true)
    public TransactionReference forLegacyTransaction(TransactionEntity transaction) {
        if (transaction == null || transaction.getTxnId() == null) {
            return null;
        }
        Optional<Assessment> assessment = legacyAssessment(transaction.getPspId(), transaction.getTxnId());
        return build(transaction.getTxnId(), assessment, transaction.getClientReference());
    }

    /**
     * The reference of a legacy transaction known only by id, scoped to {@code pspId}. Without a PSP the
     * reference carries the legacy id alone: nothing is resolved across tenants.
     */
    @Transactional(readOnly = true)
    public TransactionReference forLegacyTransactionId(Long pspId, Long txnId) {
        if (txnId == null) {
            return null;
        }
        if (pspId == null) {
            return TransactionReference.ofLegacyTransaction(txnId);
        }
        Optional<Assessment> assessment = legacyAssessment(pspId, txnId);
        String clientReference = assessment.map(Assessment::getTxnRef).isPresent()
                ? null
                : transactions.findClientReferenceByPspIdAndTxnId(pspId, txnId).orElse(null);
        return build(txnId, assessment, clientReference);
    }

    /**
     * The reference of the assessment a finding belongs to: an Edge decision record's own reference,
     * or, for a legacy TXN assessment, its transaction's reference.
     */
    @Transactional(readOnly = true)
    public TransactionReference forAssessment(Assessment assessment) {
        if (assessment == null) {
            return null;
        }
        if (assessment.getEdgeAssessmentId() != null) {
            return forDecisionRecord(assessment);
        }
        if (assessment.getTxnId() != null) {
            return forLegacyTransactionId(assessment.getPspId(), assessment.getTxnId());
        }
        return null;
    }

    /**
     * Decision records (and legacy assessments) named by {@code references}, keyed by the reference's
     * {@code edgeAssessmentId}, for one PSP. Used to show class-D fields (evaluation time, decision,
     * merchant id) next to workflow rows; never transaction detail.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Assessment> decisionRecords(Long pspId, Collection<TransactionReference> references) {
        if (pspId == null || references == null || references.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (TransactionReference reference : references) {
            if (reference != null && reference.edgeAssessmentId() != null) {
                ids.add(reference.edgeAssessmentId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Assessment> byReference = new HashMap<>();
        for (Assessment a : assessments.findReferencedByPsp(pspId, ids)) {
            UUID key = a.getEdgeAssessmentId() != null ? a.getEdgeAssessmentId() : a.getId();
            byReference.putIfAbsent(key, a);
        }
        return byReference;
    }

    /** The legacy rule's assessment: in-flight scope first (same PSP and txn), else the stored pick. */
    private Optional<Assessment> legacyAssessment(Long pspId, Long txnId) {
        if (pspId == null) {
            return Optional.empty();
        }
        List<Assessment> candidates = assessments.findByPspIdAndTxnIdAndTriggerTypeOrderByCreatedAtAscIdAsc(
                pspId, txnId, AssessmentTriggerType.TXN);
        if (candidates.isEmpty()) {
            AssessmentRecordingScope.Scope scope = AssessmentRecordingScope.current();
            if (scope != null && scope.assessmentId() != null && txnId.equals(scope.txnId())
                    && pspId.equals(scope.pspId())) {
                return assessments.findByIdAndPspId(scope.assessmentId(), pspId);
            }
            return Optional.empty();
        }
        return candidates.stream()
                .min(Comparator.comparing((Assessment a) -> a.getEdgeAssessmentId() == null));
    }

    /** Applies the legacy rule to an optional assessment and the PSP's client reference. */
    private static TransactionReference build(Long txnId, Optional<Assessment> assessment, String clientReference) {
        UUID edgeId = assessment.map(a -> a.getEdgeAssessmentId() != null ? a.getEdgeAssessmentId() : a.getId())
                .orElse(null);
        String assessedRef = assessment.map(Assessment::getTxnRef).map(TransactionReference::safeTxnRef).orElse(null);
        String txnRef = assessedRef != null ? assessedRef : TransactionReference.safeTxnRef(clientReference);
        String siteId = assessment.map(Assessment::getSiteId).orElse(null);
        return new TransactionReference(txnId, edgeId, txnRef, siteId);
    }
}
