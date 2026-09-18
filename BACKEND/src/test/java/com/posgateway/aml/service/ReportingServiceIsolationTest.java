package com.posgateway.aml.service;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportingServiceIsolationTest {

    @Test
    void auditCountUsesAuthenticatedPspScope() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        SuspiciousActivityReportRepository sars = mock(SuspiciousActivityReportRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        MerchantRepository merchants = mock(MerchantRepository.class);
        PspIsolationService isolation = mock(PspIsolationService.class);
        User user = new User();

        when(isolation.getCurrentUser()).thenReturn(user);
        when(isolation.isPlatformAdministrator(user)).thenReturn(false);
        when(isolation.getCurrentUserPspId()).thenReturn(42L);
        when(auditLogs.countByPspIdAndTimestampAfter(eq(42L), any(LocalDateTime.class))).thenReturn(7L);

        ReportingService service = new ReportingService(cases, sars, auditLogs, merchants, isolation);

        assertThat(service.auditCountLastHours(24)).isEqualTo(7L);
        verify(auditLogs).countByPspIdAndTimestampAfter(eq(42L), any(LocalDateTime.class));
    }
}
