package com.hokeka.edge.store;

import java.util.Map;
import java.util.Optional;

/**
 * Fallback used when no on-premises datastore is configured or reachable.
 *
 * <p>The edge stays fully functional: it evaluates whatever features the caller supplies. It simply
 * cannot enrich with local history, so rules that depend on velocity/history features will not fire —
 * which is why {@link #available()} is surfaced on {@code /edge/status}, so a silently
 * history-blind node is visible rather than looking healthy.
 */
public class NoOpFeatureStore implements EdgeFeatureStore {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Map<String, Object> deriveFeatures(String panHash) {
        return deriveFeaturesDetailed(panHash).features();
    }

    @Override
    public FeatureDerivation deriveFeaturesDetailed(String panHash) {
        boolean needsHistory = panHash != null && !panHash.isBlank();
        return new FeatureDerivation(Map.of(), needsHistory);
    }

    /** No store attached: there are no counters to advance (intentional-stub: the no-store fallback). */
    @Override
    public void recordVelocity(String panHash, long amountCents) {
        // nothing to record without a store
    }

    @Override
    public void saveRuleBundle(long version, byte[] ruleIrJson) {
        // no durability without a store
    }

    @Override
    public Optional<byte[]> loadRuleBundle() {
        return Optional.empty();
    }
}
