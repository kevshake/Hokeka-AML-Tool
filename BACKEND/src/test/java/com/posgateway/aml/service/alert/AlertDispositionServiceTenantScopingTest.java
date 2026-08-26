package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W20-9: AlertDispositionService.getDispositionStatistics/
 * getDispositionDistribution used to call the unscoped AlertRepository.findAlertsInTimeRange for
 * every caller, leaking every PSP's alert counts/dispositions to any authenticated tenant user.
 * Both methods must now route through the PSP-scoped query for a PSP-scoped caller, and only fall
 * back to the unscoped query for a platform admin (null PSP).
 */
@ExtendWith(MockitoExtension.class)
class AlertDispositionServiceTenantScopingTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private RuleEffectivenessService ruleEffectivenessService;
    @Mock
    private UserRepository userRepository;

    private AlertDispositionService service;

    private final LocalDateTime start = LocalDateTime.now().minusDays(1);
    private final LocalDateTime end = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service = new AlertDispositionService(alertRepository, ruleEffectivenessService, userRepository);
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
    void statisticsForPspScopedCallerUseThePspScopedQueryOnly() {
        Psp psp = new Psp();
        psp.setPspId(7L);
        User pspUser = new User();
        pspUser.setUsername("psp_user");
        pspUser.setPsp(psp);

        authenticateAs("psp_user");
        when(userRepository.findByUsername("psp_user")).thenReturn(Optional.of(pspUser));
        when(alertRepository.findAlertsInTimeRangeForPsp(eq(7L), any(), any()))
                .thenReturn(List.of(new Alert()));

        AlertDispositionService.AlertDispositionStats stats = service.getDispositionStatistics(start, end);

        verify(alertRepository).findAlertsInTimeRangeForPsp(7L, start, end);
        // The unscoped, cross-tenant query must never be reached for a PSP-scoped caller.
        verify(alertRepository, never()).findAlertsInTimeRange(any(), any());
    }

    @Test
    void distributionForPspScopedCallerUseThePspScopedQueryOnly() {
        Psp psp = new Psp();
        psp.setPspId(7L);
        User pspUser = new User();
        pspUser.setUsername("psp_user");
        pspUser.setPsp(psp);

        authenticateAs("psp_user");
        when(userRepository.findByUsername("psp_user")).thenReturn(Optional.of(pspUser));
        when(alertRepository.findAlertsInTimeRangeForPsp(eq(7L), any(), any()))
                .thenReturn(Collections.emptyList());

        service.getDispositionDistribution(start, end);

        verify(alertRepository).findAlertsInTimeRangeForPsp(7L, start, end);
        verify(alertRepository, never()).findAlertsInTimeRange(any(), any());
    }

    @Test
    void statisticsForPlatformAdminFallsBackToUnscopedQuery() {
        User admin = new User();
        admin.setUsername("platform_admin");
        admin.setPsp(null); // platform admin: no PSP restriction

        authenticateAs("platform_admin");
        when(userRepository.findByUsername("platform_admin")).thenReturn(Optional.of(admin));
        when(alertRepository.findAlertsInTimeRange(start, end)).thenReturn(Collections.emptyList());

        service.getDispositionStatistics(start, end);

        verify(alertRepository).findAlertsInTimeRange(start, end);
        verify(alertRepository, never()).findAlertsInTimeRangeForPsp(any(), any(), any());
    }
}
