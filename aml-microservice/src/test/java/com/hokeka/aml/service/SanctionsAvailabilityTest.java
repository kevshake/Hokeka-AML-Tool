package com.hokeka.aml.service;

import com.hokeka.aml.model.AmlResult;
import com.hokeka.aml.model.SanctionsScreenResponse;
import com.hokeka.aml.model.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SanctionsAvailabilityTest {

    @Test
    void disconnectedAerospikeReturnsUnavailable() {
        SanctionsService service = new SanctionsService();

        SanctionsScreenResponse result = service.screenName("Jane Doe", "PERSON");

        assertEquals("UNAVAILABLE", result.getStatus());
        assertTrue(result.getMatches().isEmpty());
    }

    @Test
    void blankNameIsRejectedInsteadOfCleared() {
        SanctionsService service = new SanctionsService();

        assertThrows(IllegalArgumentException.class, () -> service.screenName("  ", "PERSON"));
    }

    @Test
    void unavailableSanctionsForcesTransactionReview() {
        SanctionsService sanctions = mock(SanctionsService.class);
        when(sanctions.screenName("Jane Doe", null)).thenReturn(
                new SanctionsScreenResponse("Jane Doe", "UNAVAILABLE", List.of(), java.time.Instant.now()));
        AmlCheckService service = new AmlCheckService("IR", "NG");
        ReflectionTestUtils.setField(service, "sanctionsService", sanctions);

        TransactionRequest request = transaction("txn-1", 100L);
        request.setSenderName("Jane Doe");
        AmlResult result = service.check(request);

        assertEquals("REVIEW", result.getDecision());
        assertEquals("MEDIUM", result.getRiskLevel());
        assertTrue(result.getIndicators().contains("SANCTIONS_UNAVAILABLE"));
    }

    @Test
    void sanctionsHitForcesBlock() {
        SanctionsService sanctions = mock(SanctionsService.class);
        when(sanctions.screenName("Listed Person", null)).thenReturn(
                new SanctionsScreenResponse("Listed Person", "FLAGGED", List.of(), java.time.Instant.now()));
        AmlCheckService service = new AmlCheckService("IR", "NG");
        ReflectionTestUtils.setField(service, "sanctionsService", sanctions);

        TransactionRequest request = transaction("txn-2", 100L);
        request.setSenderName("Listed Person");
        AmlResult result = service.check(request);

        assertEquals("BLOCK", result.getDecision());
        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.getIndicators().contains("SANCTIONS_FLAGGED"));
    }

    @Test
    void amountCentsUsesMinorUnitThresholds() {
        AmlCheckService service = new AmlCheckService("IR", "NG");

        AmlResult result = service.check(transaction("txn-3", 1_000_001L));

        assertEquals("REVIEW", result.getDecision());
        assertEquals(0.4, result.getRiskScore(), 0.0001);
    }

    @Test
    void searchKeysCoverAliasesPhoneticsAndInternalBigrams() {
        Set<String> keys = Set.copyOf(SanctionsService.buildSearchKeys(
                "Osama Bin Laden", List.of("Usama ibn Ladin")));
        Set<String> queryKeys = Set.copyOf(SanctionsService.buildSearchKeys(
                "Usama Bin Laden", List.of()));

        assertTrue(keys.contains("e:usama ibn ladin"));
        assertTrue(keys.stream().anyMatch(value -> value.startsWith("m:")));
        assertTrue(keys.contains("g:sa"));
        assertTrue(keys.stream().anyMatch(queryKeys::contains));
    }

    @Test
    void searchKeysNormalizeDiacritics() {
        Set<String> keys = Set.copyOf(SanctionsService.buildSearchKeys(
                "José Álvarez", List.of()));

        assertTrue(keys.contains("e:jose alvarez"));
        assertTrue(keys.contains("g:jo"));
    }

    private static TransactionRequest transaction(String id, long amountCents) {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionId(id);
        request.setPspId(10L);
        request.setMerchantId("merchant-1");
        request.setAmountCents(amountCents);
        request.setCurrency("USD");
        return request;
    }
}
