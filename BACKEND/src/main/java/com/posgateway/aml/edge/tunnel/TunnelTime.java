package com.posgateway.aml.edge.tunnel;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** RFC 3339 UTC formatting used on the tunnel wire (common.schema.json {@code instant}). */
public final class TunnelTime {

    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** Utility class. */
    private TunnelTime() {
    }

    /**
     * Millisecond-precision instant, always with three fractional digits, so the sealed
     * {@code issuedAt} renders the outer {@code ts} exactly (§7.4 step 4).
     */
    public static String formatMillis(Instant instant) {
        return MILLIS.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    /** Second-precision instant (used for {@code lastSeenAt} in errors). */
    public static String formatSeconds(Instant instant) {
        return SECONDS.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}
