package com.posgateway.aml.service.corporate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.posgateway.aml.client.corporate.GdeltAdverseMediaClient;
import com.posgateway.aml.client.corporate.OpenCorporatesClient;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceCheck;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceStatus;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.corporate.CorporateIntelligenceCheckRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateIntelligenceServiceTest {
    @Mock MerchantRepository merchantRepository;
    @Mock CorporateIntelligenceCheckRepository checkRepository;
    @Mock AlertRepository alertRepository;
    @Mock OpenCorporatesClient registryClient;
    @Mock GdeltAdverseMediaClient adverseMediaClient;
    @Mock PspIsolationService isolationService;

    private CorporateIntelligenceService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new CorporateIntelligenceService(merchantRepository, checkRepository, alertRepository,
                registryClient, adverseMediaClient, isolationService, objectMapper, 30, 3, 100);
        when(checkRepository.save(any())).thenAnswer(invocation -> {
            CorporateIntelligenceCheck check = invocation.getArgument(0);
            check.setId(44L);
            return check;
        });
    }

    @Test
    void verifiedRegistryAndNoMediaLeadsProducesDurableClear() {
        Merchant merchant = merchant("ACTIVE");
        when(registryClient.search(any(), any(), any())).thenReturn(registryMatch());
        when(adverseMediaClient.search(any(), any())).thenReturn(new GdeltAdverseMediaClient.AdverseMediaResult(
                true, "GDELT_DOC_2", "\"Acme\" fraud", List.of(), Map.of("query", "q"), null));

        CorporateIntelligenceCheck check = service.performCheck(merchant, "ONBOARDING", "analyst");

        assertEquals(CorporateIntelligenceStatus.CLEAR, check.getStatus());
        assertEquals(100, check.getRegistryMatchScore());
        assertEquals(64, check.getEvidenceHash().length());
        assertNotNull(merchant.getNextCorporateIntelligenceDue());
        verifyNoInteractions(alertRepository);
    }

    @Test
    void adverseMediaLeadForcesReviewAndCreatesUnifiedAlert() {
        Merchant merchant = merchant("ACTIVE");
        when(registryClient.search(any(), any(), any())).thenReturn(registryMatch());
        var article = new GdeltAdverseMediaClient.Article(
                "Acme investigated", "https://news.example/acme", "news.example",
                "20260101T000000Z", "English", "Kenya");
        when(adverseMediaClient.search(any(), any())).thenReturn(new GdeltAdverseMediaClient.AdverseMediaResult(
                true, "GDELT_DOC_2", "\"Acme\" investigation", List.of(article), Map.of(), null));
        when(alertRepository.existsByPspIdAndSourceTypeAndSourceReference(any(), any(), any())).thenReturn(false);

        CorporateIntelligenceCheck check = service.performCheck(merchant, "MANUAL", "analyst");

        assertEquals(CorporateIntelligenceStatus.REVIEW, check.getStatus());
        assertEquals("UNDER_REVIEW", merchant.getStatus());
        assertEquals(1, check.getAdverseMediaArticleCount());
        verify(alertRepository).save(argThat(alert ->
                "CORPORATE_INTELLIGENCE".equals(alert.getSourceType())
                        && Long.valueOf(7L).equals(alert.getMerchantId())));
    }

    @Test
    void providerOutageNeverProducesSyntheticClear() {
        Merchant merchant = merchant("ACTIVE");
        when(registryClient.search(any(), any(), any()))
                .thenReturn(OpenCorporatesClient.RegistrySearchResult.unavailable("registry down"));
        when(adverseMediaClient.search(any(), any()))
                .thenReturn(GdeltAdverseMediaClient.AdverseMediaResult.unavailable("media down"));
        when(alertRepository.existsByPspIdAndSourceTypeAndSourceReference(any(), any(), any())).thenReturn(false);

        CorporateIntelligenceCheck check = service.performCheck(merchant, "PERIODIC", "scheduler");

        assertEquals(CorporateIntelligenceStatus.UNAVAILABLE, check.getStatus());
        assertTrue(check.getDecisionReason().contains("registry down"));
        assertTrue(check.getDecisionReason().contains("media down"));
        verify(alertRepository).save(any());
    }

    private OpenCorporatesClient.RegistrySearchResult registryMatch() {
        var candidate = new OpenCorporatesClient.CompanyCandidate(
                "Acme Limited", "CPR-123", "ke", "Active", "2018-01-02",
                "Nairobi", "https://opencorporates.com/companies/ke/CPR-123");
        return new OpenCorporatesClient.RegistrySearchResult(
                true, "OPENCORPORATES", List.of(candidate), Map.of("totalCount", 1), null);
    }

    private Merchant merchant(String status) {
        Psp psp = new Psp();
        psp.setPspId(9L);
        Merchant merchant = new Merchant();
        merchant.setMerchantId(7L);
        merchant.setPsp(psp);
        merchant.setLegalName("Acme Limited");
        merchant.setTradingName("Acme");
        merchant.setCountry("KEN");
        merchant.setRegistrationNumber("CPR-123");
        merchant.setStatus(status);
        return merchant;
    }
}
