package com.posgateway.aml.service.edgedata;

/**
 * Where the transaction fields of a tunnel-backed response come from (the {@code source} field the
 * contract §7.14 adds to every item).
 *
 * <p><b>Legacy-row rule (R6).</b> The source is decided per PSP, never per row, so one response never
 * mixes the two:
 * <ul>
 *   <li>{@link #EDGE}: the PSP has an Edge Node that ever activated. Its transactions are read from
 *       its Edge Sites only. Legacy cloud-ingest rows of such a PSP are not served from the cloud;
 *       they become readable from the Edge once R15 back-fills them under their reference id.</li>
 *   <li>{@link #CLOUD_LEGACY}: the PSP has no activated Edge Node. Its legacy cloud-ingest rows
 *       (kept until R12/R15) are served, each flagged {@code source: "CLOUD_LEGACY"}, with the same
 *       envelope, support-access gate and access-log row as an Edge pull.</li>
 * </ul>
 */
public enum TransactionSource {
    /** Pulled live from the PSP's Edge over the tunnel. */
    EDGE,
    /** A legacy cloud-ingest row of a PSP that has no Edge (removed by R12). */
    CLOUD_LEGACY
}
