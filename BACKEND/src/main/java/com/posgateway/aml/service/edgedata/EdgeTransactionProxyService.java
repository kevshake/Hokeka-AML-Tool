package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataRequest;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.EdgeDataStream;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Edge reads of the R6 data proxy, on top of the R4 {@code EdgeDataGateway} (which gates, audits,
 * fans out and merges). This class adds the routing the gateway leaves to its caller (§7.12):
 * gets by {@code edgeAssessmentId} go only to the site named in the PSP's cloud decision record,
 * a batchGet whose ids live on different sites becomes one pull per site, and {@code txn.related}
 * (site-local linkage) is sent to the anchor's site.
 *
 * <p>Nothing here logs, caches or stores an item (§7.16).
 */
@Service
public class EdgeTransactionProxyService {

    /** §7.6 batchGet limit. */
    public static final int MAX_BATCH_IDS = 200;

    /** Errors that are a property of the request: they fail a multi-group batchGet as a whole. */
    private static final Set<TunnelErrorCode> REQUEST_FATAL = EnumSet.of(TunnelErrorCode.INVALID_PARAMS,
            TunnelErrorCode.INVALID_CURSOR, TunnelErrorCode.PAGE_LIMIT_EXCEEDED, TunnelErrorCode.TENANT_MISMATCH,
            TunnelErrorCode.SUPPORT_ACCESS_DISABLED, TunnelErrorCode.OP_NOT_ALLOWED, TunnelErrorCode.UNSUPPORTED_VERSION,
            TunnelErrorCode.CANCELLED, TunnelErrorCode.INTERNAL);

    /** §7.13 preference when no site group answered: busy over timeout over offline. */
    private static final List<TunnelErrorCode> FAILURE_PREFERENCE = List.of(TunnelErrorCode.EDGE_BUSY,
            TunnelErrorCode.EDGE_TIMEOUT, TunnelErrorCode.DEADLINE_EXCEEDED, TunnelErrorCode.LEDGER_UNAVAILABLE,
            TunnelErrorCode.EDGE_OFFLINE);

    private final EdgeTunnelAccess tunnel;
    private final AssessmentRepository assessments;

    /** Wires the proxy over the tunnel and the cloud decision records. */
    public EdgeTransactionProxyService(EdgeTunnelAccess tunnel, AssessmentRepository assessments) {
        this.tunnel = tunnel;
        this.assessments = assessments;
    }

    /** {@code txn.search} over every site of the PSP (k-way merged, composite cursor). */
    public EdgePull search(EdgeDataScope scope, ObjectNode filter, int limit, String cursor) {
        EdgeDataRequest r = EdgeDataRequest.of(scope.pspId(), TunnelOp.TXN_SEARCH, filter, scope.actor())
                .page(limit, cursor);
        return EdgePull.of(tunnel.gateway().request(r));
    }

    /**
     * {@code txn.get} by Edge id: to the decision record's site when the cloud has the record,
     * else to every site of the PSP (a record still in the Edge outbox).
     *
     * @throws EdgeDataException {@code NOT_FOUND} when no site has it; the best site failure when
     *                           no site answered
     */
    public EdgePull get(EdgeDataScope scope, UUID edgeAssessmentId) {
        ObjectNode params = JsonNodeFactory.instance.objectNode().put("edgeAssessmentId", edgeAssessmentId.toString());
        EdgeDataRequest r = EdgeDataRequest.of(scope.pspId(), TunnelOp.TXN_GET, params, scope.actor());
        String site = siteOf(scope.pspId(), edgeAssessmentId);
        if (site != null) {
            r = r.onSites(Set.of(site));
        }
        EdgePull pull = EdgePull.of(tunnel.gateway().request(r));
        if (pull.items().isEmpty()) {
            throw new EdgeDataException(pull.unavailableSites().isEmpty()
                    ? TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such evaluation on any site")
                    : failureOf(pull.unavailableSites()));
        }
        return pull;
    }

    /**
     * {@code txn.batchGet} of up to 200 ids. Ids whose decision record names a site are grouped per
     * site; ids without a cloud record go to every site. One audited pull per group; the answer keeps
     * the requested order (§7.13 union semantics).
     */
    public EdgePull batchGet(EdgeDataScope scope, Collection<UUID> ids) {
        List<String> order = new ArrayList<>();
        new LinkedHashSet<>(ids).forEach(id -> order.add(id.toString()));
        if (order.isEmpty()) {
            return EdgePull.empty();
        }
        if (order.size() > MAX_BATCH_IDS) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, "page or id limit exceeded"));
        }
        Map<String, String> siteById = sitesOf(scope.pspId(), ids);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String id : order) {
            groups.computeIfAbsent(siteById.getOrDefault(id, ""), k -> new ArrayList<>()).add(id);
        }
        List<EdgePull> parts = new ArrayList<>();
        List<EdgeDataResult.UnavailableSite> failed = new ArrayList<>();
        List<TunnelError> failures = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            ArrayNode arr = params.putArray("edgeAssessmentIds");
            g.getValue().forEach(arr::add);
            EdgeDataRequest r = EdgeDataRequest.of(scope.pspId(), TunnelOp.TXN_BATCH_GET, params, scope.actor());
            if (!g.getKey().isEmpty()) {
                r = r.onSites(Set.of(g.getKey()));
            }
            try {
                parts.add(EdgePull.of(tunnel.gateway().request(r)));
            } catch (EdgeDataException e) {
                if (groups.size() == 1 || REQUEST_FATAL.contains(e.error().code())) {
                    throw e;
                }
                failures.add(e.error());
                String site = e.error().siteId() != null ? e.error().siteId() : g.getKey().isEmpty() ? null : g.getKey();
                failed.add(new EdgeDataResult.UnavailableSite(site, e.error().code(),
                        e.error().code() == TunnelErrorCode.EDGE_OFFLINE ? e.error().lastSeenAt() : null));
            }
        }
        if (parts.isEmpty()) {
            throw new EdgeDataException(best(failures));
        }
        return EdgePull.mergeGets(order, parts, failed);
    }

    /**
     * {@code txn.related} around an anchor. Linkage is site-local (§7.6), so the pull goes to the
     * anchor's site: the decision record's, else the site a {@code txn.get} finds it on.
     */
    public EdgePull related(EdgeDataScope scope, UUID anchor, List<String> linkBy, Integer windowDays, Integer depth,
                            int limit, String cursor) {
        String site = siteOf(scope.pspId(), anchor);
        if (site == null) {
            site = get(scope, anchor).items().get(0).path("siteId").asText(null);
        }
        ObjectNode params = JsonNodeFactory.instance.objectNode().put("edgeAssessmentId", anchor.toString());
        ArrayNode links = params.putArray("linkBy");
        linkBy.forEach(links::add);
        if (windowDays != null) {
            params.put("windowDays", windowDays);
        }
        if (depth != null) {
            params.put("depth", depth);
        }
        EdgeDataRequest r = EdgeDataRequest.of(scope.pspId(), TunnelOp.TXN_RELATED, params, scope.actor())
                .page(limit, cursor);
        if (site != null) {
            r = r.onSites(Set.of(site));
        }
        return EdgePull.of(tunnel.gateway().request(r));
    }

    /** {@code ledger.health} of every site (one item per answering site). */
    public EdgeDataResult ledgerHealth(EdgeDataScope scope) {
        return tunnel.gateway().request(EdgeDataRequest.of(scope.pspId(), TunnelOp.LEDGER_HEALTH,
                JsonNodeFactory.instance.objectNode(), scope.actor()));
    }

    /**
     * Opens a streamed {@code export.stream} across the PSP's sites; the caller writes it straight to
     * its HTTP response and closes it.
     */
    public EdgeDataStream openExport(EdgeDataScope scope, ObjectNode params) {
        return tunnel.gateway().openExport(EdgeDataRequest.of(scope.pspId(), TunnelOp.EXPORT_STREAM, params,
                scope.actor()));
    }

    /** The site in the PSP's decision record, or {@code null} when the cloud has no record. */
    String siteOf(long pspId, UUID edgeAssessmentId) {
        return assessments.findFirstByPspIdAndEdgeAssessmentId(pspId, edgeAssessmentId)
                .map(Assessment::getSiteId).filter(s -> !s.isBlank()).orElse(null);
    }

    /** Sites of the PSP's decision records among {@code ids}, keyed by id string. */
    private Map<String, String> sitesOf(long pspId, Collection<UUID> ids) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Assessment a : assessments.findReferencedByPsp(pspId, Set.copyOf(ids))) {
            if (a.getEdgeAssessmentId() != null && a.getSiteId() != null && !a.getSiteId().isBlank()) {
                out.putIfAbsent(a.getEdgeAssessmentId().toString(), a.getSiteId());
            }
        }
        return out;
    }

    /** The answer for a get that no answering site had while other sites were unavailable. */
    private static TunnelError failureOf(List<EdgeDataResult.UnavailableSite> unavailable) {
        EdgeDataResult.UnavailableSite pick = unavailable.stream()
                .min(java.util.Comparator.comparingInt(u -> rank(u.code()))).orElseThrow();
        if (pick.code() == TunnelErrorCode.EDGE_OFFLINE) {
            return TunnelError.offline(pick.siteId(), pick.lastSeenAt());
        }
        if (pick.code() == TunnelErrorCode.EDGE_TIMEOUT) {
            return TunnelError.timeout(pick.siteId());
        }
        if (pick.code() == TunnelErrorCode.EDGE_BUSY) {
            return new TunnelError(TunnelErrorCode.EDGE_BUSY, 429, "every node of site " + pick.siteId() + " is busy",
                    true, 1_000L, null, pick.siteId(), null);
        }
        return TunnelError.of(pick.code(), "site " + pick.siteId() + " could not answer").withSite(pick.siteId());
    }

    /** The preferred failure among several site-group failures. */
    private static TunnelError best(List<TunnelError> errors) {
        return errors.stream().min(java.util.Comparator.comparingInt(e -> rank(e.code()))).orElseThrow();
    }

    /** Position in the §7.13 preference (unknown codes last). */
    private static int rank(TunnelErrorCode code) {
        int i = FAILURE_PREFERENCE.indexOf(code);
        return i < 0 ? FAILURE_PREFERENCE.size() : i;
    }
}
