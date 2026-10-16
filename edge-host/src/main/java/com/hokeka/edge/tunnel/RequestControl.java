package com.hokeka.edge.tunnel;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deadline and cancellation of one tunnel request (EDGE_DATA_RESIDENCY_CONTRACT.md §7.5, §7.9).
 *
 * <p>The budget is {@code deadlineMs} measured from the request's sealed {@code issuedAt}; each
 * ledger statement runs with {@code statement_timeout} = remaining budget − 250 ms. A cancel marks
 * the request and cancels the statement that is running, so work stops well within the contract's
 * 1 s.
 */
public final class RequestControl {

    /** Safety margin between the statement timeout and the deadline (§7.5). */
    static final long STATEMENT_MARGIN_MS = 250;

    private final Instant deadline;
    private final Clock clock;
    private volatile boolean cancelled;
    private volatile TunnelErrorCode cancelCode = TunnelErrorCode.CANCELLED;
    private volatile Statement running;

    /** @param deadline when the budget runs out; @param clock time source */
    public RequestControl(Instant deadline, Clock clock) {
        this.deadline = deadline;
        this.clock = clock;
    }

    /** When the budget runs out. */
    public Instant deadline() {
        return deadline;
    }

    /** Remaining budget at the clock's now (may be negative). */
    public Duration remaining() {
        return Duration.between(clock.instant(), deadline);
    }

    /**
     * The {@code statement_timeout} for the next statement.
     *
     * @throws TunnelException {@code DEADLINE_EXCEEDED} when nothing is left after the margin,
     *                         or the cancel code when cancelled
     */
    public long statementTimeoutMs() throws TunnelException {
        checkpoint();
        long ms = remaining().toMillis() - STATEMENT_MARGIN_MS;
        if (ms <= 0) {
            throw new TunnelException(TunnelErrorCode.DEADLINE_EXCEEDED, "request deadline exhausted");
        }
        return ms;
    }

    /**
     * Throws when the request was cancelled or its deadline has passed; called between units of work.
     */
    public void checkpoint() throws TunnelException {
        if (cancelled) {
            throw new TunnelException(cancelCode, "request was cancelled");
        }
        if (!clock.instant().isBefore(deadline)) {
            throw new TunnelException(TunnelErrorCode.DEADLINE_EXCEEDED, "request deadline exhausted");
        }
    }

    /** Registers the statement now running (so {@link #cancel} can stop it). */
    public void register(Statement statement) throws SQLException {
        running = statement;
        if (cancelled) {
            statement.cancel();
        }
    }

    /** Forgets the running statement. */
    public void unregister() {
        running = null;
    }

    /** Cancels with {@code CANCELLED} (§7.9). */
    public void cancel() {
        cancel(TunnelErrorCode.CANCELLED);
    }

    /**
     * Cancels with a specific code (e.g. {@code SUPPORT_ACCESS_DISABLED} when consent is withdrawn
     * mid-request) and stops the running statement.
     */
    public void cancel(TunnelErrorCode code) {
        cancelCode = code;
        cancelled = true;
        Statement s = running;
        if (s != null) {
            try {
                s.cancel();
            } catch (SQLException ignored) {
                // The statement finished or its connection is gone; either way it is not running.
            }
        }
    }

    /** Whether the request was cancelled. */
    public boolean cancelled() {
        return cancelled;
    }

    /** The code a cancelled request ends with. */
    public TunnelErrorCode cancelCode() {
        return cancelCode;
    }
}
