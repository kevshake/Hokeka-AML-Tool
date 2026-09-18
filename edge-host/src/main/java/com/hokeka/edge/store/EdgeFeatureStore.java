package com.hokeka.edge.store;

import java.util.Map;
import java.util.Optional;

/**
 * The on-premises datastore behind the edge engine.
 *
 * <p>Everything a client's transactions produce stays on their premises: the raw transactions, the
 * velocity counters derived from them, and the last verified rule bundle. The control plane only ever
 * receives the aggregate metrics windows.
 *
 * <p>Two jobs, both of which the edge previously could not do at all:
 * <ol>
 *   <li><b>History for future rule checks.</b> Transactions are persisted locally and rolled into
 *       velocity counters, so a rule can be evaluated against what this card/merchant did before.
 *       Without a store the edge only ever saw the single request body, so every rule using a
 *       velocity/history feature silently never fired (a missing feature evaluates to false).</li>
 *   <li><b>Rule bundle durability.</b> The verified bundle is persisted, so a restart resumes
 *       enforcing immediately instead of HOLDing all traffic until the next successful poll.</li>
 * </ol>
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
     * Persist the transaction, the decision it received, and advance its velocity counters, so
     * subsequent evaluations can be checked against it and an investigator can see what was decided
     * and which rules fired. Must never throw — a store failure degrades enrichment, it does not fail
     * the decision.
     *
     * @param decision the evaluation outcome; may be null if unavailable
     */
    void recordTransaction(String panHash, Map<String, Object> features,
                           com.hokeka.edge.EdgeRuleInterpreter.Decision decision);

    /** Persist the last verified rule IR so a restart can resume enforcing without a poll. */
    void saveRuleBundle(long version, byte[] ruleIrJson);

    /** The persisted rule IR, if any. */
    Optional<byte[]> loadRuleBundle();
}
