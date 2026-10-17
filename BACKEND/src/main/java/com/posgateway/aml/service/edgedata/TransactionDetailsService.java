package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.edgedata.EdgePullStatus;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.model.TransactionReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills the transaction details of a workflow view (alert detail, SAR detail, case timeline and
 * network) by pulling the referenced transactions live, on demand, when the view is requested (R6;
 * R8 left these fields null). The details exist only in the response being built: nothing here
 * persists, caches or logs them.
 *
 * <p>A failed pull never fails the view: the cloud-held fields are returned and the
 * {@link EdgePullStatus} tells the Console which state to show (offline, partial, timeout, busy,
 * support access disabled).
 */
@Service
public class TransactionDetailsService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final LegacyTransactionReadService legacy;
    private final ObjectMapper mapper;

    /** Wires the details loader. */
    public TransactionDetailsService(EdgeDataCallerResolver callers, TransactionSourceResolver sources,
                                     EdgeTransactionProxyService edge, LegacyTransactionReadService legacy,
                                     ObjectMapper mapper) {
        this.callers = callers;
        this.sources = sources;
        this.edge = edge;
        this.legacy = legacy;
        this.mapper = mapper;
    }

    /**
     * Pulled details of a workflow record's transactions.
     *
     * @param items  details in reference order (absent ones skipped)
     * @param byKey  details by {@code edgeAssessmentId}, and by {@code legacy:<id>} for legacy rows
     * @param status how the pull went
     */
    public record Details(List<Map<String, Object>> items, Map<String, Map<String, Object>> byKey,
                          EdgePullStatus status) {

        /** The detail of one reference, or {@code null}. */
        public Map<String, Object> detailFor(TransactionReference ref) {
            if (ref == null) {
                return null;
            }
            if (ref.edgeAssessmentId() != null && byKey.containsKey(ref.edgeAssessmentId().toString())) {
                return byKey.get(ref.edgeAssessmentId().toString());
            }
            return ref.legacyTransactionId() == null ? null : byKey.get("legacy:" + ref.legacyTransactionId());
        }

        /** Nothing pulled, with the given status. */
        static Details of(EdgePullStatus status) {
            return new Details(null, Map.of(), status);
        }
    }

    /**
     * Pulls the details of {@code refs}, which belong to a record of PSP {@code recordPspId}. A PSP
     * user must own the record; platform staff need support access and a reason, as for any pull.
     */
    public Details forRecord(long recordPspId, Collection<TransactionReference> refs, TunnelActor.Purpose purpose,
                             EdgeDataCallerResolver.WorkflowRefs workflow, String supportReason) {
        List<TransactionReference> list = refs == null ? List.of()
                : refs.stream().filter(r -> r != null && !r.isEmpty()).toList();
        if (list.isEmpty()) {
            return new Details(List.of(), Map.of(), EdgePullStatus.none());
        }
        EdgeDataScope scope;
        try {
            scope = callers.scopeForRecord(callers.current(), recordPspId, supportReason, purpose, workflow);
        } catch (EdgeDataException e) {
            return Details.of(EdgePullStatus.failed(null, e.error()));
        }
        TransactionSource source = sources.sourceFor(scope.pspId());
        try {
            return source == TransactionSource.EDGE ? fromEdge(scope, list) : fromLegacy(scope, list);
        } catch (EdgeDataException e) {
            return Details.of(EdgePullStatus.failed(source.name(), e.error()));
        }
    }

    /** One batchGet (per site group) of the references' decision record ids. */
    private Details fromEdge(EdgeDataScope scope, List<TransactionReference> refs) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        List<String> unpullable = new ArrayList<>();
        for (TransactionReference r : refs) {
            if (r.edgeAssessmentId() != null) {
                ids.add(r.edgeAssessmentId());
            } else {
                unpullable.add(r.label());
            }
        }
        boolean capped = ids.size() > EdgeTransactionProxyService.MAX_BATCH_IDS;
        List<UUID> wanted = ids.stream().limit(EdgeTransactionProxyService.MAX_BATCH_IDS).toList();
        EdgePull pull = wanted.isEmpty() ? EdgePull.empty() : edge.batchGet(scope, wanted);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (ObjectNode item : pull.items()) {
            Map<String, Object> m = toMap(EdgeConsoleView.edgeItem(item));
            byKey.put(item.path("edgeAssessmentId").asText(), m);
            items.add(m);
        }
        List<String> missing = new ArrayList<>(pull.missing());
        missing.addAll(unpullable);
        return new Details(items, byKey, EdgePullStatus.answered(TransactionSource.EDGE.name(),
                pull.incomplete() || capped, pull.unavailableSites(), missing));
    }

    /** The legacy rows of a PSP without an Edge, flagged {@code CLOUD_LEGACY}. */
    private Details fromLegacy(EdgeDataScope scope, List<TransactionReference> refs) {
        ObjectNode env = legacy.batchGet(scope, refs);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : env.path("items")) {
            Map<String, Object> m = toMap(item);
            if (item.hasNonNull("edgeAssessmentId")) {
                byKey.put(item.get("edgeAssessmentId").asText(), m);
            }
            byKey.put("legacy:" + item.path("txnId").asText(), m);
            items.add(m);
        }
        List<String> missing = new ArrayList<>();
        env.path("missing").forEach(n -> missing.add(n.asText()));
        return new Details(items, byKey, EdgePullStatus.answered(TransactionSource.CLOUD_LEGACY.name(), false,
                List.of(), missing));
    }

    /** JSON item as a map for the existing {@code List<Map<String, Object>>} DTO fields. */
    private Map<String, Object> toMap(JsonNode item) {
        return mapper.convertValue(item, MAP);
    }
}
