package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.underwriting.UnderwritingDecision;
import com.posgateway.aml.model.underwriting.UnderwritingOutcome;
import com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository;
import com.posgateway.aml.service.edd.EnhancedDueDiligenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantVerificationOrchestratorTest {

    @Test
    void merchantWithNoBeneficialOwnersIsHardStoppedToReject() {
        MerchantVerificationSignalRepository signalRepo = mock(MerchantVerificationSignalRepository.class);
        when(signalRepo.saveAll(anyList())).thenReturn(List.of());

        MerchantLinkageService linkage = mock(MerchantLinkageService.class);
        when(linkage.findLinkages(any())).thenReturn(List.of());

        TransactionLaunderingCheckService tl = mock(TransactionLaunderingCheckService.class);
        when(tl.findSignals(any())).thenReturn(List.of());

        EnhancedDueDiligenceService edd = mock(EnhancedDueDiligenceService.class);

        MerchantVerificationOrchestrator orchestrator = new MerchantVerificationOrchestrator(
                List.of(),                    // no external providers
                new HardStopEvaluator(),      // real hard-stop evaluation
                signalRepo, linkage, tl, edd);

        Merchant merchant = new Merchant();
        merchant.setMerchantId(1L);
        merchant.setLegalName("No UBO Ltd");
        // No beneficial owners declared → NO_BENEFICIAL_OWNERS (CRITICAL) → hard stop.

        UnderwritingOutcome outcome = orchestrator.verify(merchant);

        assertEquals(UnderwritingDecision.REJECT, outcome.getDecision());
        assertFalse(outcome.getHardStops().isEmpty());
        assertTrue(outcome.getHardStops().stream().anyMatch(h -> h.startsWith("NO_BENEFICIAL_OWNERS")));
    }
}
