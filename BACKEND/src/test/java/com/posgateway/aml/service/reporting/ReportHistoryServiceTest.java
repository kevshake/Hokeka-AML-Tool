package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportExecutionDTO;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportHistoryServiceTest {

    @Mock
    private ReportExecutionRepository reportExecutionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PspIsolationService pspIsolationService;

    @InjectMocks
    private ReportHistoryService reportHistoryService;

    private ReportExecution testExecution;
    private Report testReport;
    private User testUser;

    @BeforeEach
    void setUp() {
        testReport = new Report();
        testReport.setId(1L);
        testReport.setReportCode("TEST_REPORT");
        testReport.setReportName("Test Report");

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        testExecution = new ReportExecution();
        testExecution.setId(1L);
        testExecution.setExecutionId("EXEC_12345");
        testExecution.setReport(testReport);
        testExecution.setPspId(1L);
        testExecution.setTriggeredBy(1L);
        testExecution.setStatus(ExecutionStatus.COMPLETED);
        testExecution.setTotalRecords(100L);
        testExecution.setFilePath("/tmp/test_report.pdf");
        testExecution.setProgressPercent(100);
        testExecution.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getReportHistory_shouldReturnPageOfExecutions() {
        // Given
        Page<ReportExecution> page = new PageImpl<>(List.of(testExecution));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(reportExecutionRepository.findByPspId(eq(1L), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        Page<ReportExecutionDTO> result = reportHistoryService.getReportHistory(1L, 0, 20, "createdAt", "DESC");

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("EXEC_12345", result.getContent().get(0).getExecutionId());
    }

    @Test
    void getReportById_shouldReturnExecution() {
        // Given
        when(reportExecutionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        ReportExecutionDTO result = reportHistoryService.getReportById(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(ExecutionStatus.COMPLETED, result.getStatus());
    }

    @Test
    void getReportById_shouldThrowExceptionForNotFound() {
        // Given
        when(reportExecutionRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            reportHistoryService.getReportById(999L);
        });
    }

    @Test
    void deleteReport_shouldRemoveExecution() {
        // Given
        when(reportExecutionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        doNothing().when(reportExecutionRepository).delete(any(ReportExecution.class));

        // When
        boolean result = reportHistoryService.deleteReport(1L);

        // Then
        assertTrue(result);
        verify(reportExecutionRepository).delete(testExecution);
    }

    @Test
    void retryReport_shouldResetFailedExecution() {
        // Given
        testExecution.setStatus(ExecutionStatus.FAILED);
        testExecution.setErrorMessage("Connection timeout");
        testExecution.setRetryCount(0);
        
        when(reportExecutionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(reportExecutionRepository.save(any(ReportExecution.class))).thenReturn(testExecution);

        // When
        ReportExecutionDTO result = reportHistoryService.retryReport(1L);

        // Then
        assertNotNull(result);
        verify(reportExecutionRepository).save(argThat(exec -> 
            exec.getStatus() == ExecutionStatus.PENDING &&
            exec.getRetryCount() == 1
        ));
    }

    @Test
    void retryReport_shouldThrowExceptionForNonFailedExecution() {
        // Given
        testExecution.setStatus(ExecutionStatus.COMPLETED);
        when(reportExecutionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(1L);

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            reportHistoryService.retryReport(1L);
        });
    }

    @Test
    void getRecentReports_shouldReturnLimitedResults() {
        // Given
        Page<ReportExecution> page = new PageImpl<>(List.of(testExecution));
        when(reportExecutionRepository.findByTriggeredBy(eq(1L), any(Pageable.class))).thenReturn(page);
        doNothing().when(pspIsolationService).validatePspAccess(anyLong());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        List<ReportExecutionDTO> result = reportHistoryService.getRecentReports(1L, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getExecutionStatistics_shouldReturnStatistics() {
        // Given
        Page<ReportExecution> page = new PageImpl<>(List.of(testExecution));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(reportExecutionRepository.findByPspId(eq(1L), any(Pageable.class))).thenReturn(page);

        // When
        ReportHistoryService.ExecutionStatistics stats = reportHistoryService.getExecutionStatistics(
            1L, LocalDateTime.now().minusMonths(1), LocalDateTime.now()
        );

        // Then
        assertNotNull(stats);
        assertEquals(1, stats.getTotalExecutions());
        assertEquals(1, stats.getCompletedCount());
    }
}
