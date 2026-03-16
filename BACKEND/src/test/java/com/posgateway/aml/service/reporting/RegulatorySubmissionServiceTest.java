package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegulatorySubmissionServiceTest {

    @Mock
    private RegulatorySubmissionRepository submissionRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PspIsolationService pspIsolationService;

    @InjectMocks
    private RegulatorySubmissionService submissionService;

    private Report testReport;
    private RegulatorySubmission testSubmission;
    private User testUser;

    @BeforeEach
    void setUp() {
        testReport = new Report();
        testReport.setId(1L);
        testReport.setReportCode("CTR_MONTHLY");
        testReport.setReportName("Monthly CTR Report");
        testReport.setReportCategory(ReportCategory.REGULATORY_SUBMISSION);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("compliance1");
        testUser.setFirstName("Compliance");
        testUser.setLastName("Officer");

        testSubmission = new RegulatorySubmission();
        testSubmission.setId(1L);
        testSubmission.setSubmissionReference("FIN-20240301-ABC123");
        testSubmission.setReport(testReport);
        testSubmission.setRegulatorCode("FINCEN");
        testSubmission.setSubmissionType("CTR");
        testSubmission.setJurisdiction("US");
        testSubmission.setStatus(SubmissionStatus.DRAFT);
        testSubmission.setPspId(1L);
        testSubmission.setFilingPeriodStart(LocalDate.of(2024, 2, 1));
        testSubmission.setFilingPeriodEnd(LocalDate.of(2024, 2, 29));
        testSubmission.setFilingDeadline(LocalDate.of(2024, 3, 15));
        testSubmission.setPreparedBy(testUser);
        testSubmission.setPreparedAt(LocalDateTime.now());
    }

    @Test
    void prepareSubmission_shouldCreateDraftSubmission() {
        // Given
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class))).thenReturn(testSubmission);

        // When
        RegulatorySubmissionDTO result = submissionService.prepareSubmission(1L, "FINCEN", 1L, 1L);

        // Then
        assertNotNull(result);
        assertEquals(SubmissionStatus.DRAFT, result.getStatus());
        assertEquals("FINCEN", result.getRegulatorCode());
        assertNotNull(result.getSubmissionReference());
    }

    @Test
    void prepareSubmission_shouldThrowExceptionForInvalidRegulator() {
        // Given
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            submissionService.prepareSubmission(1L, "INVALID_REGULATOR", 1L, 1L);
        });
    }

    @Test
    void submitToFinCEN_shouldFileApprovedSubmission() {
        // Given
        testSubmission.setStatus(SubmissionStatus.APPROVED);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class))).thenReturn(testSubmission);
        when(submissionRepository.findByFilters(any(), eq(SubmissionStatus.DRAFT), eq("FINCEN"), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        // When
        RegulatorySubmissionDTO result = submissionService.submitToFinCEN(1L, 1L, 1L);

        // Then
        assertNotNull(result);
        assertEquals(SubmissionStatus.FILED, result.getStatus());
        assertNotNull(result.getRegulatorReference());
        assertNotNull(result.getFiledAt());
    }

    @Test
    void submitToFinCEN_shouldThrowExceptionForDraftStatus() {
        // Given
        when(reportRepository.findById(1L)).thenReturn(Optional.of(testReport));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class))).thenReturn(testSubmission);
        when(submissionRepository.findByFilters(any(), eq(SubmissionStatus.DRAFT), eq("FINCEN"), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            submissionService.submitToFinCEN(1L, 1L, 1L);
        });
    }

    @Test
    void getSubmissionById_shouldReturnSubmission() {
        // Given
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);

        // When
        RegulatorySubmissionDTO result = submissionService.getSubmissionById(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("FIN-20240301-ABC123", result.getSubmissionReference());
    }

    @Test
    void listSubmissions_shouldReturnPageOfSubmissions() {
        // Given
        Page<RegulatorySubmission> page = new PageImpl<>(List.of(testSubmission));
        when(pspIsolationService.sanitizePspId(1L)).thenReturn(1L);
        when(submissionRepository.findByFilters(eq(1L), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        // When
        Page<RegulatorySubmissionDTO> result = submissionService.listSubmissions(
            1L, null, null, null, null, 0, 20, "createdAt", "DESC"
        );

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void updateSubmissionStatus_shouldTransitionStatus() {
        // Given
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class))).thenReturn(testSubmission);

        // When - DRAFT to PENDING_REVIEW
        RegulatorySubmissionDTO result = submissionService.updateSubmissionStatus(
            1L, SubmissionStatus.PENDING_REVIEW, 1L
        );

        // Then
        assertNotNull(result);
    }

    @Test
    void updateSubmissionStatus_shouldApproveFromPendingReview() {
        // Given
        testSubmission.setStatus(SubmissionStatus.PENDING_REVIEW);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class))).thenReturn(testSubmission);

        // When
        RegulatorySubmissionDTO result = submissionService.updateSubmissionStatus(
            1L, SubmissionStatus.APPROVED, 1L
        );

        // Then
        assertNotNull(result);
        verify(submissionRepository).save(argThat(s -> 
            s.getApprovedBy() != null && s.getApprovedAt() != null
        ));
    }

    @Test
    void amendSubmission_shouldCreateAmendedSubmission() {
        // Given
        testSubmission.setStatus(SubmissionStatus.FILED);
        RegulatorySubmission amendedSubmission = new RegulatorySubmission();
        amendedSubmission.setId(2L);
        amendedSubmission.setSubmissionReference("FIN-20240301-XYZ789");
        amendedSubmission.setStatus(SubmissionStatus.DRAFT);
        
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(submissionRepository.save(any(RegulatorySubmission.class)))
            .thenReturn(testSubmission)
            .thenReturn(amendedSubmission);

        // When
        RegulatorySubmissionDTO result = submissionService.amendSubmission(1L, "Correcting errors", 1L);

        // Then
        assertNotNull(result);
        assertEquals(SubmissionStatus.DRAFT, result.getStatus());
    }

    @Test
    void amendSubmission_shouldThrowExceptionForUnfiledSubmission() {
        // Given
        testSubmission.setStatus(SubmissionStatus.DRAFT);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            submissionService.amendSubmission(1L, "Correction", 1L);
        });
    }

    @Test
    void deleteSubmission_shouldRemoveDraftSubmission() {
        // Given
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);
        doNothing().when(submissionRepository).delete(any(RegulatorySubmission.class));

        // When
        submissionService.deleteSubmission(1L);

        // Then
        verify(submissionRepository).delete(testSubmission);
    }

    @Test
    void deleteSubmission_shouldThrowExceptionForFiledSubmission() {
        // Given
        testSubmission.setStatus(SubmissionStatus.FILED);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            submissionService.deleteSubmission(1L);
        });
    }

    @Test
    void trackSubmissionStatus_shouldReturnSubmission() {
        // Given
        when(submissionRepository.findBySubmissionReference("FIN-20240301-ABC123"))
            .thenReturn(Optional.of(testSubmission));
        doNothing().when(pspIsolationService).validatePspAccess(1L);

        // When
        RegulatorySubmissionDTO result = submissionService.trackSubmissionStatus("FIN-20240301-ABC123");

        // Then
        assertNotNull(result);
        assertEquals("FIN-20240301-ABC123", result.getSubmissionReference());
    }

    @Test
    void isLateFiling_shouldReturnCorrectValue() {
        // Given - submission filed after deadline
        testSubmission.setFiledAt(LocalDateTime.of(2024, 3, 20, 10, 0));
        
        // When/Then
        assertTrue(testSubmission.isLateFiling());
    }

    @Test
    void canEdit_shouldReturnTrueForDraft() {
        // Given
        testSubmission.setStatus(SubmissionStatus.DRAFT);
        
        // When/Then
        assertTrue(testSubmission.canEdit());
    }

    @Test
    void canFile_shouldReturnTrueForApproved() {
        // Given
        testSubmission.setStatus(SubmissionStatus.APPROVED);
        
        // When/Then
        assertTrue(testSubmission.canFile());
    }
}
