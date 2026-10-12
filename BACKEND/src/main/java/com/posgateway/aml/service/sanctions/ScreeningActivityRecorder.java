package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.sanctions.ScreeningActivity;
import com.posgateway.aml.repository.sanctions.ScreeningActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists in-memory transaction screening results to screening_activities for regulator evidence.
 */
@Service
public class ScreeningActivityRecorder {

    private final ScreeningActivityRepository screeningActivityRepository;

    public ScreeningActivityRecorder(ScreeningActivityRepository screeningActivityRepository) {
        this.screeningActivityRepository = screeningActivityRepository;
    }

    /**
     * Writes one screening activity row summarizing matches for a transaction-time screening run.
     */
    @Transactional
    public ScreeningActivity recordTransactionScreening(TransactionEntity transaction,
                                                        RealTimeTransactionScreeningService.TransactionScreeningResult result,
                                                        String subjectType,
                                                        String subjectName) {
        ScreeningActivity activity = new ScreeningActivity();
        activity.setPspId(transaction.getPspId());
        if (transaction.getMerchantId() != null) {
            try {
                activity.setMerchantId(Long.parseLong(transaction.getMerchantId()));
            } catch (NumberFormatException ignored) {
                // Non-numeric merchant ids are stored only on the txn row.
            }
        }
        activity.setTxnId(transaction.getTxnId());
        activity.setSubjectType(subjectType != null ? subjectType : "TRANSACTION");
        activity.setSubjectName(subjectName);
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
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("txnId", transaction.getTxnId());
            evidence.put("blocking", result.shouldBlock());
            evidence.put("unavailable", result.isScreeningUnavailable());
            evidence.put("matchCount", activity.getMatchCount());
            activity.setEvidence(evidence);
        }
        return screeningActivityRepository.save(activity);
    }
}
