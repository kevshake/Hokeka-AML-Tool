package com.posgateway.aml.service.monitoring;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.FraudDetectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W20-9: TransactionMonitoringService.getMonitoringSARs used to call
 * sarRepository.findAll() unconditionally, leaking every PSP's suspicious activity reports to any
 * authenticated caller. A PSP-scoped caller must now only ever reach the PSP-scoped repository
 * method; a platform admin (null PSP) still gets the unscoped view.
 */
@ExtendWith(MockitoExtension.class)
class TransactionMonitoringServiceTenantScopingTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SuspiciousActivityReportRepository sarRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private FraudDetectionService fraudDetectionService;

    private TransactionMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new TransactionMonitoringService(
                transactionRepository, sarRepository, alertRepository, userRepository,
                merchantRepository, /* sanctionsScreenClient */ null, fraudDetectionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", Collections.emptyList()));
    }

    @Test
    void pspScopedCallerOnlyEverReachesThePspScopedSarQuery() {
        Psp psp = new Psp();
        psp.setPspId(9L);
        User pspUser = new User();
        pspUser.setUsername("psp_user");
        pspUser.setPsp(psp);

        authenticateAs("psp_user");
        when(userRepository.findByUsername("psp_user")).thenReturn(Optional.of(pspUser));

        SuspiciousActivityReport sar = mock(SuspiciousActivityReport.class);
        when(sar.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(sarRepository.findByPspId(9L)).thenReturn(List.of(sar));

        List<java.util.Map<String, Object>> result = service.getMonitoringSARs();

        verify(sarRepository).findByPspId(9L);
        // The whole point of the fix: the cross-tenant findAll() must never be reached.
        verify(sarRepository, never()).findAll();
    }

    @Test
    void platformAdminFallsBackToTheUnscopedSarQuery() {
        User admin = new User();
        admin.setUsername("platform_admin");
        admin.setPsp(null); // platform admin: no PSP restriction

        authenticateAs("platform_admin");
        when(userRepository.findByUsername("platform_admin")).thenReturn(Optional.of(admin));
        when(sarRepository.findAll()).thenReturn(Collections.emptyList());

        service.getMonitoringSARs();

        verify(sarRepository).findAll();
        verify(sarRepository, never()).findByPspId(org.mockito.ArgumentMatchers.anyLong());
    }
}
