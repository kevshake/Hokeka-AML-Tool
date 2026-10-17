package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.edgedata.EdgePullStatus;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.case_management.CaseNetworkService;
import com.posgateway.aml.service.case_management.CaseTimelineService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills the transaction details of the case timeline and the case network graph (R8 left
 * {@code transactionDetails} null in both), and optionally expands the graph with the Edge's
 * {@code txn.related} links. Pulls happen when the view is requested and live only in the
 * response.
 */
@Service
public class CaseTransactionDetailsEnricher {

    /** Anchors expanded with {@code txn.related} per graph view (one audited pull each). */
    public static final int MAX_RELATED_ANCHORS = 5;
    /** Linked evaluations per anchor. */
    public static final int RELATED_PAGE = 50;

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final TransactionDetailsService details;
    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final ObjectMapper mapper;

    /** Wires the enricher. */
    public CaseTransactionDetailsEnricher(TransactionDetailsService details, EdgeDataCallerResolver callers,
                                          TransactionSourceResolver sources, EdgeTransactionProxyService edge,
                                          ObjectMapper mapper) {
        this.details = details;
        this.callers = callers;
        this.sources = sources;
        this.edge = edge;
        this.mapper = mapper;
    }

    /** Sets {@code transactionDetails} on every TRANSACTION event and the pull status on the timeline. */
    public void enrichTimeline(long casePspId, Long caseId, CaseTimelineService.CaseTimelineDTO timeline,
                               boolean pull, String supportReason) {
        if (!pull) {
            timeline.setTransactionDetailsStatus(EdgePullStatus.notRequested());
            return;
        }
        List<Map<String, Object>> transactionData = new ArrayList<>();
        for (CaseTimelineService.TimelineEvent e : timeline.getEvents()) {
            if ("TRANSACTION".equals(e.getType()) && e.getData() instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) m;
                transactionData.add(data);
            }
        }
        List<TransactionReference> refs = transactionData.stream().map(CaseTransactionDetailsEnricher::referenceOf)
                .toList();
        TransactionDetailsService.Details d = details.forRecord(casePspId, refs,
                TunnelActor.Purpose.CASE_INVESTIGATION, EdgeDataCallerResolver.WorkflowRefs.ofCase(caseId), supportReason);
        for (int i = 0; i < transactionData.size(); i++) {
            transactionData.get(i).put("transactionDetails", d.detailFor(refs.get(i)));
        }
        timeline.setTransactionDetailsStatus(d.status());
    }

    /**
     * Sets {@code transactionDetails} on the graph's TRANSACTION nodes and, when {@code linkBy} is
     * given, adds the evaluations linked to (up to {@value #MAX_RELATED_ANCHORS}) case transactions as
     * nodes with {@code LINKED_BY_<type>} edges. The link key value is never present.
     */
    public void enrichGraph(long casePspId, Long caseId, CaseNetworkService.NetworkGraphDTO graph, boolean pull,
                            List<String> linkBy, Integer relatedDepth, Integer windowDays, String supportReason) {
        if (!pull) {
            graph.setTransactionDetailsStatus(EdgePullStatus.notRequested());
            return;
        }
        Map<CaseNetworkService.NetworkNode, Map<String, Object>> txnNodes = new LinkedHashMap<>();
        for (CaseNetworkService.NetworkNode n : graph.getNodes()) {
            if ("TRANSACTION".equals(n.getType()) && n.getData() instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) m;
                txnNodes.put(n, data);
            }
        }
        List<TransactionReference> refs = txnNodes.values().stream().map(CaseTransactionDetailsEnricher::referenceOf)
                .toList();
        TransactionDetailsService.Details d = details.forRecord(casePspId, refs, TunnelActor.Purpose.CASE_INVESTIGATION,
                EdgeDataCallerResolver.WorkflowRefs.ofCase(caseId), supportReason);
        int i = 0;
        for (Map<String, Object> data : txnNodes.values()) {
            data.put("transactionDetails", d.detailFor(refs.get(i++)));
        }
        graph.setTransactionDetailsStatus(d.status());
        if (linkBy != null && !linkBy.isEmpty()) {
            graph.setRelatedStatus(expandRelated(casePspId, caseId, graph, txnNodes, refs, linkBy, relatedDepth,
                    windowDays, supportReason));
        }
    }

    /** One {@code txn.related} pull per anchor (bounded), merged into the graph. */
    private EdgePullStatus expandRelated(long casePspId, Long caseId, CaseNetworkService.NetworkGraphDTO graph,
                                         Map<CaseNetworkService.NetworkNode, Map<String, Object>> txnNodes,
                                         List<TransactionReference> refs, List<String> linkBy, Integer depth,
                                         Integer windowDays, String supportReason) {
        EdgeDataScope scope;
        List<String> links;
        try {
            links = TransactionReadService.normaliseLinks(linkBy);
            scope = callers.scopeForRecord(callers.current(), casePspId, supportReason,
                    TunnelActor.Purpose.NETWORK_ANALYSIS, EdgeDataCallerResolver.WorkflowRefs.ofCase(caseId));
            if (sources.sourceFor(scope.pspId()) != TransactionSource.EDGE) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED,
                        "related transactions are served by the Edge only"));
            }
        } catch (EdgeDataException e) {
            return EdgePullStatus.failed(null, e.error());
        }
        List<CaseNetworkService.NetworkNode> nodes = new ArrayList<>(txnNodes.keySet());
        boolean incomplete = false;
        List<com.posgateway.aml.edge.tunnel.EdgeDataResult.UnavailableSite> unavailable = new ArrayList<>();
        int anchors = 0;
        EdgeDataException lastFailure = null;
        int answered = 0;
        for (int i = 0; i < refs.size() && anchors < MAX_RELATED_ANCHORS; i++) {
            UUID anchor = refs.get(i).edgeAssessmentId();
            if (anchor == null) {
                continue;
            }
            anchors++;
            try {
                EdgePull pull = edge.related(scope, anchor, links, windowDays, depth, RELATED_PAGE, null);
                answered++;
                incomplete |= pull.incomplete() || pull.nextCursor() != null;
                unavailable.addAll(pull.unavailableSites());
                addRelated(graph, nodes.get(i).getId(), pull);
            } catch (EdgeDataException e) {
                lastFailure = e;
            }
        }
        if (answered == 0 && lastFailure != null) {
            return EdgePullStatus.failed(TransactionSource.EDGE.name(), lastFailure.error());
        }
        if (anchors == 0) {
            return EdgePullStatus.none();
        }
        return EdgePullStatus.answered(TransactionSource.EDGE.name(), incomplete || lastFailure != null, unavailable,
                List.of());
    }

    /** Adds the linked evaluations of one anchor as TRANSACTION nodes and LINKED_BY edges. */
    private void addRelated(CaseNetworkService.NetworkGraphDTO graph, String anchorNodeId, EdgePull pull) {
        for (ObjectNode item : pull.items()) {
            ObjectNode tx = EdgeConsoleView.edgeItem(item.path("transaction"));
            String id = tx.path("edgeAssessmentId").asText();
            String nodeId = "TXN_" + id;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("edgeAssessmentId", id);
            if (tx.hasNonNull("txnRef")) {
                data.put("txnRef", tx.get("txnRef").asText());
            }
            data.put("siteId", tx.path("siteId").asText());
            data.put("decision", tx.path("decision").asText());
            data.put("link", item.path("link").asText());
            data.put("hop", item.path("hop").asInt());
            data.put("transactionDetails", mapper.convertValue(tx, MAP));
            graph.addNode(CaseNetworkService.NetworkNode.builder().id(nodeId).type("TRANSACTION")
                    .label("TXN: " + (tx.hasNonNull("txnRef") ? tx.get("txnRef").asText() : id)).data(data).build());
            String via = item.path("viaEdgeAssessmentId").asText();
            String from = item.path("hop").asInt() <= 1 ? anchorNodeId : "TXN_" + via;
            String link = item.path("link").asText();
            graph.addEdge(CaseNetworkService.NetworkEdge.builder().from(from).to(nodeId).type("LINKED_BY_" + link)
                    .label("Linked by " + link.toLowerCase(java.util.Locale.ROOT)).build());
        }
    }

    /** Rebuilds the ID reference that the case services put into an event / node data map. */
    static TransactionReference referenceOf(Map<String, Object> data) {
        Object legacy = data.get("transactionId");
        Object edgeId = data.get("edgeAssessmentId");
        Long legacyId = legacy instanceof Number n ? n.longValue() : null;
        UUID uuid = null;
        if (edgeId != null) {
            try {
                uuid = UUID.fromString(String.valueOf(edgeId));
            } catch (IllegalArgumentException e) {
                uuid = null;
            }
        }
        Object txnRef = data.get("txnRef");
        Object site = data.get("siteId");
        return new TransactionReference(legacyId, uuid, txnRef == null ? null : String.valueOf(txnRef),
                site == null ? null : String.valueOf(site));
    }
}
