package com.hokeka.edge.ledger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One {@code edge_evaluations} row as the Edge writes it for a live {@code /edge/evaluate} call.
 *
 * <p>Built by {@link LedgerEvaluations#fromEvaluation}, which has already applied the contract's
 * hygiene: {@code features} is the §2.4-sanitised bag, the typed transaction columns hold only
 * values matching their contract formats, and {@code decision}/{@code evaluator}/{@code failClosedCode}
 * use the contract vocabularies. Instances are immutable.
 *
 * @param edgeAssessmentId the evaluation id returned to the PSP
 * @param txnRef           the PSP's {@code txn_id} (already Luhn-checked), or null
 * @param siteId           the Edge Site id
 * @param nodeId           this node's {@code edgeId}
 * @param evaluatedAt      evaluation instant, millisecond precision (class T)
 * @param panHash          card hash (class E; own column, never in {@code features}), or null
 * @param amountCents      amount in minor units, or null
 * @param currency         ISO 4217 code, or null
 * @param merchantId       cloud merchant id, or null
 * @param mcc              4-digit MCC, or null
 * @param countryCode      ISO 3166 alpha-2, or null
 * @param channel          channel code, or null
 * @param features         sanitised feature bag
 * @param decision         {@code ALLOW} / {@code ALERT} / {@code HOLD} / {@code BLOCK}
 * @param score            rule score sum, &ge; 0
 * @param triggeredRuleIds rule ids that fired (unique)
 * @param ruleVersionIds   rule version ids that fired (unique)
 * @param reasons          free-text reasons (class T: kept on the Edge, never in the decision record)
 * @param bundleVersion    active bundle version, -1 before the first bundle
 * @param bundleHash       {@code sha256:<hex>} of the active bundle, or null when none
 * @param evaluator        {@code native} / {@code java} / {@code none}
 * @param latencyUs        evaluation latency in microseconds
 * @param failClosedCode   fail-closed gate that answered, or null
 * @param ctrRequired      CTR flag
 * @param sanctionsHits    local sanctions matches ({@code sanctionsHitDetail} objects)
 * @param typologyCodes    typology codes
 */
public record LedgerEvaluation(UUID edgeAssessmentId,
                               String txnRef,
                               String siteId,
                               String nodeId,
                               Instant evaluatedAt,
                               String panHash,
                               Long amountCents,
                               String currency,
                               String merchantId,
                               String mcc,
                               String countryCode,
                               String channel,
                               Map<String, Object> features,
                               String decision,
                               int score,
                               List<Long> triggeredRuleIds,
                               List<Long> ruleVersionIds,
                               List<String> reasons,
                               long bundleVersion,
                               String bundleHash,
                               String evaluator,
                               long latencyUs,
                               String failClosedCode,
                               boolean ctrRequired,
                               List<Map<String, Object>> sanctionsHits,
                               List<String> typologyCodes) {

    /** Fail-closed code for a failed evaluation + outbox transaction (contract §4.3). */
    public static final String LEDGER_UNAVAILABLE = "LEDGER_UNAVAILABLE";

    /** Normalises nulls to empty collections and the instant to millisecond precision. */
    public LedgerEvaluation {
        Objects.requireNonNull(edgeAssessmentId, "edgeAssessmentId");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(evaluator, "evaluator");
        evaluatedAt = evaluatedAt.truncatedTo(ChronoUnit.MILLIS);
        // Not Map.copyOf: a JSON null is a legitimate scalar feature value (§2.4) and copyOf rejects it.
        features = features == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(features));
        triggeredRuleIds = triggeredRuleIds == null ? List.of() : List.copyOf(triggeredRuleIds);
        ruleVersionIds = ruleVersionIds == null ? List.of() : List.copyOf(ruleVersionIds);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        sanctionsHits = sanctionsHits == null ? List.of() : List.copyOf(sanctionsHits);
        typologyCodes = typologyCodes == null ? List.of() : List.copyOf(typologyCodes);
    }

    /** {@code sanctionsHit} as defined by the contract: at least one local match. */
    public boolean sanctionsHit() {
        return !sanctionsHits.isEmpty();
    }

    /**
     * The same evaluation with the Edge compliance overlay's outcome (R10a): the decision record's
     * {@code ctrRequired} and {@code typologyCodes}.
     *
     * @param ctr   CTR flag
     * @param codes typology codes ({@code ^[A-Z][A-Z0-9_]{1,63}$})
     */
    public LedgerEvaluation withCompliance(boolean ctr, List<String> codes) {
        return new LedgerEvaluation(edgeAssessmentId, txnRef, siteId, nodeId, evaluatedAt, panHash,
                amountCents, currency, merchantId, mcc, countryCode, channel, features, decision, score,
                triggeredRuleIds, ruleVersionIds, reasons, bundleVersion, bundleHash, evaluator, latencyUs,
                failClosedCode, ctr, sanctionsHits, codes);
    }

    /**
     * The same evaluation answered by the ledger fail-closed gate instead (§4.3): {@code HOLD},
     * {@code evaluator = none}, no triggered rules, score 0, {@code LEDGER_UNAVAILABLE}. The id,
     * time, transaction columns and features are kept, because the ledger row (once written) must
     * describe the transaction the PSP was answered for.
     *
     * @param reason the operator-facing reason returned to the PSP
     */
    public LedgerEvaluation asLedgerUnavailableHold(String reason) {
        return new LedgerEvaluation(edgeAssessmentId, txnRef, siteId, nodeId, evaluatedAt, panHash,
                amountCents, currency, merchantId, mcc, countryCode, channel, features,
                "HOLD", 0, List.of(), List.of(), List.of(reason), bundleVersion, bundleHash,
                "none", latencyUs, LEDGER_UNAVAILABLE, ctrRequired, sanctionsHits, typologyCodes);
    }
}
