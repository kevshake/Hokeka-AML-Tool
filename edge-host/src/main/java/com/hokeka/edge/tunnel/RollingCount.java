package com.hokeka.edge.tunnel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * The growth of a monotonically increasing counter over the last 24 hours, from periodic samples
 * (used for {@code ledger.health.writeFailures24h}, contract §7.6, over the recorder's lifetime
 * counter). Samples are kept at most every {@link #SAMPLE_EVERY}; the window start is the newest
 * sample at or before {@code now − 24 h}, or the first sample when the process is younger than a
 * day (the counter starts at 0 with the process).
 */
public final class RollingCount {

    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration SAMPLE_EVERY = Duration.ofMinutes(10);

    private record Sample(Instant at, long total) {
    }

    private final LongSupplier total;
    private final Clock clock;
    private final Deque<Sample> samples = new ArrayDeque<>();

    /** @param total the lifetime counter; @param clock time source */
    public RollingCount(LongSupplier total, Clock clock) {
        this.total = total;
        this.clock = clock;
        samples.addLast(new Sample(clock.instant(), total.getAsLong()));
    }

    /** Takes a sample when the last one is old enough (call from a periodic task). */
    public synchronized void sample() {
        Instant now = clock.instant();
        if (samples.isEmpty() || !samples.peekLast().at().plus(SAMPLE_EVERY).isAfter(now)) {
            samples.addLast(new Sample(now, total.getAsLong()));
        }
        Instant windowStart = now.minus(WINDOW);
        // Keep exactly one sample at or before the window start (the newest such): drop the head
        // while the next sample is also at or before it.
        while (samples.size() > 1) {
            java.util.Iterator<Sample> it = samples.iterator();
            it.next();
            Sample second = it.next();
            if (second.at().isAfter(windowStart)) {
                break;
            }
            samples.pollFirst();
        }
    }

    /** Counter growth over the last 24 hours (or since start, if younger). */
    public synchronized long last24h() {
        sample();
        long now = total.getAsLong();
        return Math.max(0, now - samples.peekFirst().total());
    }
}
