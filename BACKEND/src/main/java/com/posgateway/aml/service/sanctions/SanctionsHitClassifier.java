package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.model.ScreeningResult;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a screening result as a sanctions hit, the way the merchant blocklist (R10b) and the
 * Edge (contract 6.5) need it: only sanctions-list matches count. A PEP/RCA match (a {@code pepLevel},
 * or a list whose name says PEP, exactly {@code AmlScreeningOrchestrator}'s rule) is enhanced due
 * diligence, never a sanctions hit — the regression fixed in aml-microservice 2e17fffa2, where
 * PEPs came back without their pepLevel and were reported as sanctions matches.
 *
 * <p>Among sanctions-list matches the confirmed/potential split is
 * {@code RealTimeTransactionScreeningService.isConfirmedHit}'s: a match at or above the confirmed
 * threshold, a DOB-confirmed match, or a {@code MATCH} result whose matches carry no score (fail
 * closed) is confirmed; anything else is a potential match that a person must review.
 */
public final class SanctionsHitClassifier {

    /** aml-microservice FLAGGED threshold: a top score at or above it makes the result MATCH. */
    public static final double AML_FLAGGED_THRESHOLD = 0.95;

    private SanctionsHitClassifier() {
    }

    /** Outcome for one screened subject. */
    public enum Verdict {
        /** The screen did not complete; nothing may be concluded. */
        UNKNOWN,
        /** No sanctions-list match (PEP-only matches included). */
        CLEAR,
        /** Sanctions-list candidates below the confirmed bar: review, but no block. */
        POTENTIAL,
        /** A confirmed sanctions-list hit. */
        CONFIRMED
    }

    /**
     * Classifies {@code result}.
     *
     * @param result             the screening result (null means not screened)
     * @param confirmedThreshold {@code screening.realtime.confirmed-match-threshold}
     */
    public static Verdict classify(ScreeningResult result, double confirmedThreshold) {
        if (result == null || result.getStatus() == null
                || result.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            return Verdict.UNKNOWN;
        }
        List<ScreeningResult.Match> sanctions = result.getMatches() == null ? List.of()
                : result.getMatches().stream().filter(m -> m != null && !isPepMatch(m.getListName(), m.getPepLevel()))
                .toList();
        if (sanctions.isEmpty()) {
            return Verdict.CLEAR;
        }
        double bar = Math.min(confirmedThreshold, AML_FLAGGED_THRESHOLD);
        for (ScreeningResult.Match match : sanctions) {
            if (match.getMatchType() == ScreeningResult.MatchType.DOB_CONFIRMED) {
                return Verdict.CONFIRMED;
            }
            Double score = match.getSimilarityScore();
            if (score == null && result.getStatus() == ScreeningResult.ScreeningStatus.MATCH) {
                return Verdict.CONFIRMED;
            }
            if (score != null && score >= bar) {
                return Verdict.CONFIRMED;
            }
        }
        return Verdict.POTENTIAL;
    }

    /** PEP/RCA match: a pepLevel, or a list name that says PEP (the orchestrator's rule). */
    public static boolean isPepMatch(String listName, String pepLevel) {
        if (pepLevel != null && !pepLevel.isBlank()) {
            return true;
        }
        return listName != null && listName.toUpperCase(Locale.ROOT).contains("PEP");
    }
}
