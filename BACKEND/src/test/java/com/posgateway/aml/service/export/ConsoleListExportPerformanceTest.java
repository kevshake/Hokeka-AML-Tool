package com.posgateway.aml.service.export;

import com.posgateway.aml.dto.export.ConsoleListViewType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight performance guardrails for export query planning (no DB required).
 * Full integration benchmarks belong in CI with seeded Postgres.
 */
class ConsoleListExportPerformanceTest {

    /** Documents the async threshold constant operators rely on for UX messaging. */
    @Test
    void asyncThresholdIsAtLeastFiveThousandRows() {
        assertTrue(ConsoleListExportService.ASYNC_ROW_THRESHOLD >= 5_000L);
    }

    /** Ensures export paging uses a bounded page size to avoid loading unbounded pages. */
    @Test
    void exportPageSizeIsReasonablyBounded() {
        assertTrue(ConsoleListExportService.EXPORT_PAGE_SIZE >= 100);
        assertTrue(ConsoleListExportService.EXPORT_PAGE_SIZE <= 1_000);
    }

    /** Smoke test that view types cover primary Console list surfaces. */
    @Test
    void primaryConsoleViewsAreRegistered() {
        assertTrue(ConsoleListViewType.valueOf("ALERTS") != null);
        assertTrue(ConsoleListViewType.valueOf("TRANSACTIONS") != null);
        Map<String, String> filters = Map.of("status", "OPEN");
        assertTrue(filters.containsKey("status"));
    }
}
