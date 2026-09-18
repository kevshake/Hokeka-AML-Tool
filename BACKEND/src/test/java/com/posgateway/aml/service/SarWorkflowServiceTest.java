package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import com.posgateway.aml.service.compliance.RegulatoryDeadlineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SarWorkflowServiceTest {

    private final SuspiciousActivityReportRepository repository = mock(SuspiciousActivityReportRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RegulatoryDeadlineService deadlineService = mock(RegulatoryDeadlineService.class);
    private final ComplianceCalendarService calendarService = mock(ComplianceCalendarService.class);
    private SarWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new SarWorkflowService(repository, permissionService, auditLogService,
                deadlineService, calendarService);
        when(repository.save(any(SuspiciousActivityReport.class))).thenAnswer(invocation -> {
            SuspiciousActivityReport sar = invocation.getArgument(0);
            if (sar.getId() == null) sar.setId(77L);
            return sar;
        });
    }

    @Test
    void createsDeadlineFromSuspicionAndRegistersCalendarObligation() {
        User creator = user(10L, 45L, "analyst");
        when(permissionService.hasPermission(creator.getRole(), Permission.CREATE_SAR)).thenReturn(true);
        LocalDateTime suspicion = LocalDateTime.of(2026, 7, 15, 9, 0);
        LocalDateTime deadline = suspicion.plusDays(2);
        when(deadlineService.calculate(RegulatoryDeadlineService.SAR_STR, "KE", 45L, suspicion))
                .thenReturn(new RegulatoryDeadlineService.DeadlineCalculation(
                        2L, "KE-POCAMLA-STR-2D", deadline, 24, "POCAMLA section 44"));
        SuspiciousActivityReport draft = sar(creator, 45L, SarStatus.DRAFT);
        draft.setJurisdiction("KE");
        draft.setSuspicionAroseAt(suspicion);

        SuspiciousActivityReport saved = service.createSarDraft(draft, creator);

        assertThat(saved.getFilingDeadline()).isEqualTo(deadline);
        assertThat(saved.getDeadlinePolicyCode()).isEqualTo("KE-POCAMLA-STR-2D");
        assertThat(saved.getDeadlineWarningAt()).isEqualTo(deadline.minusHours(24));
        verify(calendarService).upsertSourceDeadline("SAR_FILING", deadline,
                "SAR-TEST filing deadline (KE-POCAMLA-STR-2D)", "KE", 45L, "SAR", 77L);
    }

    @Test
    void onlyCreatorCanSubmitAndCreatorCannotApprove() {
        User creator = user(10L, 45L, "analyst");
        User colleague = user(11L, 45L, "colleague");
        SuspiciousActivityReport sar = sar(creator, 45L, SarStatus.DRAFT);
        when(repository.findById(77L)).thenReturn(Optional.of(sar));
        when(permissionService.hasPermission(any(Role.class), eq(Permission.CREATE_SAR))).thenReturn(true);

        assertThatThrownBy(() -> service.submitForReview(77L, colleague))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("creator");

        service.submitForReview(77L, creator);
        when(permissionService.hasPermission(creator.getRole(), Permission.APPROVE_SAR)).thenReturn(true);
        assertThatThrownBy(() -> service.approveSar(77L, creator))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("own report");
    }

    @Test
    void reviewerMustBelongToSamePspAndRejectionReasonIsPersisted() {
        User creator = user(10L, 45L, "analyst");
        User otherPspReviewer = user(20L, 99L, "external-reviewer");
        User reviewer = user(11L, 45L, "mlro");
        SuspiciousActivityReport sar = sar(creator, 45L, SarStatus.PENDING_REVIEW);
        when(repository.findById(77L)).thenReturn(Optional.of(sar));
        when(permissionService.hasPermission(any(Role.class), eq(Permission.REJECT_SAR))).thenReturn(true);

        assertThatThrownBy(() -> service.rejectSar(77L, otherPspReviewer, "Insufficient evidence"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("different PSP");
        assertThatThrownBy(() -> service.rejectSar(77L, reviewer, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        SuspiciousActivityReport rejected = service.rejectSar(77L, reviewer, "Add counterparty evidence");

        assertThat(rejected.getStatus()).isEqualTo(SarStatus.REJECTED);
        assertThat(rejected.getReviewedBy()).isEqualTo(reviewer);
        assertThat(rejected.getReviewNotes()).isEqualTo("Add counterparty evidence");
        assertThat(rejected.getReviewedAt()).isNotNull();
    }

    @Test
    void filingRecordsDeadlineBreachAndCompletesCalendarObligation() {
        User filer = user(12L, 45L, "mlro");
        SuspiciousActivityReport sar = sar(user(10L, 45L, "analyst"), 45L, SarStatus.APPROVED);
        sar.setFilingDeadline(LocalDateTime.now().minusMinutes(1));
        when(repository.findById(77L)).thenReturn(Optional.of(sar));
        when(permissionService.hasPermission(filer.getRole(), Permission.FILE_SAR)).thenReturn(true);

        SuspiciousActivityReport filed = service.markAsFiled(77L, "FRC-2026-001", filer);

        assertThat(filed.getStatus()).isEqualTo(SarStatus.FILED);
        assertThat(filed.isDeadlineBreached()).isTrue();
        assertThat(filed.getFilingReferenceNumber()).isEqualTo("FRC-2026-001");
        verify(calendarService).completeSourceDeadline("SAR", 77L);
    }

    private SuspiciousActivityReport sar(User creator, Long pspId, SarStatus status) {
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setId(77L);
        sar.setSarReference("SAR-TEST");
        sar.setCreatedBy(creator);
        sar.setPspId(pspId);
        sar.setStatus(status);
        return sar;
    }

    private User user(Long id, Long pspId, String username) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        Role role = mock(Role.class);
        when(role.getName()).thenReturn("COMPLIANCE_OFFICER");
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setPsp(psp);
        return user;
    }
}
