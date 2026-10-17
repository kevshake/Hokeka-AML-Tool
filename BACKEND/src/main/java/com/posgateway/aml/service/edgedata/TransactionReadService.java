package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The tunnel-backed transaction reads behind {@code GET /api/v1/transactions}, {@code /{id}},
 * {@code POST /transactions:batchGet} and {@code GET /{edgeAssessmentId}/related} (R6). It resolves
 * the caller's scope, picks the PSP's single source ({@link TransactionSource}) and returns the
 * Console shape. Tunnel outcomes reach the caller as {@code EdgeDataException}s carrying the §7.11
 * body and status (offline 503, timeout 504, busy 429) or as {@code incomplete} envelopes (partial).
 */
@Service
public class TransactionReadService {

    /** §7.6 list page limit. */
    public static final int MAX_PAGE = 200;

    /** Link types of {@code txn.related}. */
    private static final Set<String> LINK_TYPES = Set.of("CARD", "MERCHANT", "DEVICE", "ACCOUNT", "IP");

    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final LegacyTransactionReadService legacy;
    private final TransactionReferenceResolver references;
    private final AssessmentRepository assessments;

    /** Wires the router. */
    public TransactionReadService(EdgeDataCallerResolver callers, TransactionSourceResolver sources,
                                  EdgeTransactionProxyService edge, LegacyTransactionReadService legacy,
                                  TransactionReferenceResolver references, AssessmentRepository assessments) {
        this.callers = callers;
        this.sources = sources;
        this.edge = edge;
        this.legacy = legacy;
        this.references = references;
        this.assessments = assessments;
    }

    /** Who asks and on whose behalf: PSP from the principal, or the named PSP for platform staff. */
    public record Access(Long pspId, String supportReason, TunnelActor.Purpose purpose,
                         EdgeDataCallerResolver.WorkflowRefs refs) {}

    /**
     * One page of the PSP's transactions, newest first ({@code txn.search}, cursor paging only).
     *
     * @param page offset paging is not available over the tunnel: only 0 is accepted
     */
    public ObjectNode list(Access access, TransactionFilter filter, int size, String cursor, int page) {
        if (page != 0 && (cursor == null || cursor.isBlank())) {
            throw new EdgeDataException(TunnelError.invalidParams("/page"));
        }
        EdgeDataScope scope = scope(access, TunnelActor.Purpose.TRANSACTION_MONITORING);
        int limit = clamp(size);
        String c = cursor == null || cursor.isBlank() ? null : cursor;
        if (sources.sourceFor(scope.pspId()) == TransactionSource.CLOUD_LEGACY) {
            return legacy.list(scope, filter, limit, c);
        }
        return EdgeConsoleView.envelope(edge.search(scope, filter.toJson(), limit, c), TunnelOp.TXN_SEARCH);
    }

    /**
     * One transaction: the Edge detail plus the cloud decision record, tagged {@code source}. A
     * legacy numeric id of an Edge PSP is translated to its reference id (readable once R15 has
     * back-filled it).
     */
    public ObjectNode get(Access access, String rawId) {
        TransactionId id = TransactionId.parse(rawId, "/id");
        EdgeDataScope scope = scope(access, TunnelActor.Purpose.TRANSACTION_MONITORING);
        if (sources.sourceFor(scope.pspId()) == TransactionSource.CLOUD_LEGACY) {
            return legacy.get(scope, id.legacyId(), id.edgeAssessmentId());
        }
        UUID edgeId = id.isEdge() ? id.edgeAssessmentId() : referenceIdOf(scope.pspId(), id.legacyId());
        EdgePull pull = edge.get(scope, edgeId);
        ObjectNode item = EdgeConsoleView.edgeItem(pull.items().get(0));
        assessments.findFirstByPspIdAndEdgeAssessmentId(scope.pspId(), edgeId)
                .ifPresent(a -> item.set("decisionRecord", EdgeConsoleView.decisionRecord(a)));
        if (pull.incomplete()) {
            item.put("incomplete", true);
        }
        return item;
    }

    /** Up to 200 transactions by decision record id, in request order, with {@code missing}. */
    public ObjectNode batchGet(Access access, List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new EdgeDataException(TunnelError.invalidParams("/edgeAssessmentIds"));
        }
        if (rawIds.size() > EdgeTransactionProxyService.MAX_BATCH_IDS) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, "page or id limit exceeded"));
        }
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < rawIds.size(); i++) {
            ids.add(TransactionId.parseUuid(rawIds.get(i), "/edgeAssessmentIds/" + i));
        }
        EdgeDataScope scope = scope(access, TunnelActor.Purpose.TRANSACTION_MONITORING);
        if (sources.sourceFor(scope.pspId()) == TransactionSource.CLOUD_LEGACY) {
            List<TransactionReference> refs = new ArrayList<>();
            new LinkedHashSet<>(ids).forEach(u -> refs.add(TransactionReference.ofDecisionRecord(u, null, null)));
            return legacy.batchGet(scope, refs);
        }
        return EdgeConsoleView.envelope(edge.batchGet(scope, ids), TunnelOp.TXN_BATCH_GET);
    }

    /**
     * Evaluations linked to an anchor ({@code txn.related}, site-local, never the key value). Only
     * the Edge can link transactions: a PSP without an Edge gets {@code 403 OP_NOT_ALLOWED}.
     */
    public ObjectNode related(Access access, String rawId, List<String> linkBy, Integer windowDays, Integer depth,
                              int size, String cursor) {
        UUID anchor = TransactionId.parseUuid(rawId, "/edgeAssessmentId");
        List<String> links = normaliseLinks(linkBy);
        EdgeDataScope scope = scope(access, TunnelActor.Purpose.NETWORK_ANALYSIS);
        if (sources.sourceFor(scope.pspId()) == TransactionSource.CLOUD_LEGACY) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED,
                    "related transactions are served by the Edge only"));
        }
        String c = cursor == null || cursor.isBlank() ? null : cursor;
        return EdgeConsoleView.envelope(edge.related(scope, anchor, links, windowDays, depth, clamp(size), c),
                TunnelOp.TXN_RELATED);
    }

    /** Link types: upper-cased, deduplicated; default every type; unknown → INVALID_PARAMS. */
    static List<String> normaliseLinks(List<String> linkBy) {
        if (linkBy == null || linkBy.isEmpty()) {
            return List.of("CARD", "MERCHANT", "DEVICE", "ACCOUNT", "IP");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < linkBy.size(); i++) {
            String l = linkBy.get(i) == null ? "" : linkBy.get(i).strip().toUpperCase(Locale.ROOT);
            if (!LINK_TYPES.contains(l)) {
                throw new EdgeDataException(TunnelError.invalidParams("/linkBy/" + i));
            }
            out.add(l);
        }
        return List.copyOf(out);
    }

    /** The scope with the endpoint's purpose unless the caller named an allowed one. */
    private EdgeDataScope scope(Access access, TunnelActor.Purpose defaultPurpose) {
        TunnelActor.Purpose p = access.purpose() != null ? access.purpose() : defaultPurpose;
        return callers.scope(access.pspId(), access.supportReason(), p,
                access.refs() == null ? EdgeDataCallerResolver.WorkflowRefs.NONE : access.refs());
    }

    /** The reference id R8 gives a legacy row of this PSP, else {@code 404 NOT_FOUND}. */
    private UUID referenceIdOf(long pspId, Long legacyId) {
        TransactionReference ref = references.forLegacyTransactionId(pspId, legacyId);
        if (ref == null || ref.edgeAssessmentId() == null) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such transaction"));
        }
        return ref.edgeAssessmentId();
    }

    /** Page size 1..200 (default 50 is applied by the controller). */
    static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE));
    }

    /** Parses a {@code purpose} parameter; PSP-user restrictions are applied by the scope. */
    public static TunnelActor.Purpose purpose(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            TunnelActor.Purpose p = TunnelActor.Purpose.valueOf(raw.strip().toUpperCase(Locale.ROOT));
            if (EnumSet.of(TunnelActor.Purpose.SUPPORT_ACCESS, TunnelActor.Purpose.LEDGER_BACKFILL,
                    TunnelActor.Purpose.LEDGER_OPERATIONS).contains(p)) {
                throw new EdgeDataException(TunnelError.invalidParams("/purpose"));
            }
            return p;
        } catch (IllegalArgumentException e) {
            throw new EdgeDataException(TunnelError.invalidParams("/purpose"));
        }
    }
}
