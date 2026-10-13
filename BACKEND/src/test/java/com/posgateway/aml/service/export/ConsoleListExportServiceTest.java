package com.posgateway.aml.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.export.ConsoleListExportRequestDto;
import com.posgateway.aml.dto.export.ConsoleListViewType;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.export.ConsoleListExportJobRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.reporting.ReportExportService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for export orchestration thresholds and audit logging hooks. */
class ConsoleListExportServiceTest {

    private ConsoleListExportQueryFacade queryFacade;
    private ConsoleListExportJobRepository jobRepository;
    private ConsoleListExportAsyncWorker asyncWorker;
    private ReportExportService reportExportService;
    private AuditLogService auditLogService;
    private PspIsolationService pspIsolationService;
    private ConsoleListExportService service;

    @BeforeEach
    void setUp() {
        queryFacade = mock(ConsoleListExportQueryFacade.class);
        jobRepository = mock(ConsoleListExportJobRepository.class);
        asyncWorker = mock(ConsoleListExportAsyncWorker.class);
        reportExportService = mock(ReportExportService.class);
        auditLogService = mock(AuditLogService.class);
        pspIsolationService = mock(PspIsolationService.class);
        service = new ConsoleListExportService(queryFacade, jobRepository, asyncWorker, reportExportService,
                auditLogService, pspIsolationService, new ObjectMapper());
    }

    /** Large CSV exports should queue async jobs instead of streaming synchronously. */
    @Test
    void queuesAsyncJobWhenRowCountExceedsThreshold() {
        User user = userWith(Permission.EXPORT_DATA, Permission.VIEW_CASES);
        when(queryFacade.countRows(any(), any())).thenReturn(10_000L);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsoleListExportRequestDto request = new ConsoleListExportRequestDto(
                ConsoleListViewType.ALERTS, "CSV", Map.of("status", "OPEN"), false, null, null);

        ResponseEntity<?> response = service.export(user, request, "127.0.0.1");
        assertEquals(202, response.getStatusCode().value());
        verify(auditLogService).logAction(any(), anyString(), anyString(), anyString(), any(), any(), anyString(), anyString());
    }

    /** Small CSV exports should return 200 with a streaming body. */
    @Test
    void streamsSmallCsvSynchronously() {
        User user = userWith(Permission.EXPORT_DATA);
        when(queryFacade.countRows(any(), any())).thenReturn(10L);
        when(queryFacade.fetchPage(any(), any(), anyInt(), anyInt(), eq(false))).thenReturn(java.util.List.of(
                Map.of("alertId", 1L, "status", "OPEN")));

        ConsoleListExportRequestDto request = new ConsoleListExportRequestDto(
                ConsoleListViewType.ALERTS, "CSV", Map.of(), false, null, null);

        ResponseEntity<?> response = service.export(user, request, "127.0.0.1");
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(200, response.getStatusCode().value());
    }

    /** Builds a user stub whose role includes the requested permissions for RBAC checks. */
    private static User userWith(Permission... permissions) {
        Role role = new Role();
        role.setPermissions(EnumSet.copyOf(Set.of(permissions)));
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setRole(role);
        return user;
    }
}
