package com.hokeka.edge.channel;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract §4: a nonce is accepted once, inside a 300 s window, with a ≥10 000-entry cache. */
class ReplayGuardTest {

    private final Instant now = Instant.parse("2026-07-20T10:15:30Z");

    @Test
    void acceptsAFreshNonceOnceAndRejectsTheReplay() {
        ReplayGuard guard = new ReplayGuard();
        String nonce = ReplayGuard.newNonce();

        guard.accept(nonce, now, now);
        assertThrows(SecurityException.class, () -> guard.accept(nonce, now, now));
    }

    @Test
    void rejectsTimestampsOutsideTheSkewWindow() {
        ReplayGuard guard = new ReplayGuard();
        assertThrows(SecurityException.class,
                () -> guard.accept(ReplayGuard.newNonce(), now.minusSeconds(301), now));
        assertThrows(SecurityException.class,
                () -> guard.accept(ReplayGuard.newNonce(), now.plusSeconds(301), now));
        guard.accept(ReplayGuard.newNonce(), now.minusSeconds(299), now);
    }

    @Test
    void rejectsMissingNonceOrTimestamp() {
        ReplayGuard guard = new ReplayGuard();
        assertThrows(SecurityException.class, () -> guard.accept(null, now, now));
        assertThrows(SecurityException.class, () -> guard.accept("  ", now, now));
        assertThrows(SecurityException.class, () -> guard.accept(ReplayGuard.newNonce(), null, now));
    }

    @Test
    void cacheNeverDropsBelowTheContractMinimum() {
        ReplayGuard guard = new ReplayGuard(10, Duration.ofSeconds(300));
        assertEquals(ReplayGuard.MIN_CACHE_SIZE, guard.capacity());

        // Everything inside the window stays remembered up to the contract minimum.
        for (int i = 0; i < ReplayGuard.MIN_CACHE_SIZE; i++) {
            guard.accept("nonce-" + i, now, now);
        }
        assertThrows(SecurityException.class, () -> guard.accept("nonce-0", now, now));
    }

    @Test
    void nonceIs22CharBase64UrlAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String nonce = ReplayGuard.newNonce();
            assertEquals(22, nonce.length());
            assertTrue(nonce.matches("[A-Za-z0-9_-]{22}"), nonce);
            assertTrue(seen.add(nonce));
        }
    }
}
