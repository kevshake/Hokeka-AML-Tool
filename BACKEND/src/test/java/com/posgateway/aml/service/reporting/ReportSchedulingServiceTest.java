package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportScheduleDTO;
import com.posgateway.aml.dto.reporting.ReportScheduleRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.reporting.ReportScheduleRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportSchedulingServiceTest {

    @Mock
    private ReportScheduleRepository reportScheduleRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PspIsolationService pspIsolationService;

    @InjectMocks
    private ReportSchedulingService reportSchedulingService;

    private Report testReport;
    private ReportSchedule testSchedule;
    private User testUser;
    private ReportScheduleRequest testRequest;

    @BeforeEach
    void setUp() {
        testReport = new Report();
        testReport.setId(1L);
        testReport.setReportCode("DAILY_TXN");
        testReport.setReportName("Daily Transactions Report");

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        testSchedule = new ReportSchedule();
        testSchedule.setId(1L);
        testSchedule.setReport(testReport);
        testSchedule.setPspId(1L);
        testSchedule.setScheduleName("Daily Morning Report");
        testSchedule.setFrequency(ScheduleFrequency.DAILY);
        testSchedule.setTimeOfDay(LocalTime.of(8, 0));
        testSchedule.setTimezone("UTC");
        testSchedule.setIsActive(true);
        testSchedule.setRunCount(5);
        testSchedule.setFailCount(0);
        testSchedule.setCreatedBy(testUser);

        testRequest = new ReportScheduleRequest();
        testRequest.setReportId(1L);
        testRequest.setScheduleName("Daily Morning Report");
        testRequest.setFrequency(ScheduleFrequency.DAILY);
        testRequest.setTimeOfDay(LocalTime.of(8, 0));
        testRequest.setTimezone("UTC");
        testRequest.setExportFormats(List.of("PDF", "CSV"));
    }

    @Test
    void scheduleReport_shouldCreateNewSchedule() {
        // Given
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(reportScheduleRepository.save(any(ReportSchedule.class))).thenReturn(testSchedule);

        // When
        ReportScheduleDTO result = reportSchedulingService.scheduleReport(
            1L, testRequest, null, 1L, 1L
        );

        // Then
        assertNotNull(result);
        assertEquals("Daily Morning Report", result.getScheduleName());
        assertEquals(ScheduleFrequency.DAILY, result.getFrequency());
        assertTrue(result.getIsActive());
    }

    @Test
    void scheduleReport_shouldThrowExceptionForMissingName() {
        // Given
        testRequest.setScheduleName("");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            reportSchedulingService.scheduleReport(1L, testRequest, null, 1L, 1L);
        });
    }

    @Test
    void unscheduleReport_shouldDeactivateSchedule() {
        // Given
        when(reportScheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(reportScheduleRepository.save(any(ReportSchedule.class))).thenReturn(testSchedule);

        // When
        boolean result = reportSchedulingService.unscheduleReport(1L);

        // Then
        assertTrue(result);
        verify(reportScheduleRepository).save(argThat(s -> Boolean.FALSE.equals(s.getIsActive())));
    }

    @Test
    void getScheduledReports_shouldReturnPageOfSchedules() {
        // Given
        Page<ReportSchedule> page = new PageImpl<>(List.of(testSchedule));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(reportScheduleRepository.findByPspId(eq(1L), any(Pageable.class))).thenReturn(page);

        // When
        Page<ReportScheduleDTO> result = reportSchedulingService.getScheduledReports(1L, 0, 20, "createdAt", "DESC");

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Daily Morning Report", result.getContent().get(0).getScheduleName());
    }

    @Test
    void updateSchedule_shouldUpdateFields() {
        // Given
        ReportScheduleRequest updateRequest = new ReportScheduleRequest();
        updateRequest.setScheduleName("Updated Name");
        updateRequest.setFrequency(ScheduleFrequency.WEEKLY);
        updateRequest.setTimeOfDay(LocalTime.of(9, 0));

        when(reportScheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(reportScheduleRepository.save(any(ReportSchedule.class))).thenReturn(testSchedule);

        // When
        ReportScheduleDTO result = reportSchedulingService.updateSchedule(1L, updateRequest);

        // Then
        assertNotNull(result);
        verify(reportScheduleRepository).save(any(ReportSchedule.class));
    }

    @Test
    void calculateNextRunTime_shouldReturnCorrectTimeForDaily() {
        // Given
        testSchedule.setFrequency(ScheduleFrequency.DAILY);
        testSchedule.setTimeOfDay(LocalTime.of(8, 0));
        testSchedule.setTimezone("UTC");
        testSchedule.setIsActive(true);

        // When
        LocalDateTime nextRun = reportSchedulingService.calculateNextRunTime(testSchedule);

        // Then
        assertNotNull(nextRun);
        assertEquals(LocalTime.of(8, 0), nextRun.toLocalTime());
    }

    @Test
    void activateSchedule_shouldSetActiveAndCalculateNextRun() {
        // Given
        testSchedule.setIsActive(false);
        when(reportScheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(reportScheduleRepository.save(any(ReportSchedule.class))).thenReturn(testSchedule);

        // When
        ReportScheduleDTO result = reportSchedulingService.activateSchedule(1L);

        // Then
        assertNotNull(result);
        verify(reportScheduleRepository).save(argThat(s -> Boolean.TRUE.equals(s.getIsActive())));
    }

    @Test
    void deleteSchedule_shouldRemoveSchedule() {
        // Given
        when(reportScheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        doNothing().when(reportScheduleRepository).delete(any(ReportSchedule.class));

        // When
        reportSchedulingService.deleteSchedule(1L);

        // Then
        verify(reportScheduleRepository).delete(testSchedule);
    }
}
