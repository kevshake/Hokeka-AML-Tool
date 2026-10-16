package com.posgateway.aml.repository.edge;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Regression (sim run 2026-10-16): {@code GET /edge/nodes/analytics?pspId=} answered 500
 * (ArrayIndexOutOfBounds) because the single-row aggregate was declared as {@code Object[]} and
 * Spring Data returned it wrapped in a one-element array.
 */
class EdgeMetricsRecordRepositorySummaryTest {

    @Test
    void summarizeForPspReturnsTheSevenColumnRowItself() {
        EdgeMetricsRecordRepository repo = Mockito.mock(EdgeMetricsRecordRepository.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        Object[] row = {1L, 10L, 7L, 1L, 1L, 1L, 120.0};
        Mockito.doReturn(Collections.singletonList(row)).when(repo)
                .summarizeRowsForPsp(anyLong(), any(Instant.class), any(Instant.class));

        Object[] s = repo.summarizeForPsp(5L, Instant.EPOCH, Instant.now());

        assertThat(s).hasSize(7);
        assertThat(s[1]).isEqualTo(10L);
    }

    @Test
    void summarizeForPspIsNullWhenNoRow() {
        EdgeMetricsRecordRepository repo = Mockito.mock(EdgeMetricsRecordRepository.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        Mockito.doReturn(List.of()).when(repo)
                .summarizeRowsForPsp(anyLong(), any(Instant.class), any(Instant.class));

        assertThat(repo.summarizeForPsp(5L, Instant.EPOCH, Instant.now())).isNull();
    }
}
