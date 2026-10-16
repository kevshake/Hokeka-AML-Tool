package com.hokeka.edge.residency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Effective retention (EDGE_DATA_RESIDENCY_CONTRACT.md §6.3, §8.2): {@code max(config
 * retention.years, local floor)}, where neither input may go below the FATF R.11 floor of 5 years.
 * A lower value from either source is ignored (treated as 5) and logged once as a warning. Without
 * an accepted config document there is no effective retention at all, so nothing is purged.
 */
public final class RetentionPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicy.class);

    /** Contract floor and default (§8.1). */
    public static final int FLOOR_YEARS = 5;
    /** Contract cap (§8.1). */
    public static final int MAX_YEARS = 25;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Utility class. */
    private RetentionPolicy() {
    }

    /**
     * Retention in years in force, or empty before the first accepted config document (§8.2: no
     * purge before config).
     *
     * @param config     the accepted config document, if any
     * @param localFloor {@code EDGE_RETENTION_MIN_YEARS}
     */
    public static OptionalInt effectiveYears(Optional<EdgeConfigDocument> config, int localFloor) {
        if (config.isEmpty()) {
            return OptionalInt.empty();
        }
        int fromConfig = clamp(config.get().retentionYears(), "config retention.years");
        int floor = clamp(localFloor, "EDGE_RETENTION_MIN_YEARS");
        return OptionalInt.of(Math.max(fromConfig, floor));
    }

    /** The local floor alone, clamped to the contract floor (for status output before any config). */
    public static int localFloor(int localFloor) {
        return clamp(localFloor, "EDGE_RETENTION_MIN_YEARS");
    }

    /**
     * Purge cutoff for {@code years} at {@code now}: the same UTC calendar instant {@code years}
     * earlier (§8.2 computes it in UTC).
     */
    public static Instant cutoff(Instant now, int years) {
        return now.atOffset(ZoneOffset.UTC).minusYears(years).toInstant();
    }

    /** Values below the floor become the floor (with a one-time warning); above the cap, the cap. */
    private static int clamp(int years, String source) {
        if (years < FLOOR_YEARS) {
            if (WARNED.add(source + ":" + years)) {
                log.warn("Ignoring {} = {} year(s): below the {}-year retention floor; using {}", source, years,
                        FLOOR_YEARS, FLOOR_YEARS);
            }
            return FLOOR_YEARS;
        }
        if (years > MAX_YEARS) {
            if (WARNED.add(source + ":" + years)) {
                log.warn("{} = {} year(s) exceeds the {}-year cap; using {}", source, years, MAX_YEARS, MAX_YEARS);
            }
            return MAX_YEARS;
        }
        return years;
    }
}
