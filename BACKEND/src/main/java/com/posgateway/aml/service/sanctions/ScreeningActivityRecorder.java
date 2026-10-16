package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.sanctions.ScreeningActivity;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.sanctions.ScreeningActivityRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists in-memory transaction screening results to screening_activities for regulator evidence.
 * The screened transaction is referenced by ID only (V264): decision record id / txnRef / site and
 * the legacy cloud id. A counterparty name is transaction data and is never stored; only a merchant
 * subject (cloud master data) keeps its screened name.
 */
@Service
public class ScreeningActivityRecorder {

    private final ScreeningActivityRepository screeningActivityRepository;
    private final TransactionReferenceResolver transactionReferences;

    public ScreeningActivityRecorder(ScreeningActivityRepository screeningActivityRepository,
                                     TransactionReferenceResolver transactionReferences) {
        this.screeningActivityRepository = screeningActivityRepository;
        this.transactionReferences = transactionReferences;
    }

    /**
     * Writes one screening activity row summarizing matches for a transaction-time screening run.
     *
     * @param subjectEntityType entity type of the reported match (MERCHANT, COUNTERPARTY, …); the
     *                          name is kept only for MERCHANT
     */
    @Transactional
    public ScreeningActivity recordTransactionScreening(TransactionEntity transaction,
                                                        RealTimeTransactionScreeningService.TransactionScreeningResult result,
                                                        String subjectType,
                                                        String subjectName,
                                                        String subjectEntityType) {
        ScreeningActivity activity = new ScreeningActivity();
        activity.setPspId(transaction.getPspId());
        if (transaction.getMerchantId() != null) {
            try {
                activity.setMerchantId(Long.parseLong(transaction.getMerchantId()));
            } catch (NumberFormatException ignored) {
                // Non-numeric merchant ids are stored only on the txn row.
            }
        }
        TransactionReference reference = transactionReferences.forLegacyTransaction(transaction);
        activity.applyTransactionReference(reference);
        activity.setSubjectType(subjectType != null ? subjectType : "TRANSACTION");
        activity.setSubjectName("MERCHANT".equalsIgnoreCase(subjectEntityType) ? subjectName : null);
        if (result != null) {
            activity.setHit(result.hasMatches());
            activity.setBlocking(result.shouldBlock());
            activity.setMatchCount(result.getMatches() != null ? result.getMatches().size() : 0);
            activity.setHighestScore(result.getMatches().stream()
                    .map(m -> m.getScreeningResult() != null ? m.getScreeningResult().getHighestMatchScore() : null)
                    .filter(score -> score != null)
                    .mapToDouble(Double::doubleValue)
                    .max().orElse(0.0));
            activity.setReason(result.hasMatches()
                    ? "Transaction screening match (" + activity.getMatchCount() + " hit(s))"
                    : "Clear");
            Map<String, Object> evidence = new HashMap<>(reference != null ? reference.toMap() : Map.of());
            evidence.put("blocking", result.shouldBlock());
            evidence.put("unavailable", result.isScreeningUnavailable());
            evidence.put("matchCount", activity.getMatchCount());
            if (subjectEntityType != null) {
                evidence.put("subjectEntityType", subjectEntityType);
            }
            activity.setEvidence(evidence);
        }
        return screeningActivityRepository.save(activity);
    }
}
