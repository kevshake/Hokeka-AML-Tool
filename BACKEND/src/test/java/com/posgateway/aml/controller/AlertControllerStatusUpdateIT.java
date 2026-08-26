package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.alert.AlertDispositionService;
import com.posgateway.aml.service.case_management.AlertFraudIncidentBridge;
import com.posgateway.aml.service.case_management.AlertToCaseService;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression coverage for PSP-scoped alert status updates. */
@ExtendWith(MockitoExtension.class)
class AlertControllerStatusUpdateIT {

    @Mock private AlertRepository alertRepository;
    @Mock private AlertDispositionService alertDispositionService;
    @Mock private PspIsolationService pspIsolationService;
    @Mock private MerchantRepository merchantRepository;
    @Mock private RuleEffectivenessService ruleEffectivenessService;
    @Mock private AlertToCaseService alertToCaseService;
    @Mock private AlertFraudIncidentBridge alertFraudIncidentBridge;
    @Mock private UserRepository userRepository;

    private AlertController alertController;

    @BeforeEach
    void setUp() {
        alertController = new AlertController(
                alertRepository,
                alertDispositionService,
                pspIsolationService,
                merchantRepository,
                ruleEffectivenessService,
                alertToCaseService,
                alertFraudIncidentBridge,
                userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * W14-7 fix: resolveAlert's case-bridge used to resolve disposingUser only when
     * Authentication.getPrincipal() was literally a User instance, silently dropping attribution
     * (alertToCaseService and alertFraudBridge both got a null user) for any other principal
     * shape. It must now fall back to a username lookup via UserRepository.
     */
    @Test
    void resolveAlert_withNonUserPrincipal_stillResolvesDisposingUserByUsername() {
        Alert alert = new Alert();
        alert.setAlertId(200L);
        alert.setStatus("open");
        alert.setPspId(7L);

        User resolvedUser = new User();
        resolvedUser.setId(55L);
        resolvedUser.setUsername("jdoe");

        // Principal is a plain String (e.g. "jdoe"), NOT a User instance -- the exact case the
        // old instanceof-only check silently dropped.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jdoe", "n/a", Collections.emptyList()));

        AlertController.ResolveAlertRequest request = new AlertController.ResolveAlertRequest();
        request.setDisposition(AlertDisposition.TRUE_POSITIVE_SAR_FILED);

        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);
        when(alertRepository.findById(200L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(resolvedUser));

        alertController.resolveAlert(200L, request);

        verify(alertToCaseService).resolveAlertToCase(eq(200L), eq(AlertDisposition.TRUE_POSITIVE_SAR_FILED),
                any(), eq(resolvedUser));
        verify(alertFraudIncidentBridge).createFraudIncidentFromAlert(eq(200L),
                eq(AlertDisposition.TRUE_POSITIVE_SAR_FILED), any(), eq(resolvedUser));
    }

    @Test
    void updateStatus_withMatchingPspMerchant_persistsAndReturns200() {
        Long pspId = 7L;
        Alert alert = new Alert();
        alert.setAlertId(123L);
        alert.setStatus("open");
        alert.setMerchantId(99L);

        Psp psp = Psp.builder().pspId(pspId).build();
        Merchant merchant = new Merchant();
        merchant.setMerchantId(99L);
        merchant.setPsp(psp);
        AlertController.UpdateAlertStatusRequest request = new AlertController.UpdateAlertStatusRequest();
        request.setStatus("resolved");

        when(pspIsolationService.getCurrentUserPspId()).thenReturn(pspId);
        when(alertRepository.findById(123L)).thenReturn(Optional.of(alert));
        when(merchantRepository.findById(99L)).thenReturn(Optional.of(merchant));
        when(alertRepository.save(alert)).thenReturn(alert);

        ResponseEntity<?> response = alertController.updateAlertStatus(123L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(alert, response.getBody());
        assertEquals("resolved", alert.getStatus());
        verify(alertRepository).save(alert);
    }

    @Test
    void updateStatus_withDirectPspAlert_rejectsAnotherPsp() {
        Alert alert = new Alert();
        alert.setAlertId(124L);
        alert.setStatus("open");
        alert.setPspId(7L);
        AlertController.UpdateAlertStatusRequest request = new AlertController.UpdateAlertStatusRequest();
        request.setStatus("resolved");

        when(pspIsolationService.getCurrentUserPspId()).thenReturn(8L);
        when(alertRepository.findById(124L)).thenReturn(Optional.of(alert));

        ResponseEntity<?> response = alertController.updateAlertStatus(124L, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(alertRepository, never()).save(any(Alert.class));
        verify(merchantRepository, never()).findById(any());
    }
}
