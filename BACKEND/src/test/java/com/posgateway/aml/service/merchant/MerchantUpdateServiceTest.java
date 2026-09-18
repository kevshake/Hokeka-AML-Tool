package com.posgateway.aml.service.merchant;

import com.posgateway.aml.dto.request.MerchantUpdateRequest;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantUpdateServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private AmlScreeningOrchestrator screeningOrchestrator;
    @Mock private AuditTrailRepository auditTrailRepository;
    @Mock private PspIsolationService pspIsolationService;
    @Mock private com.posgateway.aml.service.underwriting.MerchantVerificationOrchestrator verificationOrchestrator;
    @Mock private com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository verificationSignalRepository;

    private MerchantUpdateService service;

    @BeforeEach
    void setUp() {
        service = new MerchantUpdateService(
                merchantRepository, screeningOrchestrator, auditTrailRepository, pspIsolationService,
                verificationOrchestrator, verificationSignalRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("analyst@example.com", "n/a", java.util.List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateValidatesTenantAndAuditsAuthenticatedActor() {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(44L);
        merchant.setContactEmail("old@example.com");
        when(merchantRepository.findById(44L)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(merchant)).thenReturn(merchant);
        MerchantUpdateRequest request = new MerchantUpdateRequest();
        request.setContactEmail("new@example.com");

        Merchant saved = service.updateMerchant(44L, request);

        assertEquals("new@example.com", saved.getContactEmail());
        verify(pspIsolationService).validateMerchantAccess(merchant);
        verify(auditTrailRepository).save(any(AuditTrail.class));
        verify(auditTrailRepository).save(org.mockito.ArgumentMatchers.argThat(
                audit -> "analyst@example.com".equals(audit.getPerformedBy())));
    }

    @Test
    void updateStopsBeforeMutationWhenTenantAccessIsDenied() {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(44L);
        when(merchantRepository.findById(44L)).thenReturn(Optional.of(merchant));
        doThrow(new SecurityException("cross-tenant"))
                .when(pspIsolationService).validateMerchantAccess(merchant);

        assertThrows(SecurityException.class,
                () -> service.updateMerchant(44L, new MerchantUpdateRequest()));

        verify(merchantRepository, never()).save(any());
        verify(auditTrailRepository, never()).save(any());
    }
}
