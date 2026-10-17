package com.posgateway.aml.service.filing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The transaction data of one filing, pulled at filing time (contract §7.6 {@code filing.extract},
 * or the legacy cloud rows of a PSP without an Edge). It lives only for the filing request and
 * reaches storage only inside the rendered document (§2.3). Never logged.
 *
 * @param source       {@code EDGE}, {@code CLOUD_LEGACY} or {@code NONE} (no transactions)
 * @param transactions one entry per filed transaction, in reference order
 * @param ctr          the CTR aggregate (CTR filings only), else {@code null}
 * @param auditIds     {@code edge_data_access_log.audit_id}s of the pulls behind this extract
 */
public record FilingExtract(String source, List<Txn> transactions, CtrAggregate ctr, List<UUID> auditIds) {

    /**
     * One filed transaction.
     *
     * @param edgeAssessmentId decision record id (legacy rows: the reference id, may be null)
     * @param legacyTxnId      cloud {@code transactions.txn_id} for legacy rows, else null
     * @param txnRef           the PSP's transaction id, if any
     * @param siteId           Edge Site that answered, if any
     * @param evaluatedAt      evaluation / transaction time
     * @param amountMinor      amount in currency minor units (required)
     * @param currency         ISO 4217 code (required)
     */
    public record Txn(String edgeAssessmentId, Long legacyTxnId, String txnRef, String siteId, Instant evaluatedAt,
                      String decision, Integer score, String merchantId, String mcc, long amountMinor, String currency,
                      String countryCode, String channel, List<Long> triggeredRuleIds, List<String> typologyCodes,
                      List<SanctionsHit> sanctionsHits, Map<String, String> features, boolean legalHold) {

        /** The identifier shown as the transaction number: txnRef, else the decision record id. */
        public String number() {
            if (txnRef != null) {
                return txnRef;
            }
            return edgeAssessmentId != null ? edgeAssessmentId : "legacy-" + legacyTxnId;
        }
    }

    /** One sanctions/PEP match recorded by the Edge (public-list entry id plus the matched value). */
    public record SanctionsHit(String entryId, double score, String matchedOn, String matchedValue,
                               String listVersion) {}

    /**
     * The CTR aggregate of a filing.
     *
     * @param basis             {@code CARD_BUSINESS_DAY} (Edge) or {@code SINGLE_TRANSACTION} (legacy rows)
     * @param cashTotal         cash total in {@code reportingCurrency} major units
     * @param threshold         reporting threshold in {@code reportingCurrency} major units
     * @param edgeAssessmentIds the transactions that make up the total
     * @param fxAsOf            FX rates' as-of instant
     * @param fxSource          legacy rate source, if any
     * @param configVersion     Edge config version used, if any
     */
    public record CtrAggregate(String basis, String anchorId, LocalDate businessDate, String timezone,
                               String reportingCurrency, BigDecimal cashTotal, BigDecimal threshold, int txnCount,
                               List<String> edgeAssessmentIds, Instant fxAsOf, String fxSource, Long configVersion) {}

    /** Number of transactions in the extract. */
    public int transactionCount() {
        return transactions.size();
    }
}
