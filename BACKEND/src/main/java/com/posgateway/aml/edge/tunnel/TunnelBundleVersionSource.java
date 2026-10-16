package com.posgateway.aml.edge.tunnel;

/** Current rule bundle version of a PSP, for {@code hello.ack.currentBundleVersion} (§7.2). */
@FunctionalInterface
public interface TunnelBundleVersionSource {

    /** The bundle version an Edge of {@code pspId} would be served now ({@code -1} when none). */
    long currentBundleVersion(long pspId);
}
