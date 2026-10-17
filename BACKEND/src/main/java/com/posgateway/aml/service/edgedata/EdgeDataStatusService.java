package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelNodeDirectory;
import com.posgateway.aml.edge.tunnel.TunnelRegistry;
import com.posgateway.aml.edge.tunnel.TunnelTime;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code GET /api/v1/edge-data/status} (R6): per Edge Site of a PSP, the tunnel state (live sessions
 * from the Redis registry, last seen) and the ledger health ({@code ledger.health}, counts and
 * timestamps only). Tunnel failures never fail the call: an unreachable or refusing site is shown
 * with its §7.11 code, so the Console can render offline / timeout / busy / support-access states.
 */
@Service
public class EdgeDataStatusService {

    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final EdgeTunnelAccess tunnel;
    private final EdgeNodeRepository nodes;
    private final Clock clock = Clock.systemUTC();

    /** Wires the status view. */
    public EdgeDataStatusService(EdgeDataCallerResolver callers, TransactionSourceResolver sources,
                                 EdgeTransactionProxyService edge, EdgeTunnelAccess tunnel, EdgeNodeRepository nodes) {
        this.callers = callers;
        this.sources = sources;
        this.edge = edge;
        this.tunnel = tunnel;
        this.nodes = nodes;
    }

    /** Builds the status document of the caller's PSP (or the named PSP for platform staff). */
    public ObjectNode status(Long requestedPspId, String supportReason) {
        EdgeDataScope scope = callers.scope(requestedPspId, supportReason, TunnelActor.Purpose.TRANSACTION_MONITORING,
                EdgeDataCallerResolver.WorkflowRefs.NONE);
        long psp = scope.pspId();
        Instant now = clock.instant();
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("pspId", psp);
        out.put("source", sources.sourceFor(psp).name());
        out.put("tunnelEnabled", tunnel.tunnelEnabled());
        out.put("generatedAt", TunnelTime.formatSeconds(now));

        Map<String, List<EdgeNode>> bySite = new LinkedHashMap<>();
        for (EdgeNode n : nodes.findByPspIdOrderByCreatedAtDesc(psp)) {
            bySite.computeIfAbsent(n.effectiveSiteId(), k -> new ArrayList<>()).add(n);
        }
        List<TunnelRegistry.Entry> live = tunnel.registry().map(r -> r.liveEntries(psp, now)).orElse(List.of());
        Optional<TunnelNodeDirectory> directory = tunnel.directory();

        Map<String, ObjectNode> ledgerBySite = new LinkedHashMap<>();
        ObjectNode ledgerError = null;
        boolean anyOnline = live.stream().anyMatch(e -> bySite.containsKey(e.siteId()));
        if (anyOnline) {
            try {
                EdgeDataResult health = edge.ledgerHealth(scope);
                health.itemsBySite().forEach((site, items) -> {
                    if (!items.isEmpty()) {
                        ledgerBySite.put(site, items.get(0));
                    }
                });
                for (EdgeDataResult.UnavailableSite u : health.unavailableSites()) {
                    ledgerBySite.put(u.siteId(), JsonNodeFactory.instance.objectNode().put("state", u.code().name()));
                }
            } catch (EdgeDataException e) {
                ledgerError = errorBody(e.error());
            }
        }

        ArrayNode sites = out.putArray("sites");
        for (Map.Entry<String, List<EdgeNode>> s : bySite.entrySet()) {
            ObjectNode site = sites.addObject();
            site.put("siteId", s.getKey());
            List<TunnelRegistry.Entry> sessions = live.stream().filter(e -> s.getKey().equals(e.siteId())).toList();
            site.put("tunnel", !tunnel.tunnelEnabled() ? "DISABLED" : sessions.isEmpty() ? "OFFLINE" : "ONLINE");
            site.put("liveSessions", sessions.size());
            directory.ifPresent(d -> site.put("lastSeenAt", TunnelTime.formatSeconds(d.lastSeen(s.getValue()))));
            ArrayNode nodeArr = site.putArray("nodes");
            for (EdgeNode n : s.getValue()) {
                ObjectNode node = nodeArr.addObject();
                node.put("edgeId", n.getEdgeId());
                node.put("displayName", n.getDisplayName());
                node.put("status", n.getStatus() == null ? null : n.getStatus().name());
                List<TunnelRegistry.Entry> mine = sessions.stream().filter(e -> n.getEdgeId().equals(e.edgeId())).toList();
                node.put("connected", !mine.isEmpty());
                node.put("inflight", mine.stream().mapToInt(TunnelRegistry.Entry::inflight).sum());
                if (n.getLastSeenAt() != null) {
                    node.put("lastPolledAt", TunnelTime.formatSeconds(n.getLastSeenAt()));
                }
                if (n.getAgentVersion() != null) {
                    node.put("agentVersion", n.getAgentVersion());
                }
            }
            if (ledgerError != null) {
                site.set("ledger", ledgerError.deepCopy());
            } else if (ledgerBySite.containsKey(s.getKey())) {
                site.set("ledger", ledgerBySite.get(s.getKey()));
            } else {
                site.set("ledger", JsonNodeFactory.instance.objectNode()
                        .put("state", sessions.isEmpty() ? "EDGE_OFFLINE" : "UNKNOWN"));
            }
        }
        return out;
    }

    /** The §7.11 body of a failed health pull, as a per-site ledger state. */
    private static ObjectNode errorBody(TunnelError error) {
        ObjectNode n = error.toJson();
        n.put("state", error.code().name());
        return n;
    }
}
