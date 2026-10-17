package com.hokeka.edge.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The on-premises velocity and rule-bundle store behind the edge engine (Aerospike).
 *
 * <p>Three jobs:
 * <ol>
 *   <li><b>History for rule checks.</b> Every evaluation advances the card's hourly and daily
 *       buckets and its bounded list of recent evaluations (plus the device's and IP's), so the
 *       typology features ({@link CardHistoryFeatures}) see what this card did before.</li>
 *   <li><b>Compliance counters.</b> Generic site-scoped counters for CTR card-day totals and Edge
 *       Config limits ({@link #readCounters}, {@link #addCounters}).</li>
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
     * Legacy velocity features for a card ({@code pan_txn_count_1h}/{@code _24h},
     * {@code pan_amount_sum_24h}); see {@link #derive(TxnContext)} for the full set.
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
     * The card's, device's and IP's history in one round trip.
     *
     * @return the history, or empty when the store cannot be read (callers must not treat that as
     *         "no history": a new card and an unreadable store are different facts)
     */
    default Optional<CardHistory> readHistory(TxnContext txn) {
        boolean needsHistory = txn.hasCard() || txn.deviceKey() != null || txn.ipAddress() != null;
        return needsHistory ? Optional.empty() : Optional.of(CardHistory.empty());
    }

    /**
     * Every derived feature for one evaluation. When the history cannot be read only the
     * request-level features (amount in major units, normalised direction, amount ending) are
     * derived and the result is flagged unavailable — history features are then absent, never
     * invented from an empty history.
     */
    default FeatureDerivation derive(TxnContext txn) {
        Optional<CardHistory> history = readHistory(txn);
        if (history.isEmpty()) {
            return new FeatureDerivation(CardHistoryFeatures.deriveRequestOnly(txn), txn.hasCard());
        }
        return new FeatureDerivation(CardHistoryFeatures.derive(history.get(), txn), false);
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

    /**
     * Records one evaluated transaction into every history structure {@link #derive} reads. Must
     * never throw.
     *
     * @param txn             the transaction
     * @param structuringBand whether it is a cash transaction in the structuring band
     */
    default void record(TxnContext txn, boolean structuringBand) {
        recordVelocity(txn.panHash(), txn.amountCentsOrZero());
    }

    /**
     * A counter record: {@code set} and user key (the store appends the site id).
     *
     * @param set the Aerospike set
     * @param key the user key without the site suffix
     */
    record CounterKey(String set, String key) {
    }

    /**
     * Reads counter bins for many keys in one round trip.
     *
     * @param keys the records
     * @param bins bin names to read
     * @return {@code values[i][j]} = bin j of key i (0 when absent), or empty when the store is
     *         unavailable (callers must then fail visibly, never assume zero)
     */
    default Optional<long[][]> readCounters(List<CounterKey> keys, List<String> bins) {
        return Optional.empty();
    }

    /**
     * Atomically adds to counter bins and returns the totals after the add.
     *
     * @param key        the record
     * @param ttlSeconds record time-to-live
     * @param increments bin → amount to add
     * @return the bins' values after the add (same order as {@code increments}' iteration), or empty
     *         when the store is unavailable
     */
    default Optional<long[]> addCounters(CounterKey key, int ttlSeconds, Map<String, Long> increments) {
        return Optional.empty();
    }

    /** Persist the last verified rule IR so a restart can resume enforcing without a poll. */
    void saveRuleBundle(long version, byte[] ruleIrJson);

    /** The persisted rule IR, if any. */
    Optional<byte[]> loadRuleBundle();
}
