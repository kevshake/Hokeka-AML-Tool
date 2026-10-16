package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.ai.decision.AiEngineAdvisor;
import com.posgateway.aml.service.ai.decision.AiEngineType;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import com.posgateway.aml.service.cache.ScreeningCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that only confirmed sanctions hits block outright, while non-exact candidates are routed
 * to disambiguation / review (with a REVIEW baseline) instead of being blocked immediately.
 */
class RealTimeTransactionScreeningServiceDisambiguationTest {

    private AerospikeSanctionsScreeningService aerospike;
    private AiEngineAdvisor advisor;
    private RealTimeTransactionScreeningService service;

    @BeforeEach
    void setUp() {
        aerospike = mock(AerospikeSanctionsScreeningService.class);
        advisor = mock(AiEngineAdvisor.class);
        service = new RealTimeTransactionScreeningService(
                aerospike, mock(MerchantRepository.class), mock(ScreeningWhitelistService.class),
                mock(ScreeningCacheService.class));
        ReflectionTestUtils.setField(service, "realtimeScreeningEnabled", true);
        ReflectionTestUtils.setField(service, "blockOnMatch", true);
        ReflectionTestUtils.setField(service, "blockOnUnavailable", true);
        ReflectionTestUtils.setField(service, "screenMerchant", false);
        ReflectionTestUtils.setField(service, "screenCounterparty", true);
        ReflectionTestUtils.setField(service, "aiEngineAdvisor", advisor);
    }

    /** Builds a counterparty transaction whose cardholder name is carried in the CH_NAME tag. */
    private static TransactionEntity txn() {
        TransactionEntity t = new TransactionEntity();
        t.setTxnId(77L);
        t.setPspId(3L);
        t.setIsoMsg("CH_NAME=Jon Doh|");
        return t;
    }

    /** Builds a screening result with one match at the given status / similarity / match type. */
    private static ScreeningResult result(ScreeningResult.ScreeningStatus status, double similarity,
                                          ScreeningResult.MatchType type) {
        ScreeningResult.Match match = ScreeningResult.Match.builder()
                .matchedName("John Doe")
                .similarityScore(similarity)
                .listName("OFAC_SDN")
                .matchType(type)
                .build();
        return ScreeningResult.builder()
                .status(status)
                .highestMatchScore(similarity)
                .matchCount(1)
                .matches(List.of(match))
                .build();
    }

    @Test
    void fuzzyPotentialMatchIsRoutedToDisambiguationNotBlocked() {
        when(aerospike.screenName(eq("Jon Doh"), any()))
                .thenReturn(result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.83,
                        ScreeningResult.MatchType.NAME_MATCH));

        RealTimeTransactionScreeningService.TransactionScreeningResult r = service.screenTransaction(txn());

        assertTrue(r.hasMatches());
        assertFalse(r.shouldBlock());
        assertTrue(r.requiresReview());
        verify(advisor).adviseAsync(eq(AiEngineType.SANCTIONS_DISAMBIGUATION), eq(3L), eq("REVIEW"),
                anyMap(), eq(77L), isNull(), isNull(), isNull());
    }

    @Test
    void confirmedMatchStillBlocks() {
        when(aerospike.screenName(eq("Jon Doh"), any()))
                .thenReturn(result(ScreeningResult.ScreeningStatus.MATCH, 0.97,
                        ScreeningResult.MatchType.NAME_MATCH));

        RealTimeTransactionScreeningService.TransactionScreeningResult r = service.screenTransaction(txn());

        assertTrue(r.shouldBlock());
        assertFalse(r.requiresReview());
        verify(advisor).adviseAsync(eq(AiEngineType.SANCTIONS_DISAMBIGUATION), eq(3L), eq("BLOCK"),
                anyMap(), eq(77L), isNull(), isNull(), isNull());
    }

    @Test
    void highSimilarityOrDobConfirmedPotentialMatchIsTreatedAsConfirmed() {
        assertTrue(service.isConfirmedHit(result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.96,
                ScreeningResult.MatchType.NAME_MATCH)));
        assertTrue(service.isConfirmedHit(result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.81,
                ScreeningResult.MatchType.DOB_CONFIRMED)));
        assertFalse(service.isConfirmedHit(result(ScreeningResult.ScreeningStatus.POTENTIAL_MATCH, 0.81,
                ScreeningResult.MatchType.ALIAS_MATCH)));
    }

    @Test
    void missingStatusFailsClosedAsConfirmed() {
        assertTrue(service.isConfirmedHit(result(null, 0.5, ScreeningResult.MatchType.NAME_MATCH)));
    }
}
