package com.posgateway.aml.edge.tunnel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The tunnel's private timer threads (request deadlines, housekeeping tick). Deliberately wrapped
 * instead of exposed as a {@link ScheduledExecutorService} bean: with {@code @EnableScheduling}
 * Spring would otherwise adopt such a bean for every {@code @Scheduled} job of the application
 * and Spring Boot would skip its own {@code taskScheduler}.
 */
public final class TunnelTimers implements AutoCloseable {

    private final ScheduledExecutorService executor;

    /** Two daemon threads named {@code edge-tunnel-timer-N}. */
    public TunnelTimers() {
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "edge-tunnel-timer-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** The executor (never handed to Spring's scheduling infrastructure). */
    public ScheduledExecutorService executor() {
        return executor;
    }

    /** Stops the timers on context shutdown. */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
