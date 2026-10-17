package com.posgateway.aml.service.backfill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One legacy cloud transaction as read for a back-fill, before it is mapped to the contract row
 * ({@link LegacyRowMapper}). Holds class-T data in memory for the life of one batch only (contract
 * I2); {@link #toString()} is overridden so the row can never reach a log by accident.
 *
 * @param source              legacy store
 * @param sourceId            primary key in that store
 * @param pspId               owning PSP
 * @param sortTs              evaluation time exactly as stored (UTC, keyset position)
 * @param evaluatedAt         evaluation (ingest) time, truncated to milliseconds
 * @param assessmentId        the R8 / V264 cloud assessment of the row, or {@code null}
 * @param assessmentEdgeId    that assessment's {@code edge_assessment_id}, or {@code null}
 * @param assessmentTxnRef    that assessment's {@code txn_ref}, or {@code null}
 * @param assessmentScore     that assessment's v2 score, or {@code null}
 * @param assessmentRuleIds   that assessment's v2 triggered rule ids (may be empty)
 * @param assessmentVersionIds that assessment's v2 rule version ids (may be empty)
 * @param clientReference     the PSP's own reference ({@code client_reference} /
 *                            {@code external_transaction_id})
 * @param panHash             card identity hash, or {@code null}
 * @param merchantId          merchant id, or {@code null}
 * @param amountMinor         amount in minor units when the store keeps one, else {@code null}
 * @param amountDecimal       amount in major units when the store keeps a decimal, else {@code null}
 * @param currency            currency (or asset) code as stored
 * @param countryCode         country as stored (2 or 3 letters)
 * @param channel             channel as stored
 * @param decision            decision as stored (legacy values allowed)
 * @param legacyScore         legacy model score (card rows, 0..1 or points) or risk score (multi-asset, points),
 *                            or {@code null}
 * @param ctrRequired         CTR flag as stored
 * @param triggeredRuleNames  rule names the legacy engine reported
 * @param riskSignals         multi-asset risk signal codes
 * @param features            raw feature bag (columns + stored feature JSON), before hygiene
 */
public record LegacyRow(LegacySource source, long sourceId, long pspId, java.time.LocalDateTime sortTs,
                        Instant evaluatedAt, UUID assessmentId,
                        UUID assessmentEdgeId, String assessmentTxnRef, Integer assessmentScore,
                        List<Long> assessmentRuleIds, List<Long> assessmentVersionIds, String clientReference,
                        String panHash, String merchantId, Long amountMinor, BigDecimal amountDecimal, String currency,
                        String countryCode, String channel, String decision, Double legacyScore, boolean ctrRequired,
                        List<String> triggeredRuleNames, List<String> riskSignals, Map<String, Object> features) {

    /** The {@code legacyTransactionId} sent to the Edge. */
    public String legacyTransactionId() {
        return LegacyBackfillIds.legacyId(source, sourceId);
    }

    /** True when the row's edgeAssessmentId is minted (no cloud assessment, §11 step 2). */
    public boolean minted() {
        return assessmentId == null;
    }

    /**
     * The edgeAssessmentId of the row: the cloud assessment's Edge id, else the assessment's own id
     * (both as V264 / {@code TransactionReferenceResolver} defined them), else the minted UUIDv5.
     */
    public UUID edgeAssessmentId() {
        if (assessmentEdgeId != null) {
            return assessmentEdgeId;
        }
        if (assessmentId != null) {
            return assessmentId;
        }
        return LegacyBackfillIds.mint(pspId, legacyTransactionId());
    }

    /** Only the identifiers: a row is never logged with its values (contract I2, §7.16). */
    @Override
    public String toString() {
        return "LegacyRow[" + legacyTransactionId() + "]";
    }
}
