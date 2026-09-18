package com.posgateway.aml.service.edd;

import com.posgateway.aml.entity.edd.EddEvidenceEvent;
import com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.edd.EddEvidenceEventRepository;
import com.posgateway.aml.repository.edd.EnhancedDueDiligenceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedDueDiligenceServiceTest {
    @Mock MerchantRepository merchantRepository;
    @Mock EnhancedDueDiligenceRequestRepository requestRepository;
    @Mock EddEvidenceEventRepository eventRepository;
    private EnhancedDueDiligenceService service;

    @BeforeEach
    void setUp() {
        service = new EnhancedDueDiligenceService(merchantRepository, requestRepository, eventRepository);
    }

    @Test
    void rejectsUnknownChecklistCodeInsteadOfIgnoringIt() {
        EnhancedDueDiligenceRequest request = new EnhancedDueDiligenceRequest(3L);
        request.setId(8L);
        when(requestRepository.findByMerchantId(3L)).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateItemStatus(3L, "UNKNOWN", true, "reviewer", null));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void completionUpdatesMerchantReviewAndWritesEvidence() {
        Merchant merchant = new Merchant();
        EnhancedDueDiligenceRequest request = new EnhancedDueDiligenceRequest(3L);
        request.setId(8L);
        request.setSourceOfFundsVerified(true);
        request.setSourceOfWealthVerified(true);
        request.setSeniorManagementApproval(true);
        request.setFamilyAssociateChecks(true);
        request.setSiteVisitRequired(false);
        when(requestRepository.findByMerchantId(3L)).thenReturn(Optional.of(request));
        when(merchantRepository.findById(3L)).thenReturn(Optional.of(merchant));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findByEddRequestIdOrderByOccurredAtDesc(8L)).thenReturn(List.of());

        var status = service.updateItemStatus(3L, "TRANSACTION_PURPOSE_REVIEW", true, "mlro", "Evidence checked");

        assertEquals("COMPLETED", status.status());
        assertNotNull(status.completedAt());
        assertNotNull(merchant.getLastEddReviewAt());
        verify(eventRepository).save(argThat(event -> event.getItemCode().equals("TRANSACTION_PURPOSE_REVIEW")
                && event.getNewValue() && "mlro".equals(event.getPerformedBy())));
    }
}
