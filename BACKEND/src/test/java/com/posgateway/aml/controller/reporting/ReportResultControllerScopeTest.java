package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.controller.BatchController;
import com.posgateway.aml.controller.MonitoringController;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportResultRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B8-read: report result reads are scoped to the execution's PSP.
 * B11: global batch / metrics recompute jobs are not reachable by PSP roles.
 */
class ReportResultControllerScopeTest {

    private ReportResultRepository results;
    private ReportExecutionRepository executions;
    private PspIsolationService isolation;
    private ReportResultController controller;

    @BeforeEach
    void setUp() {
        results = mock(ReportResultRepository.class);
        executions = mock(ReportExecutionRepository.class);
        isolation = mock(PspIsolationService.class);
        controller = new ReportResultController(results, executions, isolation);
    }

    private ReportExecution executionOwnedBy(long pspId) {
        ReportExecution exec = new ReportExecution();
        exec.setPspId(pspId);
        return exec;
    }

    @Test
    void readOfOtherPspsExecutionIsRejectedBeforeQueryingRows() {
        when(executions.findById(5L)).thenReturn(Optional.of(executionOwnedBy(2L)));
        doThrow(new SecurityException("Cannot access data from another PSP"))
                .when(isolation).validatePspAccess(2L);

        assertThrows(SecurityException.class, () -> controller.listByExecution(5L));
        assertThrows(SecurityException.class, () -> controller.pageByExecution(5L, 0, 100));
        verify(results, never()).findByExecutionIdOrderByRowNumberAsc(anyLong());
        verify(results, never()).findByExecutionId(anyLong(), any(Pageable.class));
    }

    @Test
    void readOfOwnExecutionReturnsRows() {
        when(executions.findById(5L)).thenReturn(Optional.of(executionOwnedBy(1L)));
        when(results.findByExecutionIdOrderByRowNumberAsc(5L)).thenReturn(List.of());

        assertEquals(List.of(), controller.listByExecution(5L));
        verify(isolation).validatePspAccess(1L);
    }

    @Test
    void unknownExecutionIs404() {
        when(executions.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> controller.listByExecution(9L));
    }

    @Test
    void globalRecomputeJobsAreAdminOnly() throws Exception {
        assertAdminOnly(BatchController.class.getMethod("batchScoreYesterday"));
        assertAdminOnly(BatchController.class.getMethod("backfillFeatures", int.class));
        assertAdminOnly(MonitoringController.class.getMethod("computeMetrics"));
    }

    private static void assertAdminOnly(java.lang.reflect.Method m) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        String expr = pa == null ? "" : pa.value();
        assertFalse(expr.isEmpty(), m.getName() + " must declare a method-level @PreAuthorize");
        assertFalse(expr.contains("PSP_"), m.getName() + " must not be reachable by PSP roles: " + expr);
    }
}
