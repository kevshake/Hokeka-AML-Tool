package com.hokeka.edge.store;

import java.util.Map;
import java.util.Optional;

/**
 * The on-premises velocity and rule-bundle store behind the edge engine (Aerospike).
 *
 * <p>Two jobs:
 * <ol>
 *   <li><b>Velocity history for rule checks.</b> Every evaluation advances the card's hourly
 *       counters, so a rule can be evaluated against what this card did before. Without a store the
 *       edge only ever saw the single request body, so every rule using a velocity feature silently
 *       never fired (a missing feature evaluates to false).</li>
 *   <li><b>Rule bundle durability.</b> The verified bundle is persisted, so a restart resumes
 *       enforcing immediately instead of HOLDing all traffic until the next successful poll.</li>
 * </ol>
 *
 * <p>Evaluations themselves are recorded on the Edge Ledger ({@code com.hokeka.edge.ledger}), not
 * here.
 */
public interface EdgeFeatureStore {

    /** True when a real datastore is attached; false for the no-op fallback. */
    boolean available();

    /**
     * Locally-derived history features for this transaction, to be merged into the feature map
     * before evaluation (velocity counts and amount sums for the card).
     *
     * @param panHash the card identifier the counters are keyed on; null/blank yields an empty map
     * @return feature name → value, never null
     */
    Map<String, Object> deriveFeatures(String panHash);

    /**
     * Like {@link #deriveFeatures(String)} but reports whether the store was unavailable for this
     * lookup (distinct from "no history yet").
     */
    default FeatureDerivation deriveFeaturesDetailed(String panHash) {
        return new FeatureDerivation(deriveFeatures(panHash), !available());
    }

    /**
     * Advance the card's velocity counters by one transaction of {@code amountCents}, so subsequent
     * evaluations see it. Must never throw — a store failure degrades enrichment, it does not fail
     * the decision.
     *
     * @param panHash     the card identifier; null/blank records nothing
     * @param amountCents the amount in minor units (negative values count as 0)
     */
    void recordVelocity(String panHash, long amountCents);

    /** Persist the last verified rule IR so a restart can resume enforcing without a poll. */
    void saveRuleBundle(long version, byte[] ruleIrJson);

    /** The persisted rule IR, if any. */
    Optional<byte[]> loadRuleBundle();
}
