package com.posgateway.aml.edge.tunnel;

import java.util.Arrays;
import java.util.Optional;

/**
 * The §7.6 read-only op allowlist (plus the insert-only {@code ledger.backfill}), with the
 * per-op deadline maximum and default, and how a multi-site PSP is served (§7.13).
 */
public enum TunnelOp {

    TXN_SEARCH("txn.search", 8_000, 8_000, Shape.LIST),
    TXN_GET("txn.get", 8_000, 5_000, Shape.GET),
    TXN_BATCH_GET("txn.batchGet", 8_000, 8_000, Shape.GET),
    TXN_RELATED("txn.related", 8_000, 8_000, Shape.LIST),
    FILING_EXTRACT("filing.extract", 30_000, 15_000, Shape.GET),
    EXPORT_STREAM("export.stream", 300_000, 300_000, Shape.STREAM),
    LEDGER_BACKFILL("ledger.backfill", 60_000, 60_000, Shape.SINGLE_SITE),
    LEDGER_HEALTH("ledger.health", 8_000, 3_000, Shape.PER_SITE);

    /** How results from several sites are combined (§7.13). */
    public enum Shape {
        /** k-way merge by (evaluatedAt desc, edgeAssessmentId desc) with a composite cursor. */
        LIST,
        /** Union of items; an id is missing only if missing on every site that answered. */
        GET,
        /** Chunk stream under credit, merged chunk-wise. */
        STREAM,
        /** Exactly one target site (the PSP-chosen backfill site, §11). */
        SINGLE_SITE,
        /** One answer per site, returned side by side. */
        PER_SITE
    }

    private final String wire;
    private final long maxDeadlineMs;
    private final long defaultDeadlineMs;
    private final Shape shape;

    /** Binds the wire name, deadline max/default and merge shape. */
    TunnelOp(String wire, long maxDeadlineMs, long defaultDeadlineMs, Shape shape) {
        this.wire = wire;
        this.maxDeadlineMs = maxDeadlineMs;
        this.defaultDeadlineMs = defaultDeadlineMs;
        this.shape = shape;
    }

    /** The op name on the wire ({@code common.schema.json#/$defs/op}). */
    public String wire() {
        return wire;
    }

    /** §7.6 deadline maximum in ms. */
    public long maxDeadlineMs() {
        return maxDeadlineMs;
    }

    /** §7.6 default deadline in ms. */
    public long defaultDeadlineMs() {
        return defaultDeadlineMs;
    }

    /** Multi-site combination rule. */
    public Shape shape() {
        return shape;
    }

    /** True for ops whose request carries {@code page} (§7.6 "Paged: yes"). */
    public boolean paged() {
        return shape == Shape.LIST;
    }

    /** §7.12: read ops may be retried once on another session of the same site. */
    public boolean retryableOnSessionLoss() {
        return this != EXPORT_STREAM && this != LEDGER_BACKFILL;
    }

    /** Looks up an op by its wire name. */
    public static Optional<TunnelOp> fromWire(String wire) {
        return Arrays.stream(values()).filter(o -> o.wire.equals(wire)).findFirst();
    }
}
