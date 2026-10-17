package com.posgateway.aml.service.backfill;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code ledger_backfill_runs} (V270): run state, position and counts. Ids, counts,
 * digests and codes only — never a transaction value.
 */
public record BackfillRun(UUID backfillId, long pspId, String siteId, boolean defaultSite, List<String> siteIdsSnapshot,
                          BackfillState state, BackfillPhase phase, Instant sourceFrom, Instant sourceTo,
                          Instant retentionCutoff, int retentionYears, long expectedRows, LegacySource cursorSource,
                          LocalDateTime cursorTs, Long cursorId, long nextBatchSeq, int resendRound,
                          int batchByteBudget, long rowsAccepted, long rowsDuplicate, String expectedIdDigest,
                          Long expectedHeldRows, String edgeCommitState, Long edgeRowsTotal, String edgeIdDigest,
                          Integer sampleSize, Integer sampleMismatches, String lastErrorCode, Instant lastErrorAt,
                          int consecutiveFailures, Instant nextAttemptAt, String abortReason, String createdBy,
                          String createdReason, Instant createdAt, Instant begunAt, Instant committedAt,
                          Instant verifiedAt, Instant finishedAt) {

    /** The keyset position of the next batch, or {@code null} at the start of a source. */
    public LegacyRowReader.Position position() {
        return cursorTs == null || cursorId == null ? null : new LegacyRowReader.Position(cursorTs, cursorId);
    }

    /** The source the next batch reads ({@code TXN} at the start). */
    public LegacySource source() {
        return cursorSource == null ? LegacySource.TXN : cursorSource;
    }
}
