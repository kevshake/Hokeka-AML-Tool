package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.edgedata.EdgePullStatus;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.case_management.CaseNetworkService;
import com.posgateway.aml.service.case_management.CaseTimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R6 live fill of workflow views: details by reference order, pull status instead of a failed view,
 * tenant refusal before any pull, the legacy source, and the case timeline / graph enrichment
 * (including txn.related expansion).
 */
class TransactionDetailsServiceTest {

    private static final UUID A = UUID.fromString("0000000a-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("0000000b-0000-4000-8000-000000000002");

    private final EdgeDataCallerResolver callers = mock(EdgeDataCallerResolver.class);
    private final TransactionSourceResolver sources = mock(TransactionSourceResolver.class);
    private final EdgeTransactionProxyService edge = mock(EdgeTransactionProxyService.class);
    private final LegacyTransactionReadService legacy = mock(LegacyTransactionReadService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TransactionDetailsService details = new TransactionDetailsService(callers, sources, edge, legacy, mapper);
    private final EdgeDataCaller caller = new EdgeDataCaller("u-4", "COMPLIANCE_OFFICER", 7L, false);

    /** The caller is a PSP-7 officer whose scope resolves normally. */
    @BeforeEach
    void scope() {
        when(callers.current()).thenReturn(caller);
        when(callers.scopeForRecord(eq(caller), anyLong(), any(), any(), any())).thenAnswer(inv -> {
            long psp = inv.getArgument(1);
            if (psp != 7L) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH, "other PSP"));
            }
            TunnelActor actor = TunnelActor.pspUser("u-4", "COMPLIANCE_OFFICER", 7L, inv.getArgument(3), null);
            return new EdgeDataScope(7L, actor, caller);
        });
        when(sources.sourceFor(7L)).thenReturn(TransactionSource.EDGE);
    }

    /** A detail item. */
    private static ObjectNode item(UUID id) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("edgeAssessmentId", id.toString());
        n.put("siteId", "site-a");
        n.put("evaluatedAt", "2026-10-16T09:00:00.000Z");
        n.put("decision", "HOLD");
        n.put("amountMinor", 900);
        return n;
    }

    /** Verifies: details follow the references, carry source EDGE, and missing ids are reported. */
    @Test
    void edgeDetailsAreKeyedByReferenceWithStatus() {
        when(edge.batchGet(any(), anyList())).thenReturn(new EdgePull(List.of(item(A)), null, false, null, List.of(),
                List.of(B.toString()), List.of(UUID.randomUUID())));
        TransactionDetailsService.Details d = details.forRecord(7L,
                List.of(TransactionReference.ofDecisionRecord(A, "r1", "site-a"),
                        TransactionReference.ofDecisionRecord(B, "r2", "site-a")),
                TunnelActor.Purpose.ALERT_REVIEW, EdgeDataCallerResolver.WorkflowRefs.ofAlert(5L), null);
        assertThat(d.items()).hasSize(1);
        assertThat(d.items().get(0)).containsEntry("source", "EDGE").containsEntry("amountCents", 900);
        assertThat(d.detailFor(TransactionReference.ofDecisionRecord(A, null, null))).isNotNull();
        assertThat(d.status().state()).isEqualTo("OK");
        assertThat(d.status().source()).isEqualTo("EDGE");
        assertThat(d.status().missing()).containsExactly(B.toString());
    }

    /** Verifies: an offline Edge gives a status, not a failed view, and no details. */
    @Test
    void offlineEdgeGivesAFailedStatusNotAnError() {
        when(edge.batchGet(any(), anyList())).thenThrow(new EdgeDataException(
                TunnelError.offline("site-a", Instant.parse("2026-10-16T08:00:00Z"))));
        TransactionDetailsService.Details d = details.forRecord(7L, List.of(TransactionReference.ofDecisionRecord(A,
                null, null)), TunnelActor.Purpose.ALERT_REVIEW, EdgeDataCallerResolver.WorkflowRefs.NONE, null);
        assertThat(d.items()).isNull();
        assertThat(d.status().state()).isEqualTo("EDGE_OFFLINE");
        assertThat(d.status().error()).containsEntry("lastSeenAt", "2026-10-16T08:00:00Z").containsEntry("httpStatus", 503);
    }

    /** Verifies: a partial batchGet is a PARTIAL status naming the site. */
    @Test
    void partialPullIsPartial() {
        when(edge.batchGet(any(), anyList())).thenReturn(new EdgePull(List.of(item(A)), null, true, null,
                List.of(new EdgeDataResult.UnavailableSite("site-b", TunnelErrorCode.EDGE_TIMEOUT, null)), List.of(),
                List.of()));
        TransactionDetailsService.Details d = details.forRecord(7L, List.of(TransactionReference.ofDecisionRecord(A,
                null, null)), TunnelActor.Purpose.ALERT_REVIEW, EdgeDataCallerResolver.WorkflowRefs.NONE, null);
        assertThat(d.status().state()).isEqualTo("PARTIAL");
        assertThat(d.status().unavailableSites().get(0)).containsEntry("siteId", "site-b").containsEntry("code", "EDGE_TIMEOUT");
    }

    /** Verifies: a record of another PSP is refused before anything is pulled. */
    @Test
    void anotherPspsRecordIsRefusedWithoutAPull() {
        TransactionDetailsService.Details d = details.forRecord(8L, List.of(TransactionReference.ofDecisionRecord(A,
                null, null)), TunnelActor.Purpose.ALERT_REVIEW, EdgeDataCallerResolver.WorkflowRefs.NONE, null);
        assertThat(d.status().state()).isEqualTo("TENANT_MISMATCH");
        verifyNoInteractions(edge, legacy);
    }

    /** Verifies: no reference means no pull. */
    @Test
    void noReferenceNoPull() {
        TransactionDetailsService.Details d = details.forRecord(7L, List.of(), TunnelActor.Purpose.ALERT_REVIEW,
                EdgeDataCallerResolver.WorkflowRefs.NONE, null);
        assertThat(d.status().state()).isEqualTo("NONE");
        verifyNoInteractions(edge, legacy);
    }

    /** Verifies: a PSP without an Edge gets its legacy rows, keyed by legacy id, source CLOUD_LEGACY. */
    @Test
    void legacyPspGetsLegacyRows() {
        when(sources.sourceFor(7L)).thenReturn(TransactionSource.CLOUD_LEGACY);
        ObjectNode env = JsonNodeFactory.instance.objectNode();
        env.putArray("items").addObject().put("txnId", 77).put("source", "CLOUD_LEGACY");
        env.putArray("missing");
        when(legacy.batchGet(any(), anyList())).thenReturn(env);
        TransactionDetailsService.Details d = details.forRecord(7L, List.of(TransactionReference.ofLegacyTransaction(77L)),
                TunnelActor.Purpose.SAR_FILING, EdgeDataCallerResolver.WorkflowRefs.ofFiling("SAR-1"), null);
        assertThat(d.status().source()).isEqualTo("CLOUD_LEGACY");
        assertThat(d.detailFor(TransactionReference.ofLegacyTransaction(77L))).containsEntry("source", "CLOUD_LEGACY");
        verifyNoInteractions(edge);
    }

    /** Verifies: the timeline's TRANSACTION events get their details and the timeline its status. */
    @Test
    void timelineEventsAreFilled() {
        when(edge.batchGet(any(), anyList())).thenReturn(new EdgePull(List.of(item(A)), null, false, null, List.of(),
                List.of(), List.of()));
        CaseTransactionDetailsEnricher enricher = new CaseTransactionDetailsEnricher(details, callers, sources, edge, mapper);
        Map<String, Object> data = new LinkedHashMap<>(TransactionReference.ofDecisionRecord(A, "r1", "site-a").toMap());
        data.put("transactionDetails", null);
        CaseTimelineService.CaseTimelineDTO timeline = CaseTimelineService.CaseTimelineDTO.builder().caseId(3L)
                .events(new ArrayList<>(List.of(CaseTimelineService.TimelineEvent.builder().type("TRANSACTION")
                        .timestamp(LocalDateTime.now()).data(data).build()))).build();
        enricher.enrichTimeline(7L, 3L, timeline, true, null);
        assertThat(timeline.getTransactionDetailsStatus().state()).isEqualTo("OK");
        @SuppressWarnings("unchecked")
        Map<String, Object> filled = (Map<String, Object>) data.get("transactionDetails");
        assertThat(filled).containsEntry("edgeAssessmentId", A.toString()).containsEntry("source", "EDGE");
    }

    /** Verifies: the graph's transaction nodes are filled and txn.related adds linked nodes and edges. */
    @Test
    void graphIsFilledAndExpandedWithRelatedLinks() {
        when(edge.batchGet(any(), anyList())).thenReturn(new EdgePull(List.of(item(A)), null, false, null, List.of(),
                List.of(), List.of()));
        ObjectNode rel = JsonNodeFactory.instance.objectNode();
        rel.put("link", "CARD");
        rel.put("hop", 1);
        rel.put("viaEdgeAssessmentId", A.toString());
        rel.set("transaction", item(B));
        when(edge.related(any(), eq(A), anyList(), isNull(), isNull(), anyInt(), isNull()))
                .thenReturn(new EdgePull(List.of(rel), null, false, null, List.of(), List.of(), List.of()));
        CaseTransactionDetailsEnricher enricher = new CaseTransactionDetailsEnricher(details, callers, sources, edge, mapper);
        CaseNetworkService.NetworkGraphDTO graph = new CaseNetworkService.NetworkGraphDTO();
        Map<String, Object> data = new LinkedHashMap<>(TransactionReference.ofDecisionRecord(A, "r1", "site-a").toMap());
        graph.addNode(CaseNetworkService.NetworkNode.builder().id("TXN_" + A).type("TRANSACTION").data(data).build());
        enricher.enrichGraph(7L, 3L, graph, true, List.of("card"), null, null, null);
        assertThat(graph.getTransactionDetailsStatus().state()).isEqualTo("OK");
        assertThat(data.get("transactionDetails")).isNotNull();
        assertThat(graph.getNodes()).extracting(CaseNetworkService.NetworkNode::getId).contains("TXN_" + B);
        assertThat(graph.getEdges()).anyMatch(e -> e.getFrom().equals("TXN_" + A) && e.getTo().equals("TXN_" + B)
                && e.getType().equals("LINKED_BY_CARD"));
        assertThat(graph.getRelatedStatus().state()).isEqualTo("OK");
        verify(edge).related(any(), eq(A), eq(List.of("CARD")), isNull(), isNull(), anyInt(), isNull());
    }

    /** Verifies: not pulling is reported as NOT_REQUESTED. */
    @Test
    void notRequestedIsReported() {
        CaseTransactionDetailsEnricher enricher = new CaseTransactionDetailsEnricher(details, callers, sources, edge, mapper);
        CaseNetworkService.NetworkGraphDTO graph = new CaseNetworkService.NetworkGraphDTO();
        enricher.enrichGraph(7L, 3L, graph, false, null, null, null, null);
        assertThat(graph.getTransactionDetailsStatus()).isEqualTo(EdgePullStatus.notRequested());
        verifyNoInteractions(edge);
    }
}
