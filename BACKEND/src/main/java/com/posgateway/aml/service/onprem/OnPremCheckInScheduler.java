package com.posgateway.aml.service.onprem;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Assigns the next mandatory check-in instant for an on-prem instance.
 *
 * <p>Check-ins are daily (or {@code checkIntervalDays}) at a <b>deterministic minute-of-day</b>
 * derived from the instance id hash, so the fleet is spread across 24 hours instead of
 * stampeding the central auth server at midnight.
 */
@Component
public class OnPremCheckInScheduler {

    /**
     * @param instanceId        stable instance identity
     * @param from              reference instant (usually now)
     * @param checkIntervalDays days until the next check window (typically 1)
     * @param leaseUntil        hard lease expiry — next check is never scheduled after this
     */
    public Instant assignNextCheckAt(String instanceId, Instant from, int checkIntervalDays,
                                     Instant leaseUntil) {
        int interval = Math.max(1, checkIntervalDays);
        int minuteOfDay = stableMinuteOfDay(instanceId);
        LocalDate baseDate = from.atZone(ZoneOffset.UTC).toLocalDate().plusDays(interval);
        ZonedDateTime candidate = baseDate.atStartOfDay(ZoneOffset.UTC).plusMinutes(minuteOfDay);
        Instant next = candidate.toInstant();
        // If the assigned slot somehow lands before/at "from" (clock skew / same-day reissue),
        // push one more interval.
        if (!next.isAfter(from)) {
            next = candidate.plusDays(interval).toInstant();
        }
        if (leaseUntil != null && next.isAfter(leaseUntil)) {
            return leaseUntil;
        }
        return next;
    }

    /**
     * Deterministic minute in [0, 1439] from instance id — stable across renewals so each PSP
     * keeps a consistent daily slot.
     */
    static int stableMinuteOfDay(String instanceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(instanceId.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 8) | (hash[1] & 0xff);
            return Math.floorMod(value, 24 * 60);
        } catch (Exception e) {
            return Math.floorMod(instanceId.hashCode(), 24 * 60);
        }
    }
}
