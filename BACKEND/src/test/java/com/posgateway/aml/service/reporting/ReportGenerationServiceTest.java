package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportExecutionDTO;
import com.posgateway.aml.dto.reporting.ReportGenerateRequest;
import com.posgateway.aml.dto.reporting.ReportPreviewDTO;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.ReportDefinitionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportDefinitionRepository reportDefinitionRepository;

    @Mock
    private ReportExecutionRepository reportExecutionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PspIsolationService pspIsolationService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private ReportGenerationService reportGenerationService;

    private Report testReport;
    private ReportDefinition testDefinition;
    private ReportExecution testExecution;
    private User testUser;

    @BeforeEach
    void setUp() {
        testReport = new Report();
        testReport.setId(1L);
        testReport.setReportCode("TEST_REPORT");
        testReport.setReportName("Test Report");
        testReport.setReportCategory(ReportCategory.AML_FRAUD);
        testReport.setEnabled(true);

        testDefinition = new ReportDefinition();
        testDefinition.setId(1L);
        testDefinition.setReport(testReport);
        testDefinition.setVersion(1);
        testDefinition.setSqlQuery("SELECT * FROM test_table WHERE psp_id = :pspId");
        testDefinition.setIsActive(true);
        testDefinition.setColumns(List.of(Map.of("name", "id", "type", "INTEGER")));

        testExecution = new ReportExecution();
        testExecution.setId(1L);
        testExecution.setExecutionId("EXEC_12345");
        testExecution.setReport(testReport);
        testExecution.setPspId(1L);
        testExecution.setStatus(ExecutionStatus.PENDING);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
    }

    @Test
    void generateReport_shouldCreatePendingExecution() {
        // Given
        String reportType = "TEST_REPORT";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dateFrom", LocalDateTime.now().minusDays(30).toString());
        parameters.put("dateTo", LocalDateTime.now().toString());

        when(reportRepository.findByReportCode(reportType)).thenReturn(Optional.of(testReport));
        when(reportDefinitionRepository.findByReportIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testDefinition));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(reportExecutionRepository.save(any(ReportExecution.class))).thenAnswer(i -> {
            ReportExecution exec = i.getArgument(0);
            exec.setId(1L);
            return exec;
        });
        when(reportExecutionRepository.findByExecutionId(anyString())).thenReturn(Optional.of(testExecution));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(reportExportService.exportToPDF(any(), anyString(), anyString())).thenReturn("/tmp/test.pdf");

        // When
        CompletableFuture<ReportExecutionDTO> future = reportGenerationService.generateReport(
            reportType, parameters, 1L, 1L
        );

        // Then
        assertNotNull(future);
        verify(reportExecutionRepository, atLeastOnce()).save(any(ReportExecution.class));
    }

    @Test
    void previewReport_shouldReturnPreviewData() {
        // Given
        String reportType = "TEST_REPORT";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pspId", 1L);

        when(reportRepository.findByReportCode(reportType)).thenReturn(Optional.of(testReport));
        when(reportDefinitionRepository.findByReportIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testDefinition));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);

        // When
        ReportPreviewDTO preview = reportGenerationService.previewReport(reportType, parameters, 1L);

        // Then
        assertNotNull(preview);
        assertEquals("TEST_REPORT", preview.getReportType());
        assertEquals("Test Report", preview.getReportName());
    }

    @Test
    void previewReport_shouldThrowExceptionForInvalidReportType() {
        // Given
        String reportType = "INVALID_REPORT";
        when(reportRepository.findByReportCode(reportType)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            reportGenerationService.previewReport(reportType, new HashMap<>(), 1L);
        });
    }

    @Test
    void getReportExecutionStatus_shouldReturnExecutionStatus() {
        // Given
        String executionId = "EXEC_12345";
        when(reportExecutionRepository.findByExecutionId(executionId)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(anyLong());
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));

        // When
        ReportExecutionDTO result = reportGenerationService.getReportExecutionStatus(executionId);

        // Then
        assertNotNull(result);
        assertEquals(executionId, result.getExecutionId());
        assertEquals(ExecutionStatus.PENDING, result.getStatus());
    }

    @Test
    void cancelReportExecution_shouldCancelPendingExecution() {
        // Given
        String executionId = "EXEC_12345";
        testExecution.setStatus(ExecutionStatus.RUNNING);
        when(reportExecutionRepository.findByExecutionId(executionId)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(anyLong());
        when(reportExecutionRepository.save(any(ReportExecution.class))).thenReturn(testExecution);

        // When
        boolean result = reportGenerationService.cancelReportExecution(executionId);

        // Then
        assertTrue(result);
        verify(reportExecutionRepository).save(argThat(exec -> 
            exec.getStatus() == ExecutionStatus.CANCELLED
        ));
    }

    @Test
    void cancelReportExecution_shouldReturnFalseForCompletedExecution() {
        // Given
        String executionId = "EXEC_12345";
        testExecution.setStatus(ExecutionStatus.COMPLETED);
        when(reportExecutionRepository.findByExecutionId(executionId)).thenReturn(Optional.of(testExecution));
        doNothing().when(pspIsolationService).validatePspAccess(anyLong());

        // When
        boolean result = reportGenerationService.cancelReportExecution(executionId);

        // Then
        assertFalse(result);
    }
}
