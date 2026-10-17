package com.posgateway.aml.service.legalhold;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.TunnelSessionListener;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Connects legal holds to the tunnel's session events on the session-holding instance (the R4
 * extension point left for R9): after every {@code hello.ack} the site's unconfirmed holds are
 * re-sent (§7.10 MUST), and every {@code hold.ack} is recorded. The work runs on a dedicated
 * single thread so the WebSocket thread never waits on the database.
 */
@Component
public class LegalHoldTunnelListener implements TunnelSessionListener {

    private static final Logger log = LoggerFactory.getLogger(LegalHoldTunnelListener.class);

    private final LegalHoldService holds;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "legal-hold-tunnel");
        t.setDaemon(true);
        return t;
    });

    /** Wires the listener to the hold service. */
    public LegalHoldTunnelListener(LegalHoldService holds) {
        this.holds = holds;
    }

    /** Re-sends the site's unconfirmed holds (all of them, however recently sent). */
    @Override
    public void onSessionOpened(TunnelSessionInfo session) {
        submit(() -> holds.resendUnconfirmed(session, null), "session open");
    }

    /** Records the acknowledgement of a hold notify. */
    @Override
    public void onHoldAck(TunnelSessionInfo session, String notifyId, ObjectNode item) {
        ObjectNode copy = item.deepCopy();
        submit(() -> holds.recordAck(session, notifyId, copy), "hold.ack");
    }

    /** Queues one task; failures are logged by class only. */
    private void submit(Runnable task, String what) {
        try {
            worker.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.warn("Legal hold {} handling failed: {}", what, e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Legal hold {} handling skipped: instance shutting down", what);
        }
    }

    /** Lets queued work finish briefly, then stops the worker. */
    @PreDestroy
    public void shutdown() throws InterruptedException {
        worker.shutdown();
        if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
            worker.shutdownNow();
        }
    }
}
