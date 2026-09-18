package com.posgateway.aml.service.aml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W14-6: saveScreeningResult used to call
 * objectMapper.convertValue(result.getMatches(), Map.class) directly, which throws
 * IllegalArgumentException whenever the match list is non-empty (Jackson can't convert a List to a
 * Map), and that exception was caught and rethrown -- failing the save exactly when there was a
 * sanctions match worth persisting. This is the specific "matches exist" scenario the old code
 * never handled.
 */
@ExtendWith(MockitoExtension.class)
class AmlScreeningOrchestratorMatchDetailsTest {

    @Mock
    private AerospikeSanctionsScreeningService screeningEngine;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private MerchantScreeningResultRepository screeningResultRepository;
    @Mock
    private AuditTrailRepository auditTrailRepository;

    private AmlScreeningOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper: this test's whole point is exercising real Jackson conversion
        // behaviour, not a mocked stand-in for it.
        orchestrator = new AmlScreeningOrchestrator(
                screeningEngine, merchantRepository, screeningResultRepository,
                auditTrailRepository, new ObjectMapper());
    }

    @Test
    void screeningAMerchantWithARealMatchSavesSuccessfullyInsteadOfThrowing() {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(1L);
        merchant.setLegalName("Acme Corp");

        ScreeningResult.Match match = ScreeningResult.Match.builder()
                .matchedName("Acme Corp")
                .aliases(List.of("Acme Corporation"))
                .similarityScore(0.95)
                .listName("OFAC_SDN")
                .build();

        ScreeningResult result = ScreeningResult.builder()
                .screenedName("Acme Corp")
                .status(ScreeningResult.ScreeningStatus.MATCH)
                .matchCount(1)
                .highestMatchScore(0.95)
                .matches(List.of(match))
                .screenedAt(LocalDateTime.now())
                .build();

        when(screeningEngine.screenMerchant("Acme Corp", null)).thenReturn(result);
        when(screeningResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Before the fix, this threw because convertValue(List, Map.class) can't convert a
        // non-empty list -- the whole save silently failed for exactly the merchants that had a
        // real match worth recording.
        assertDoesNotThrow(() -> orchestrator.screenMerchant(merchant));

        ArgumentCaptor<MerchantScreeningResult> captor = ArgumentCaptor.forClass(MerchantScreeningResult.class);
        org.mockito.Mockito.verify(screeningResultRepository).save(captor.capture());

        Map<String, Object> matchDetails = captor.getValue().getMatchDetails();
        assertNotNull(matchDetails);
        assertEquals(1, matchDetails.get("matchCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) matchDetails.get("matches");
        assertEquals(1, matches.size());
        assertEquals("Acme Corp", matches.get(0).get("matchedName"));
    }
}
