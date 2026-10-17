package com.posgateway.aml.service.backfill;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code edge.backfill.*}: tuning of the R15 back-fill worker. Contract limits are fixed in code
 * (≤ 500 rows per BATCH, 60 s per phase); these knobs only make a run gentler or let tests run fast.
 */
@Component
@ConfigurationProperties(prefix = "edge.backfill")
public class BackfillProperties {

    /**
     * Zone of the legacy {@code TIMESTAMP} (without time zone) columns: ingest wrote them as the
     * JVM's local wall clock. Blank = this JVM's default zone (the same zone the ingest instances ran
     * in, UTC in the container images).
     */
    private String legacyTimeZone = "";

    /** Runs the scheduled worker on this Control Plane instance. */
    private boolean workerEnabled = true;

    /** Rows per BATCH (contract maximum 500). */
    private int batchRows = 500;

    /**
     * Serialized row bytes per BATCH. The sealed frame must stay under 1 MiB (§7.3); sealing grows
     * the plaintext by the base64 factor, so the default leaves a wide margin. Halved automatically
     * on {@code FRAME_TOO_LARGE}.
     */
    private int batchBytes = 600_000;

    /** Smallest byte budget a FRAME_TOO_LARGE may shrink to before the run fails. */
    private int minBatchBytes = 16_384;

    /** Held rows re-read from the Edge with txn.batchGet and compared field by field. */
    private int sampleSize = 200;

    /** TXN_REF_CONFLICT rejections checked with txn.get per verification. */
    private int txnRefChecks = 1_000;

    /** VERIFY_MISMATCH re-send rounds before the run fails. */
    private int maxResendRounds = 2;

    /** Consecutive retryable failures before a run is STALLED. */
    private int maxConsecutiveFailures = 50;

    /** First retry delay; doubles per consecutive failure up to {@link #maxBackoffMs}. */
    private long initialBackoffMs = 5_000;

    /** Longest retry delay. */
    private long maxBackoffMs = 300_000;

    /** Worker lease on a claimed run. */
    private long leaseMs = 120_000;

    /** Longest a worker keeps one run before letting others go first. */
    private long sliceMs = 50_000;

    /** {@link #legacyTimeZone}. */
    public String getLegacyTimeZone() { return legacyTimeZone; }

    /** Sets {@link #legacyTimeZone}. */
    public void setLegacyTimeZone(String legacyTimeZone) { this.legacyTimeZone = legacyTimeZone; }

    /** The zone legacy wall-clock timestamps are read in. */
    public java.time.ZoneId legacyZone() {
        return legacyTimeZone == null || legacyTimeZone.isBlank() ? java.time.ZoneId.systemDefault()
                : java.time.ZoneId.of(legacyTimeZone.strip());
    }

    /** {@link #workerEnabled}. */
    public boolean isWorkerEnabled() { return workerEnabled; }

    /** Sets {@link #workerEnabled}. */
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }

    /** {@link #batchRows}, capped at the contract's 500. */
    public int getBatchRows() { return Math.max(1, Math.min(500, batchRows)); }

    /** Sets {@link #batchRows}. */
    public void setBatchRows(int batchRows) { this.batchRows = batchRows; }

    /** {@link #batchBytes}. */
    public int getBatchBytes() { return batchBytes; }

    /** Sets {@link #batchBytes}. */
    public void setBatchBytes(int batchBytes) { this.batchBytes = batchBytes; }

    /** {@link #minBatchBytes}. */
    public int getMinBatchBytes() { return minBatchBytes; }

    /** Sets {@link #minBatchBytes}. */
    public void setMinBatchBytes(int minBatchBytes) { this.minBatchBytes = minBatchBytes; }

    /** {@link #sampleSize}, within the txn.batchGet maximum per call handled by the verifier. */
    public int getSampleSize() { return Math.max(0, sampleSize); }

    /** Sets {@link #sampleSize}. */
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }

    /** {@link #txnRefChecks}. */
    public int getTxnRefChecks() { return Math.max(0, txnRefChecks); }

    /** Sets {@link #txnRefChecks}. */
    public void setTxnRefChecks(int txnRefChecks) { this.txnRefChecks = txnRefChecks; }

    /** {@link #maxResendRounds}. */
    public int getMaxResendRounds() { return Math.max(0, maxResendRounds); }

    /** Sets {@link #maxResendRounds}. */
    public void setMaxResendRounds(int maxResendRounds) { this.maxResendRounds = maxResendRounds; }

    /** {@link #maxConsecutiveFailures}. */
    public int getMaxConsecutiveFailures() { return Math.max(1, maxConsecutiveFailures); }

    /** Sets {@link #maxConsecutiveFailures}. */
    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }

    /** {@link #initialBackoffMs}. */
    public long getInitialBackoffMs() { return Math.max(1, initialBackoffMs); }

    /** Sets {@link #initialBackoffMs}. */
    public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }

    /** {@link #maxBackoffMs}. */
    public long getMaxBackoffMs() { return Math.max(getInitialBackoffMs(), maxBackoffMs); }

    /** Sets {@link #maxBackoffMs}. */
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }

    /** {@link #leaseMs}. */
    public long getLeaseMs() { return Math.max(1_000, leaseMs); }

    /** Sets {@link #leaseMs}. */
    public void setLeaseMs(long leaseMs) { this.leaseMs = leaseMs; }

    /** {@link #sliceMs}. */
    public long getSliceMs() { return Math.max(1, sliceMs); }

    /** Sets {@link #sliceMs}. */
    public void setSliceMs(long sliceMs) { this.sliceMs = sliceMs; }

    /** Back-off before attempt {@code failures + 1}: initial × 2^(failures − 1), capped. */
    public long backoffMs(int failures) {
        long delay = getInitialBackoffMs();
        for (int i = 1; i < failures && delay < getMaxBackoffMs(); i++) {
            delay *= 2;
        }
        return Math.min(delay, getMaxBackoffMs());
    }
}
