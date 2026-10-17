package com.hokeka.edge.typology;

import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.store.CardHistory;
import com.hokeka.edge.store.CardHistoryFeatures;
import com.hokeka.edge.store.TxnContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process cost the R10a typology path adds per evaluation: deriving every history feature from
 * a full history (48 hourly + 31 daily buckets, 100 remembered evaluations, device and IP
 * profiles) and evaluating the whole compiled seed catalog on the Java interpreter (the slower of
 * the two cores). Store round trips are not part of this number (see
 * docs/edge-transaction-evaluation.md §2.2 for their count).
 */
class TypologyLatencyTest {

    /** p99 of derive + full-catalog evaluation stays well under a millisecond. */
    @Test
    void derivationAndCatalogEvaluationAreSubMillisecond() throws Exception {
        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        interpreter.loadBundle(Files.readAllBytes(Path.of("..", "edge-engine", "test-vectors",
                "typology_catalog_bundle.json")));
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        List<CardHistory.Hour> hours = new ArrayList<>();
        for (int i = 0; i < CardHistory.HOURS; i++) {
            hours.add(new CardHistory.Hour(5, 50_000, 5, 2, 3, 20_000, 30_000, 0));
        }
        List<CardHistory.Day> days = new ArrayList<>();
        for (int i = 0; i < CardHistory.DAYS; i++) {
            days.add(new CardHistory.Day(40, 400_000, 40, 4.4e9, 15, 25));
        }
        List<CardHistory.Entry> recent = new ArrayList<>();
        for (int i = 0; i < CardHistory.RECENT_MAX; i++) {
            recent.add(new CardHistory.Entry(now.toEpochMilli() - i * 60_000L, 10_000L + i, "m" + (i % 7),
                    i % 3 == 0 ? "UG" : "KE", "USD", i % 4 == 0, "dev-" + (i % 2), "VISA"));
        }
        CardHistory h = new CardHistory(hours, days, now.toEpochMilli() - 86_400_000L * 90,
                now.toEpochMilli() - 60_000L, recent, recent, recent);
        TxnContext t = new TxnContext(now, "card", 12_345L, "USD", "KE", "m1", "POS", TxnContext.OUTBOUND, false,
                "dev-1", "10.0.0.1", "VISA");

        int warm = 5_000;
        int n = 20_000;
        long[] nanos = new long[n];
        for (int i = 0; i < warm + n; i++) {
            long t0 = System.nanoTime();
            Map<String, Object> f = new LinkedHashMap<>(CardHistoryFeatures.derive(h, t));
            f.put("pan_hash", "card");
            interpreter.evaluate(f);
            if (i >= warm) {
                nanos[i - warm] = System.nanoTime() - t0;
            }
        }
        Arrays.sort(nanos);
        double p50 = nanos[n / 2] / 1e6;
        double p99 = nanos[(int) (n * 0.99)] / 1e6;
        System.out.printf("typology derive + %d-rule catalog evaluation: p50 %.4f ms p99 %.4f ms%n",
                interpreterRuleCount(), p50, p99);
        assertTrue(p99 < 1.0, "p99 " + p99 + " ms");
    }

    /** Rules in the shared catalog bundle. */
    private static int interpreterRuleCount() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(Path.of("..",
                "edge-engine", "test-vectors", "typology_catalog_bundle.json"))).get("rules").size();
    }
}
