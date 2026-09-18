package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportExecutionDTO;
import com.posgateway.aml.entity.reporting.DateRangeType;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.service.reporting.ReportSchedulingService.ScheduledReportClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledReportRunnerTest {

    @Mock ReportSchedulingService schedulingService;
    @Mock ReportGenerationService generationService;
    @Mock ReportDeliveryService deliveryService;

    @Test
    void dispatchesEveryConfiguredFormatWithConcreteDateWindow() {
        ScheduledReportClaim claim = new ScheduledReportClaim(4L, "MKT_001", 8L, 12L,
                Map.of("segment", "retail"), Map.of("status", "OPEN"),
                DateRangeType.PREVIOUS_DAY, List.of("CSV", "XLSX"), "Africa/Nairobi",
                LocalDateTime.now(), List.of(), null, null);
        when(schedulingService.claimDueSchedules(any())).thenReturn(List.of(claim));
        when(generationService.generateExecutionId()).thenReturn("EXEC-A", "EXEC-B");
        ReportExecutionDTO complete = new ReportExecutionDTO();
        complete.setStatus(ExecutionStatus.COMPLETED);
        complete.setId(30L);
        complete.setFilePath("report.csv");
        when(generationService.queueScheduledReport(anyString(), eq("MKT_001"), anyMap(), eq(12L), eq(8L)))
                .thenReturn(complete);
        when(schedulingService.recordScheduledDispatch(eq(4L), eq(30L), eq(8L), any(), anyString(), anyList()))
                .thenReturn(50L, 51L);
        when(generationService.generateScheduledReport(anyString(), eq("MKT_001"), anyMap(), eq(12L), eq(8L)))
                .thenReturn(CompletableFuture.completedFuture(complete));

        new ScheduledReportRunner(schedulingService, generationService, deliveryService).dispatchDueReports();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(generationService, times(2)).queueScheduledReport(anyString(), eq("MKT_001"),
                parameters.capture(), eq(12L), eq(8L));
        assertThat(parameters.getAllValues()).allSatisfy(values -> {
            assertThat(values.get("dateFrom")).isInstanceOf(LocalDateTime.class);
            assertThat(values.get("dateTo")).isInstanceOf(LocalDateTime.class);
            assertThat(values.get("filters")).isEqualTo(Map.of("status", "OPEN"));
        });
        assertThat(parameters.getAllValues()).extracting(values -> values.get("outputFormat"))
                .containsExactly("CSV", "XLSX");
        verify(schedulingService, never()).recordScheduledExecutionFailure(anyLong());
        verify(schedulingService).completeScheduledDispatch(50L, "COMPLETED", null);
        verify(schedulingService).completeScheduledDispatch(51L, "COMPLETED", null);
    }
}
