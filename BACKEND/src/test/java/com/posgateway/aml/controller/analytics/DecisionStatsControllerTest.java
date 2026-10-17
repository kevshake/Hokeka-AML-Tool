package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.service.analytics.DecisionStatsService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** PSP scoping and response shape of {@link DecisionStatsController} (R11). */
class DecisionStatsControllerTest {

    private DecisionStatsService stats;
    private PspIsolationService isolation;
    private DecisionStatsController controller;

    /** Mocks with a fixed "today". */
    @BeforeEach
    void setUp() {
        stats = mock(DecisionStatsService.class);
        isolation = mock(PspIsolationService.class);
        controller = new DecisionStatsController(stats, isolation);
        Instant today = Instant.parse("2026-10-17T00:00:00Z");
        when(stats.startOfToday()).thenReturn(today);
        when(stats.now()).thenReturn(Instant.parse("2026-10-17T12:00:00Z"));
        when(stats.startOf(any())).thenReturn(today.minusSeconds(6 * 86_400));
        DecisionStatsService.Counts counts = new DecisionStatsService.Counts(10, 6, 2, 1, 1, 0, 1, 1, 300);
        when(stats.between(any(), any(), any())).thenReturn(counts);
        when(stats.daily(any(), anyInt())).thenReturn(List.of(
                new DecisionStatsService.Day(LocalDate.parse("2026-10-17"), "Oct 17", counts)));
        when(stats.scoreBands(any(), any(), any())).thenReturn(Map.of("low", 1L, "medium", 0L, "high", 0L, "critical", 0L));
        when(stats.topMerchants(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(stats.recent(any(), anyInt())).thenReturn(List.of(Map.of("edgeAssessmentId", "x")));
    }

    @Test
    void pspUserIsScopedToOwnPsp() {
        when(isolation.isPlatformAdministrator()).thenReturn(false);
        when(isolation.getCurrentUserPspId()).thenReturn(42L);
        ResponseEntity<Map<String, Object>> r = controller.summary(7, null, 5);
        assertEquals(200, r.getStatusCode().value());
        assertEquals(42L, r.getBody().get("pspId"));
        verify(stats).daily(eq(42L), eq(7));
        @SuppressWarnings("unchecked")
        Map<String, Object> today = (Map<String, Object>) r.getBody().get("today");
        assertEquals(4L, today.get("flagged"), "alert + hold + block");
    }

    @Test
    void pspUserNamingAnotherPspIsForbiddenWithoutReadingAnything() {
        when(isolation.isPlatformAdministrator()).thenReturn(false);
        when(isolation.getCurrentUserPspId()).thenReturn(42L);
        assertEquals(403, controller.summary(7, 7L, 5).getStatusCode().value());
        assertEquals(403, controller.recent(10, 7L).getStatusCode().value());
        verify(stats, never()).daily(any(), anyInt());
        verify(stats, never()).recent(any(), anyInt());
    }

    @Test
    void platformStaffSeeEveryPspOrTheNamedOne() {
        when(isolation.isPlatformAdministrator()).thenReturn(true);
        ResponseEntity<Map<String, Object>> all = controller.recent(10, null);
        assertNull(all.getBody().get("pspId"));
        verify(stats).recent(isNull(), eq(10));
        controller.summary(30, 7L, 5);
        verify(stats).daily(eq(7L), eq(30));
    }

    @Test
    void percentDeltaHasNoBaseWithoutYesterday() {
        assertEquals(0.0, DecisionStatsController.percentDelta(5, 0));
        assertEquals(50.0, DecisionStatsController.percentDelta(15, 10));
        verifyNoInteractions(isolation);
    }
}
