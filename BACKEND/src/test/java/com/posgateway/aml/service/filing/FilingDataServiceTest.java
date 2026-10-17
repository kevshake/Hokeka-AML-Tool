package com.posgateway.aml.service.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.edgedata.EdgeDataCaller;
import com.posgateway.aml.service.edgedata.EdgeDataScope;
import com.posgateway.aml.service.edgedata.EdgeTransactionProxyService;
import com.posgateway.aml.service.edgedata.LegacyTransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionSource;
import com.posgateway.aml.service.edgedata.TransactionSourceResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Filing pulls refuse anything short of complete data (R9: never file an incomplete document). */
class FilingDataServiceTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final UUID A = UUID.fromString("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f");
    private static final UUID B = UUID.fromString("5a7b9c1d-3e5f-4a6b-9c8d-7e6f5a4b3c2d");

    private final TransactionSourceResolver sources = mock(TransactionSourceResolver.class);
    private final EdgeTransactionProxyService edge = mock(EdgeTransactionProxyService.class);
    private final LegacyTransactionReadService legacy = mock(LegacyTransactionReadService.class);
    private final FilingDataService service = new FilingDataService(sources, edge, legacy);
    private final EdgeDataScope scope = new EdgeDataScope(45L, TunnelActor.pspUser("u-12", "MLRO", 45L,
            TunnelActor.Purpose.SAR_FILING, null), new EdgeDataCaller("u-12", "MLRO", 45L, false));

    /** A filing.extract TRANSACTION item. */
    static ObjectNode item(UUID id, Long amountMinor) {
        ObjectNode t = M.createObjectNode();
        t.put("edgeAssessmentId", id.toString());
        t.put("txnRef", "AUTH-" + id.toString().substring(0, 8));
        t.put("siteId", "site-a");
        t.put("evaluatedAt", "2026-10-16T10:02:51.880Z");
        t.put("decision", "ALERT");
        t.put("score", 72);
        if (amountMinor != null) {
            t.put("amountMinor", amountMinor);
        }
        t.put("currency", "KES");
        t.putArray("triggeredRuleIds").add(9001);
        t.putObject("features").put("device_id", "dv-1").putObject("nested").put("x", 1);
        t.put("legalHold", true);
        ObjectNode item = M.createObjectNode();
        item.put("kind", "TRANSACTION");
        item.set("transaction", t);
        return item;
    }

    /** A CTR_AGGREGATE item for {@code anchor}. */
    static ObjectNode aggregate(UUID anchor) {
        ObjectNode a = M.createObjectNode();
        a.put("kind", "CTR_AGGREGATE");
        a.put("anchorEdgeAssessmentId", anchor.toString());
        a.put("businessDate", "2026-10-16");
        a.put("timezone", "Africa/Nairobi");
        a.put("reportingCurrency", "USD");
        a.put("cashTotalMinor", 1_617_000L);
        a.put("thresholdMinor", 1_500_000L);
        a.put("txnCount", 2);
        a.putArray("edgeAssessmentIds").add(anchor.toString()).add(B.toString());
        a.put("fxAsOf", "2026-10-16T06:00:00Z");
        a.put("configVersion", 14);
        return a;
    }

    /** Verifies: a complete Edge answer becomes filed transactions in reference order. */
    @Test
    void completeEdgeAnswerIsMapped() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.EDGE);
        UUID audit = UUID.randomUUID();
        when(edge.filingExtract(eq(scope), eq("SAR"), eq("SAR-1"), anyCollection())).thenReturn(
                new EdgeTransactionProxyService.FilingPull(List.of(item(A, 210_000_000L), item(B, 500L)), List.of(),
                        List.of(), false, List.of(new EdgeDataResult.UnavailableSite("site-b", TunnelErrorCode.EDGE_OFFLINE,
                        Instant.EPOCH)), List.of(audit)));

        FilingExtract x = service.extractSar(scope, "SAR", "SAR-1", List.of(
                TransactionReference.ofDecisionRecord(A, null, "site-a"), TransactionReference.ofDecisionRecord(B, null, null)));

        assertThat(x.source()).isEqualTo("EDGE");
        assertThat(x.transactions()).extracting(FilingExtract.Txn::edgeAssessmentId).containsExactly(A.toString(), B.toString());
        assertThat(x.transactions().get(0).amountMinor()).isEqualTo(210_000_000L);
        assertThat(x.transactions().get(0).features()).containsEntry("device_id", "dv-1").doesNotContainKey("nested");
        assertThat(x.transactions().get(0).legalHold()).isTrue();
        assertThat(x.auditIds()).containsExactly(audit);
    }

    /** Verifies: an id no answering site returned refuses the filing as retryable 503. */
    @Test
    void missingTransactionRefusesRetryably() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.EDGE);
        when(edge.filingExtract(any(), any(), any(), anyCollection())).thenReturn(new EdgeTransactionProxyService.FilingPull(
                List.of(item(A, 1L)), List.of(), List.of(B.toString()), false,
                List.of(new EdgeDataResult.UnavailableSite("site-b", TunnelErrorCode.EDGE_OFFLINE, Instant.EPOCH)),
                List.of()));

        assertThatThrownBy(() -> service.extractSar(scope, "SAR", "SAR-1", List.of(
                TransactionReference.ofDecisionRecord(A, null, null), TransactionReference.ofDecisionRecord(B, null, null))))
                .isInstanceOfSatisfying(FilingException.class, e -> {
                    assertThat(e.code()).isEqualTo(FilingException.Code.FILING_INCOMPLETE);
                    assertThat(e.httpStatus()).isEqualTo(503);
                    assertThat(e.toJson().get("retryable").asBoolean()).isTrue();
                    assertThat(e.toJson().get("missingCount").asInt()).isEqualTo(1);
                    assertThat(e.toJson().get("unavailableSites").get(0).get("siteId").asText()).isEqualTo("site-b");
                });
    }

    /** Verifies: an offline Edge refuses with its §7.11 error inside, status 503, retryable. */
    @Test
    void offlineEdgeRefusesWithTheTunnelError() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.EDGE);
        when(edge.filingExtract(any(), any(), any(), anyCollection()))
                .thenThrow(new EdgeDataException(TunnelError.offline("site-a", Instant.parse("2026-10-16T09:00:00Z"))));

        assertThatThrownBy(() -> service.extractSar(scope, "SAR", "SAR-1",
                List.of(TransactionReference.ofDecisionRecord(A, null, null))))
                .isInstanceOfSatisfying(FilingException.class, e -> {
                    assertThat(e.code()).isEqualTo(FilingException.Code.EDGE_PULL_FAILED);
                    assertThat(e.httpStatus()).isEqualTo(503);
                    assertThat(e.toJson().get("edgeError").get("code").asText()).isEqualTo("EDGE_OFFLINE");
                    assertThat(e.toJson().get("retryable").asBoolean()).isTrue();
                });
    }

    /** Verifies: a reference without a decision record id, or an item without amount, is never filed. */
    @Test
    void unpullableReferenceOrMissingAmountRefuses() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.EDGE);
        assertThatThrownBy(() -> service.extractSar(scope, "SAR", "SAR-1",
                List.of(TransactionReference.ofLegacyTransaction(9L))))
                .isInstanceOfSatisfying(FilingException.class,
                        e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_INCOMPLETE));
        verify(edge, never()).filingExtract(any(), any(), any(), anyCollection());

        when(edge.filingExtract(any(), any(), any(), anyCollection())).thenReturn(new EdgeTransactionProxyService.FilingPull(
                List.of(item(A, null)), List.of(), List.of(), false, List.of(), List.of()));
        assertThatThrownBy(() -> service.extractSar(scope, "SAR", "SAR-1",
                List.of(TransactionReference.ofDecisionRecord(A, null, null))))
                .isInstanceOfSatisfying(FilingException.class,
                        e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_INCOMPLETE));
    }

    /** Verifies: a CTR needs its anchor's aggregate; amounts convert from minor units. */
    @Test
    void ctrNeedsItsAggregate() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.EDGE);
        TransactionReference anchor = TransactionReference.ofDecisionRecord(A, null, "site-a");
        when(edge.filingExtract(any(), eq("CTR"), eq("CTR-31"), anyCollection())).thenReturn(
                new EdgeTransactionProxyService.FilingPull(List.of(item(A, 1L)), List.of(), List.of(), false, List.of(),
                        List.of()));
        assertThatThrownBy(() -> service.extractCtr(scope, "CTR-31", anchor))
                .isInstanceOfSatisfying(FilingException.class,
                        e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_INCOMPLETE));

        when(edge.filingExtract(any(), eq("CTR"), eq("CTR-31"), anyCollection())).thenReturn(
                new EdgeTransactionProxyService.FilingPull(List.of(item(A, 1L)), List.of(aggregate(A)), List.of(), false,
                        List.of(), List.of()));
        FilingExtract x = service.extractCtr(scope, "CTR-31", anchor);
        assertThat(x.ctr().cashTotal()).isEqualByComparingTo(new BigDecimal("16170.00"));
        assertThat(x.ctr().threshold()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(x.ctr().edgeAssessmentIds()).containsExactly(A.toString(), B.toString());
        assertThat(x.ctr().configVersion()).isEqualTo(14L);
    }

    /** Verifies: a PSP without an Edge files from its legacy rows (audited read), CTR from the row's evaluation. */
    @Test
    void legacyRowsAreUsedForPspsWithoutAnEdge() {
        when(sources.sourceFor(45L)).thenReturn(TransactionSource.CLOUD_LEGACY);
        TransactionEntity t = new TransactionEntity();
        t.setTxnId(9L);
        t.setAmountCents(150_000L);
        t.setCurrency("KES");
        t.setTxnTs(LocalDateTime.of(2026, 10, 16, 9, 30));
        t.setMerchantId("m-1");
        t.setCtrUsdEquivalent(new BigDecimal("16000.0000"));
        t.setCtrThresholdUsd(new BigDecimal("15000.0000"));
        TransactionReference ref = TransactionReference.ofLegacyTransaction(9L);
        when(legacy.filingExtract(eq(scope), eq("CTR"), eq("CTR-7"), anyCollection(), any())).thenReturn(
                new LegacyTransactionReadService.LegacyFiling(List.of(new LegacyTransactionReadService.LegacyFilingRow(ref, t)),
                        List.of()));

        FilingExtract x = service.extractCtr(scope, "CTR-7", ref);

        assertThat(x.source()).isEqualTo("CLOUD_LEGACY");
        assertThat(x.transactions().get(0).legacyTxnId()).isEqualTo(9L);
        assertThat(x.transactions().get(0).amountMinor()).isEqualTo(150_000L);
        assertThat(x.ctr().basis()).isEqualTo("SINGLE_TRANSACTION");
        assertThat(x.ctr().cashTotal()).isEqualByComparingTo("16000");
        assertThat(x.auditIds()).hasSize(1);

        when(legacy.filingExtract(eq(scope), eq("SAR"), eq("SAR-2"), anyCollection(), any())).thenReturn(
                new LegacyTransactionReadService.LegacyFiling(List.of(), List.of("9")));
        assertThatThrownBy(() -> service.extractSar(scope, "SAR", "SAR-2", List.of(ref)))
                .isInstanceOfSatisfying(FilingException.class,
                        e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_INCOMPLETE));
    }

    /** Verifies: a SAR without transactions is filed without any pull. */
    @Test
    void sarWithoutTransactionsNeedsNoPull() {
        FilingExtract x = service.extractSar(scope, "SAR", "SAR-1", List.of());
        assertThat(x.source()).isEqualTo("NONE");
        assertThat(x.transactions()).isEmpty();
        verify(edge, never()).filingExtract(any(), any(), any(), anyCollection());
    }
}
