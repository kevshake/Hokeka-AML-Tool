package com.posgateway.aml.service.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled driver of back-fill runs. Each tick claims one due run under a database lease (so one
 * Control Plane instance drives a run at a time, and a crashed instance's lease simply expires),
 * then lets {@link BackfillEngine} take steps until the run backs off, finishes, is paused or
 * aborted by an operator, or the time slice ends. Runs resume from their persisted phase and cursor.
 */
@Component
@ConditionalOnProperty(name = "edge.backfill.worker-enabled", havingValue = "true", matchIfMissing = true)
public class LedgerBackfillWorker {

    private static final Logger log = LoggerFactory.getLogger(LedgerBackfillWorker.class);

    private final BackfillRunStore store;
    private final BackfillEngine engine;
    private final BackfillProperties props;
    private final String owner;

    /** Wires the worker; the lease owner is this host plus a random suffix. */
    public LedgerBackfillWorker(BackfillRunStore store, BackfillEngine engine, BackfillProperties props) {
        this.store = store;
        this.engine = engine;
        this.props = props;
        this.owner = hostName() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** One tick: claim a due run and drive it for at most one slice. */
    @Scheduled(fixedDelayString = "${edge.backfill.tick-ms:5000}", initialDelayString = "${edge.backfill.initial-delay-ms:30000}")
    public void tick() {
        Optional<UUID> claimed;
        try {
            claimed = store.claimDue(owner, props.getLeaseMs());
        } catch (DataAccessException e) {
            log.warn("Back-fill worker could not claim a run ({})", e.getClass().getSimpleName());
            return;
        }
        claimed.ifPresent(this::drive);
    }

    /** Drives a claimed run until it backs off, stops being driven, or the slice ends. */
    void drive(UUID backfillId) {
        long deadline = System.currentTimeMillis() + props.getSliceMs();
        try {
            while (System.currentTimeMillis() < deadline) {
                if (!store.renewLease(backfillId, owner, props.getLeaseMs())) {
                    return;
                }
                Optional<BackfillRun> run = store.find(backfillId);
                if (run.isEmpty() || !BackfillState.DRIVEN.contains(run.get().state())
                        || (run.get().nextAttemptAt() != null
                        && run.get().nextAttemptAt().toEpochMilli() > System.currentTimeMillis())) {
                    return;
                }
                if (!engine.step(run.get())) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            // A defect in one step must not kill the scheduler thread; the run is retried next tick.
            log.error("Back-fill {}: step failed ({})", backfillId, e.getClass().getSimpleName());
        } finally {
            try {
                store.releaseLease(backfillId, owner);
            } catch (DataAccessException e) {
                log.warn("Back-fill {}: lease not released ({}); it expires on its own", backfillId,
                        e.getClass().getSimpleName());
            }
        }
    }

    /** This host's name for the lease owner. */
    private static String hostName() {
        try {
            String h = InetAddress.getLocalHost().getHostName();
            return h.length() > 100 ? h.substring(0, 100) : h;
        } catch (UnknownHostException e) {
            return "cp";
        }
    }
}
