package com.hokeka.edge.channel;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replay detection for HSE-1 envelopes, per the channel contract §4: a nonce may be seen once, and
 * {@code |now - issuedAt|} must stay within the skew window (300 s). The nonce cache is a bounded
 * access-ordered LRU (≥ 10 000 entries) so a long-running edge cannot be starved into forgetting a
 * recent nonce inside the skew window.
 */
public final class ReplayGuard {

    /** Contract minimum for the nonce cache. */
    public static final int MIN_CACHE_SIZE = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int capacity;
    private final Duration maxSkew;
    private final Map<String, Boolean> seen;

    public ReplayGuard() {
        this(MIN_CACHE_SIZE, Duration.ofSeconds(300));
    }

    public ReplayGuard(int capacity, Duration maxSkew) {
        this.capacity = Math.max(MIN_CACHE_SIZE, capacity);
        this.maxSkew = maxSkew == null || maxSkew.isZero() ? Duration.ofSeconds(300) : maxSkew.abs();
        this.seen = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > ReplayGuard.this.capacity;
            }
        };
    }

    /** A fresh 22-char base64url nonce over 16 random bytes (contract §4). */
    public static String newNonce() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * Accept an envelope exactly once, within the skew window.
     *
     * @throws SecurityException when the nonce is malformed, already seen, or the timestamp is
     *                           outside the window
     */
    public void accept(String nonce, Instant issuedAt, Instant now) {
        if (nonce == null || nonce.isBlank()) {
            throw new SecurityException("replay envelope has no nonce");
        }
        if (issuedAt == null) {
            throw new SecurityException("replay envelope has no issuedAt");
        }
        long skewSeconds = Math.abs(Duration.between(issuedAt, now).getSeconds());
        if (skewSeconds > maxSkew.getSeconds()) {
            throw new SecurityException("replay envelope timestamp skew " + skewSeconds
                    + "s exceeds the " + maxSkew.getSeconds() + "s window");
        }
        synchronized (seen) {
            if (seen.put(nonce, Boolean.TRUE) != null) {
                throw new SecurityException("replayed envelope nonce " + nonce);
            }
        }
    }

    public int capacity() {
        return capacity;
    }

    public Duration maxSkew() {
        return maxSkew;
    }
}
