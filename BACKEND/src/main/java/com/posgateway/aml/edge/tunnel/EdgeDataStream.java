package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A streamed {@code export.stream} across a PSP's sites (§7.8, §7.13): a blocking iterator of
 * validated items merged by (evaluatedAt desc, edgeAssessmentId desc).
 *
 * <p><b>Credit.</b> Each site starts with its {@code initialCredit}; whenever the consumer has taken
 * every item of one chunk, one more chunk is granted to that site. The cloud therefore never holds
 * more than the chunks it has granted and not yet forwarded, and nothing is written anywhere: the
 * caller streams items straight to its HTTP response (§7.14).
 *
 * <p>A site that fails mid-stream is dropped and reported in {@link #unavailableSites()}; the stream
 * stops after {@code maxRows} items. {@link #close()} cancels unfinished sites
 * ({@code CLIENT_GONE}) and completes the audit row.
 */
public class EdgeDataStream implements Iterator<ObjectNode>, AutoCloseable {

    private final UUID auditId;
    private final EdgeDataGateway gateway;
    private final Set<String> contacted;
    private final int maxRows;
    private final long deadlineAt;
    private final Map<String, SiteBuffer> buffers = new LinkedHashMap<>();
    private final List<EdgeDataResult.UnavailableSite> unavailable = new ArrayList<>();
    private final Set<String> answered = new TreeSet<>();
    private long emitted;
    private long totalRows;
    private boolean closed;
    private ObjectNode lookahead;

    /** Per-site buffer: the items of the chunk being consumed, plus end-of-stream state. */
    private static final class SiteBuffer {
        final EdgeTunnelClient.SiteCall call;
        final Deque<ObjectNode> items = new ArrayDeque<>();
        boolean currentChunkLast;
        boolean finished;

        /** Buffers the chunks of one site call. */
        SiteBuffer(EdgeTunnelClient.SiteCall call) {
            this.call = call;
        }
    }

    /**
     * Wraps the started site calls and waits for each site's first event, so a pull no site can
     * serve fails before any byte is streamed.
     *
     * @throws EdgeDataException when every site failed
     */
    EdgeDataStream(UUID auditId, Map<String, EdgeTunnelClient.SiteCall> calls, Set<String> contacted, int maxRows,
                   long budgetMs, EdgeDataGateway gateway) {
        this.auditId = auditId;
        this.gateway = gateway;
        this.contacted = contacted;
        this.maxRows = maxRows;
        this.deadlineAt = System.currentTimeMillis() + budgetMs;
        calls.forEach((site, call) -> buffers.put(site, new SiteBuffer(call)));
        List<TunnelError> failures = new ArrayList<>();
        for (Map.Entry<String, SiteBuffer> e : buffers.entrySet()) {
            TunnelError err = fill(e.getKey(), e.getValue());
            if (err != null) {
                failures.add(err);
            }
        }
        if (buffers.values().stream().allMatch(b -> b.finished && b.items.isEmpty()) && answered.isEmpty()) {
            closed = true;
            TunnelError best = EdgeDataGateway.bestFailure(failures);
            gateway.completeAudit(auditId, contacted, answered, 0, bytes(), best.code().name());
            throw new EdgeDataException(best);
        }
    }

    /** The audit id of this pull. */
    public UUID auditId() {
        return auditId;
    }

    /** True when some site did not complete its part of the export. */
    public boolean incomplete() {
        return !unavailable.isEmpty();
    }

    /** Sites that failed, with their error code. */
    public List<EdgeDataResult.UnavailableSite> unavailableSites() {
        return List.copyOf(unavailable);
    }

    /** Sum of the Edges' {@code totalRows} from the sites that finished. */
    public long totalRows() {
        return totalRows;
    }

    /** True while another merged item exists (blocks until the sites' next chunks arrive). */
    @Override
    public boolean hasNext() {
        if (lookahead != null) {
            return true;
        }
        if (closed || emitted >= maxRows) {
            return false;
        }
        lookahead = takeNext();
        return lookahead != null;
    }

    /** The next merged item. */
    @Override
    public ObjectNode next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        ObjectNode out = lookahead;
        lookahead = null;
        emitted++;
        return out;
    }

    /** k-way merge step: refill every live site's head, then take the newest head. */
    private ObjectNode takeNext() {
        String best = null;
        EdgeDataGateway.SortKey bestKey = null;
        for (Map.Entry<String, SiteBuffer> e : buffers.entrySet()) {
            SiteBuffer b = e.getValue();
            if (b.items.isEmpty() && !b.finished) {
                fill(e.getKey(), b);
            }
            if (b.items.isEmpty()) {
                continue;
            }
            EdgeDataGateway.SortKey k = EdgeDataGateway.SortKey.of(TunnelOp.EXPORT_STREAM, b.items.peekFirst());
            if (bestKey == null || k.compareTo(bestKey) > 0) {
                bestKey = k;
                best = e.getKey();
            }
        }
        if (best == null) {
            return null;
        }
        SiteBuffer b = buffers.get(best);
        ObjectNode item = b.items.pollFirst();
        if (b.items.isEmpty() && !b.currentChunkLast && !b.finished) {
            b.call.grant(1);
        }
        return item;
    }

    /**
     * Blocks for the site's next chunk (skipping empty non-final chunks, each of which is
     * re-credited) or its terminal event.
     *
     * @return the site's error when it failed, else {@code null}
     */
    private TunnelError fill(String site, SiteBuffer b) {
        while (b.items.isEmpty() && !b.finished) {
            long wait = deadlineAt - System.currentTimeMillis();
            EdgeTunnelClient.StreamEvent ev;
            try {
                ev = wait <= 0 ? null : b.call.poll(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ev = null;
            }
            if (ev == null) {
                b.call.cancel("DEADLINE");
                b.finished = true;
                TunnelError err = TunnelError.timeout(site);
                unavailable.add(new EdgeDataResult.UnavailableSite(site, err.code(), null));
                return err;
            }
            if (ev instanceof EdgeTunnelClient.Failed f) {
                b.finished = true;
                unavailable.add(new EdgeDataResult.UnavailableSite(site, f.error().code(),
                        f.error().code() == TunnelErrorCode.EDGE_OFFLINE ? f.error().lastSeenAt() : null));
                return f.error();
            }
            EdgeTunnelClient.Chunk c = (EdgeTunnelClient.Chunk) ev;
            answered.add(site);
            b.items.addAll(c.items());
            b.currentChunkLast = c.last();
            if (c.last()) {
                b.finished = true;
                if (c.totalRows() != null) {
                    totalRows += c.totalRows();
                }
            } else if (c.items().isEmpty()) {
                b.call.grant(1);
            }
        }
        return null;
    }

    /** Bytes received from every site so far. */
    private long bytes() {
        long n = 0;
        for (SiteBuffer b : buffers.values()) {
            n += b.call.bytes();
        }
        return n;
    }

    /**
     * Ends the export: unfinished sites get {@code cancel CLIENT_GONE} (or {@code SUPERSEDED} when
     * {@code maxRows} was reached) and the audit row is completed with {@code OK}, {@code PARTIAL}
     * or {@code CANCELLED}.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        boolean cutShort = false;
        for (SiteBuffer b : buffers.values()) {
            if (!b.finished) {
                cutShort = true;
                b.call.cancel(emitted >= maxRows ? "SUPERSEDED" : "CLIENT_GONE");
            }
        }
        String outcome = cutShort && emitted < maxRows ? TunnelErrorCode.CANCELLED.name()
                : unavailable.isEmpty() ? "OK" : "PARTIAL";
        gateway.completeAudit(auditId, contacted, answered, (int) Math.min(Integer.MAX_VALUE, emitted), bytes(),
                outcome);
    }
}
