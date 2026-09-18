package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.record.RecordTrailDtos.RecordLink;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.service.record.RecordTrailService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRunTraceServiceTest {
    @Mock private ReportRepository reportRepository;
    @Mock private ReportExecutionRepository executionRepository;
    @Mock private RecordTrailService recordTrailService;
    @Mock private PspIsolationService pspIsolationService;
    @InjectMocks private ReportRunTraceService reportRunTraceService;

    @Test
    void record_persistsCalculationsAndTransactionLinksForDirectReports() {
        Report report = new Report();
        report.setReportCode("CTR_001");
        RecordLink transactionLink = new RecordLink("TRANSACTION", "99", "Transaction #99", "SOURCE_RECORD", Map.of());
        when(reportRepository.findByReportCode("CTR_001")).thenReturn(Optional.of(report));
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);
        when(recordTrailService.resolveLink("TRANSACTION", 99L)).thenReturn(Optional.of(transactionLink));
        when(executionRepository.save(any(ReportExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String executionId = reportRunTraceService.record(
                "CTR_001",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 31, 23, 59),
                List.of(Map.of("transaction_id", 99L, "amount", 1250L, "risk_score", 4.5)),
                Map.of("threshold", 1000));

        ArgumentCaptor<ReportExecution> execution = ArgumentCaptor.forClass(ReportExecution.class);
        org.mockito.Mockito.verify(executionRepository).save(execution.capture());
        ReportExecution saved = execution.getValue();
        assertNotNull(executionId);
        assertEquals(ExecutionStatus.COMPLETED, saved.getStatus());
        assertEquals(7L, saved.getPspId());
        assertEquals(1L, saved.getTotalRecords());
        assertEquals(1, saved.getRecordLinks().size());
        assertEquals("TRANSACTION", saved.getRecordLinks().get(0).get("recordType"));
        assertEquals(1, saved.getCalculationSummary().get("rowCount"));
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> totals = (Map<String, BigDecimal>) saved.getCalculationSummary().get("numericTotals");
        assertEquals(new BigDecimal("1250"), totals.get("amount"));
    }
}
