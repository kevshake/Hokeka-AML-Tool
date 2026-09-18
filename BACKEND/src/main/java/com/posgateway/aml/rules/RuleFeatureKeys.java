package com.posgateway.aml.rules;

import java.util.Set;

/**
 * Canonical registry of the feature-map keys the rule engine consumes when building a
 * {@link TransactionFact}. Centralising them as named constants gives compile-time protection
 * against the class of bug where a consumer reads a key that no producer ever writes — which is
 * exactly how the 24h PAN-amount signal silently read {@code pan_txn_amount_sum_24h} (produced by
 * nothing) instead of the enrichment-produced {@code pan_amount_sum_24h}, leaving the fact field
 * permanently zero.
 *
 * <p>These names must match the keys written by the feature producers
 * ({@code FeatureExtractionService}, {@code OptimizedFeatureExtractionService},
 * {@code RuleFeatureEnrichmentService}) and the raw transaction fields placed on the feature map
 * before rule evaluation. {@link RuleFeatureKeysTest} guards that the DRL fact-builder only reads
 * keys listed here.
 */
public final class RuleFeatureKeys {

    private RuleFeatureKeys() {
    }

    // --- Raw transaction fields (placed on the map by the base feature extraction) ---
    public static final String MERCHANT_ID = "merchant_id";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String COUNTRY_CODE = "country_code";
    public static final String CHANNEL = "channel";
    public static final String PAN_HASH = "pan_hash";
    public static final String MCC = "mcc";

    // --- Graph / network features ---
    public static final String PAGE_RANK = "pageRank";
    public static final String COMMUNITY_ID = "communityId";
    public static final String BETWEENNESS = "betweenness";
    public static final String CONNECTION_COUNT = "connectionCount";

    // --- Velocity features ---
    public static final String PAN_TXN_COUNT_1H = "pan_txn_count_1h";
    public static final String PAN_TXN_COUNT_24H = "pan_txn_count_24h";
    /** 24h summed amount for the card. NOTE: the enrichment writes {@code pan_amount_sum_24h};
     *  the previous consumer read the non-existent {@code pan_txn_amount_sum_24h}. */
    public static final String PAN_AMOUNT_SUM_24H = "pan_amount_sum_24h";
    public static final String MERCHANT_AMOUNT_SUM_24H = "merchant_txn_amount_sum_24h";

    // --- Risk / flags ---
    public static final String CASH_TRANSACTION = "cash_transaction";
    public static final String COUNTRY_HIGH_RISK = "country_high_risk";
    public static final String KRS_SCORE = "krs_score";
    public static final String CRA_SCORE = "cra_score";
    public static final String TRS_SCORE = "trs_score";

    /** Every key the DRL fact-builder is permitted to read. */
    public static final Set<String> FACT_BUILDER_KEYS = Set.of(
            MERCHANT_ID, AMOUNT, CURRENCY, COUNTRY_CODE, CHANNEL, PAN_HASH, MCC,
            PAGE_RANK, COMMUNITY_ID, BETWEENNESS, CONNECTION_COUNT,
            PAN_TXN_COUNT_1H, PAN_TXN_COUNT_24H, PAN_AMOUNT_SUM_24H, MERCHANT_AMOUNT_SUM_24H,
            CASH_TRANSACTION, COUNTRY_HIGH_RISK, KRS_SCORE, CRA_SCORE, TRS_SCORE);
}
