package com.posgateway.aml.service.edge.typology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which cloud rule inputs ({@code #features['k']}, {@code #tx.prop}) exist on the Edge, under what
 * Edge feature name, and why the others do not.
 *
 * <p>The Edge derives these features in {@code com.hokeka.edge.store.CardHistoryFeatures} and
 * {@code com.hokeka.edge.typology.ComplianceOverlay} (edge-host) from its site-local Aerospike
 * history and the Edge config; their names and presence rules follow the cloud producer
 * {@code RuleFeatureEnrichmentService}, so a compiled rule sees the same value the cloud rule saw.
 * Boolean list-hit features compile to {@code in_list} conditions instead (see
 * {@link #listHit(String)}). Keep this class and the edge-host derivation in step: the compiler
 * test {@code EdgeTypologyCompilationTest} pins every seeded rule's outcome.
 */
public final class EdgeFeatureCatalog {

    private EdgeFeatureCatalog() {
    }

    /** Cloud feature key → Edge feature name, for features the Edge derives (or the PSP sends). */
    private static final Map<String, String> FEATURES = new LinkedHashMap<>();
    /** Cloud feature key → reason it cannot be evaluated on the Edge. */
    private static final Map<String, String> NOT_ON_EDGE = new LinkedHashMap<>();
    /** {@code #tx} property → Edge feature name. */
    private static final Map<String, String> TX = new LinkedHashMap<>();
    /** {@code #tx} property → reason it cannot be evaluated on the Edge. */
    private static final Map<String, String> TX_NOT_ON_EDGE = new LinkedHashMap<>();

    /** The kinds of list-hit macro. */
    public enum ListHitKind {
        /** {@code in_list(field, list)}. */
        SINGLE,
        /** {@code variable_blacklist_hit}: pan hash or device on their blacklists. */
        PAN_OR_DEVICE,
        /** {@code reference_keyword_blacklist}: payment reference contains a blacklisted keyword. */
        KEYWORDS
    }

    /**
     * A boolean cloud feature that is a list-membership test on the Edge.
     *
     * @param kind  macro kind
     * @param field Edge feature the list is matched against
     * @param list  bundle list name
     */
    public record ListHit(ListHitKind kind, String field, String list) {
    }

    private static final Map<String, ListHit> LIST_HITS = new LinkedHashMap<>();

    /** Edge feature holding the device identifier ({@code device_id}, else {@code device_fingerprint}). */
    public static final String DEVICE_KEY = "device_key";
    /** Edge feature holding the lower-cased payment reference. */
    public static final String REFERENCE_LC = "reference_lc";
    /**
     * Keywords the cloud scans payment references for ({@code RuleFeatureEnrichmentService
     * .containsBlacklistedKeyword}); a keyword counts only while it is on the PSP's
     * {@code reference_keyword} blacklist.
     */
    public static final Set<String> REFERENCE_KEYWORDS = Set.of("casino", "crypto", "gambling", "offshore", "shell");

    private static final String SCREENING = "merchant/counterparty screening result: the Edge sanctions matcher and "
            + "merchant blocklist are work package R10b";
    private static final String MERCHANT_NAMES = "needs merchant legal-name master data, which is not distributed to "
            + "the Edge (a per-PSP name map can exceed the 1 MiB Aerospike record that persists the bundle)";
    private static final String DISPUTES = "chargeback/dispute records reach the cloud from scheme and Verifi feeds; "
            + "the Edge has no dispute feed";
    private static final String PEER = "cross-merchant peer-group aggregate computed only in the cloud";
    private static final String SCHEME = "scheme monitoring programme stage (VFMP/HECM) computed only in the cloud";
    private static final String ML = "ML/graph score is not computed on the Edge (Edge model artifact is later work)";
    private static final String MERCHANT_AGG = "merchant-wide aggregate across sites is not derived on the Edge";

    static {
        for (String same : new String[]{
                "amount", "amount_cents", "currency", "merchant_id", "pan_hash", "country_code", "channel", "mcc",
                "pan_txn_count_1m", "pan_txn_count_5m", "pan_txn_count_1h", "pan_txn_count_24h", "pan_amount_sum_24h",
                "is_first_transaction", "days_since_last_activity", "is_new_country", "is_new_currency",
                "txn_count_deviation_ratio", "volume_deviation_ratio", "avg_amount_spike_ratio",
                "zscore_amount_vs_pan_history", "volume_ratio_t1_t2", "txn_count_window_ratio", "daily_txn_count_ratio",
                "avg_amount_window_ratio", "daily_avg_amount_ratio", "send_receive_ratio", "spend_receive_spike_ratio",
                "same_parties_volume", "circular_trading_count", "refund_share_pct", "unique_senders",
                "unique_counterparties", "distinct_banks", "distinct_payment_ids", "bank_name_changes",
                "round_value_share_pct", "round_value_txn_count", "amount_ending_pattern", "cash_transaction",
                "anonymous_payment_screening_hit", "distinct_users_per_ip", "payment_id_use_count", "distinct_countries",
                "ip_outside_expected_location", "ip_change_count", "distinct_users_per_payment_id", "ctr_required",
                "is_structuring_suspected", "structuring_repeat_count_24h", "structuring_amount_band"}) {
            FEATURES.put(same, same);
        }
        // The cloud normalises direction (absent → OUTBOUND); the Edge derives the normalised value
        // under its own name so a raw caller-supplied "direction" cannot shadow it.
        FEATURES.put("direction", "txn_direction");
        FEATURES.put("origin_country", "country_code");

        for (String k : new String[]{"sanctions_hit", "pep_hit", "adverse_media_hit", "entity_screening_hit",
                "counterparty_screening_hit", "bank_name_screening_hit"}) {
            NOT_ON_EDGE.put(k, SCREENING);
        }
        for (String k : new String[]{"distinct_account_holder_names", "address_changes",
                "account_name_levenshtein_exceeded"}) {
            NOT_ON_EDGE.put(k, MERCHANT_NAMES);
        }
        for (String k : new String[]{"merchant_chargeback_ratio", "merchant_chargeback_count_30d", "is_chargeback",
                "dispute_reason_code", "dispute_reason_category", "rdr_status", "rdr_prevention_match"}) {
            NOT_ON_EDGE.put(k, DISPUTES);
        }
        NOT_ON_EDGE.put("peer_group_volume_ratio", PEER);
        NOT_ON_EDGE.put("peer_group_deviations", PEER);
        NOT_ON_EDGE.put("vfmp_stage", SCHEME);
        NOT_ON_EDGE.put("hecm_stage", SCHEME);
        for (String k : new String[]{"merchant_txn_count_30d", "merchant_txn_count_24h", "merchant_amount_sum_24h"}) {
            NOT_ON_EDGE.put(k, MERCHANT_AGG);
        }
        for (String k : new String[]{"ml_score", "krs_score", "trs_score", "cra_score", "page_rank", "community_id",
                "betweenness", "connection_count"}) {
            NOT_ON_EDGE.put(k, ML);
        }
        NOT_ON_EDGE.put("regulatory_cash_evidence", "structured evidence object, not a scalar feature");
        NOT_ON_EDGE.put("structuring_evaluation_status", "cloud status string; the Edge exposes is_structuring_suspected");

        LIST_HITS.put("country_high_risk",
                new ListHit(ListHitKind.SINGLE, "country_code", EdgeReferenceLists.HIGH_RISK_COUNTRIES));
        LIST_HITS.put("onboarding_high_risk_country",
                new ListHit(ListHitKind.SINGLE, "merchant_id", EdgeReferenceLists.MERCHANTS_HIGH_RISK_COUNTRY));
        LIST_HITS.put("ip_high_risk_country",
                new ListHit(ListHitKind.SINGLE, "merchant_id", EdgeReferenceLists.MERCHANTS_HIGH_RISK_COUNTRY));
        // The cloud matches the wallet and payment-details blacklists against the device
        // fingerprint and the card-country blacklist against the merchant country
        // (RuleFeatureEnrichmentService.enrichBlacklistHits); the Edge matches the same inputs.
        LIST_HITS.put("wallet_blacklist_hit",
                new ListHit(ListHitKind.SINGLE, DEVICE_KEY, EdgeReferenceLists.BLACKLIST_PREFIX + "wallet"));
        LIST_HITS.put("payment_details_blacklist",
                new ListHit(ListHitKind.SINGLE, DEVICE_KEY, EdgeReferenceLists.BLACKLIST_PREFIX + "payment_details"));
        LIST_HITS.put("card_issuer_country_blacklist",
                new ListHit(ListHitKind.SINGLE, "country_code", EdgeReferenceLists.BLACKLIST_PREFIX + "card_country"));
        LIST_HITS.put("variable_blacklist_hit", new ListHit(ListHitKind.PAN_OR_DEVICE, null, null));
        LIST_HITS.put("reference_keyword_blacklist", new ListHit(ListHitKind.KEYWORDS, REFERENCE_LC,
                EdgeReferenceLists.BLACKLIST_PREFIX + "reference_keyword"));

        TX.put("amount", "amount");
        TX.put("amountAsDouble", "amount");
        TX.put("currency", "currency");
        TX.put("countryCode", "country_code");
        TX.put("channel", "channel");
        TX.put("mcc", "mcc");
        TX.put("merchantId", "merchant_id");
        TX.put("panHash", "pan_hash");
        TX.put("panTxnCount1h", "pan_txn_count_1h");
        TX.put("panTxnCount24h", "pan_txn_count_24h");
        TX.put("panAmountSum24h", "pan_amount_sum_24h");
        TX.put("cashTransaction", "cash_transaction");
        TX.put("ctrRequired", "ctr_required");
        for (String k : new String[]{"mlScore", "pageRank", "communityId", "betweenness", "connectionCount", "krs",
                "cra", "trs"}) {
            TX_NOT_ON_EDGE.put(k, ML);
        }
        TX_NOT_ON_EDGE.put("merchantAmountSum24h", MERCHANT_AGG);
        TX_NOT_ON_EDGE.put("txnId", "the cloud transaction id does not exist on the Edge");
        TX_NOT_ON_EDGE.put("txnTime", "time-of-day expressions are not supported on the Edge");
        TX_NOT_ON_EDGE.put("decision", "rule-set side effects are not supported");
        TX_NOT_ON_EDGE.put("sarRequired", "rule-set side effects are not supported");
    }

    /** The Edge feature for a cloud feature key, or null when it is not a plain feature. */
    public static String feature(String cloudKey) {
        return FEATURES.get(cloudKey);
    }

    /** The list-hit macro for a boolean cloud feature, or null. */
    public static ListHit listHit(String cloudKey) {
        return LIST_HITS.get(cloudKey);
    }

    /** Why a cloud feature cannot run on the Edge (a catch-all for unknown keys). */
    public static String notOnEdgeReason(String cloudKey) {
        String reason = NOT_ON_EDGE.get(cloudKey);
        return reason != null ? reason : "feature '" + cloudKey + "' is not derived on the Edge";
    }

    /** The Edge feature for a {@code #tx} property (bean name, e.g. {@code panTxnCount24h}), or null. */
    public static String txProperty(String property) {
        return TX.get(property);
    }

    /** Why a {@code #tx} property cannot run on the Edge. */
    public static String txNotOnEdgeReason(String property) {
        String reason = TX_NOT_ON_EDGE.get(property);
        return reason != null ? reason : "#tx." + property + " has no Edge equivalent";
    }

    /** Every Edge feature name a compiled rule may reference (the edge-host derivation contract). */
    public static Set<String> edgeFeatureNames() {
        Set<String> names = new java.util.TreeSet<>(FEATURES.values());
        names.addAll(TX.values());
        names.add(DEVICE_KEY);
        names.add(REFERENCE_LC);
        return names;
    }
}
