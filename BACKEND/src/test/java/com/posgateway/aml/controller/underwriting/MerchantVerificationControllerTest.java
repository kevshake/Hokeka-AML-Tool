package com.posgateway.aml.controller.underwriting;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.underwriting.MerchantVerificationSignal;
import com.posgateway.aml.model.underwriting.UnderwritingDecision;
import com.posgateway.aml.model.underwriting.UnderwritingOutcome;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.underwriting.MerchantVerificationOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantVerificationControllerTest {

    @Mock private MerchantVerificationOrchestrator orchestrator;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantVerificationSignalRepository signalRepository;
    @Mock private PspIsolationService isolationService;

    private MerchantVerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new MerchantVerificationController(
                orchestrator, merchantRepository, signalRepository, isolationService);
    }

    @Test
    void verify_rejectsCrossPspAccess() {
        Merchant merchant = merchantWithPsp(1L, 10L);
        User user = userWithPsp(20L);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(isolationService.getCurrentUser()).thenReturn(user);
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.verify(1L));
        verify(orchestrator, never()).verify(any());
    }

    @Test
    void verify_allowsSamePsp() {
        Merchant merchant = merchantWithPsp(1L, 10L);
        User user = userWithPsp(10L);
        UnderwritingOutcome outcome = new UnderwritingOutcome();
        outcome.setDecision(UnderwritingDecision.APPROVE);
        outcome.setScore(12);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(isolationService.getCurrentUser()).thenReturn(user);
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);
        when(orchestrator.verify(merchant)).thenReturn(outcome);

        ResponseEntity<UnderwritingOutcome> response = controller.verify(1L);
        assertEquals(UnderwritingDecision.APPROVE, response.getBody().getDecision());
    }

    @Test
    void signals_scopedToMerchantAfterAccessCheck() {
        Merchant merchant = merchantWithPsp(2L, 5L);
        User user = userWithPsp(5L);
        MerchantVerificationSignal signal = new MerchantVerificationSignal();
        signal.setMerchantId(2L);
        signal.setSignalCode("SANCTIONS_CLEAR");

        when(merchantRepository.findById(2L)).thenReturn(Optional.of(merchant));
        when(isolationService.getCurrentUser()).thenReturn(user);
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);
        when(signalRepository.findByMerchantIdOrderByObservedAtDesc(2L)).thenReturn(List.of(signal));

        ResponseEntity<List<MerchantVerificationSignal>> response = controller.signals(2L);
        assertEquals(1, response.getBody().size());
    }

    private static Merchant merchantWithPsp(Long merchantId, Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        Merchant merchant = new Merchant();
        merchant.setMerchantId(merchantId);
        merchant.setPsp(psp);
        return merchant;
    }

    private static User userWithPsp(Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        User user = new User();
        user.setPsp(psp);
        return user;
    }
}
