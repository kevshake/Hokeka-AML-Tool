package com.hokeka.edge.tunnel;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** RFC 3339 UTC renderings used on the tunnel wire ({@code common.schema.json} instant formats). */
public final class TunnelTime {

    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** Utility class. */
    private TunnelTime() {
    }

    /**
     * Millisecond precision, always three fractional digits: the sealed {@code issuedAt} then
     * renders the outer {@code ts} exactly (§7.4 step 4), and item {@code evaluatedAt} values
     * round-trip through cursors unchanged.
     */
    public static String millis(Instant instant) {
        return MILLIS.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    /** Second precision ({@code instantSeconds}, used by ledger.health). */
    public static String seconds(Instant instant) {
        return SECONDS.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}
