package com.posgateway.aml.service.aml;

import com.posgateway.aml.client.aml.SanctionsScreenClient;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenResponse;
import com.posgateway.aml.model.ScreeningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AerospikeSanctionsScreeningServiceTest {

    @Mock
    private SanctionsScreenClient client;

    private AerospikeSanctionsScreeningService service;

    @BeforeEach
    void setUp() {
        service = new AerospikeSanctionsScreeningService(client);
    }

    @Test
    void unavailableMicroserviceNeverProducesClearResult() {
        when(client.screen(any())).thenReturn(
                BackendSanctionsScreenResponse.unavailable("Jane Doe"));

        ScreeningResult result = service.screenName("Jane Doe", ScreeningResult.EntityType.PERSON);

        assertEquals(ScreeningResult.ScreeningStatus.UNAVAILABLE, result.getStatus());
        assertEquals("AML_MICROSERVICE_UNAVAILABLE", result.getScreeningProvider());
        assertFalse(result.hasMatches());
    }

    @Test
    void pepLevelFromMicroserviceFlowsIntoMatch() {
        BackendSanctionsScreenResponse flagged = new BackendSanctionsScreenResponse(
                "Jane Politician",
                "REVIEW",
                List.of(new BackendSanctionsScreenResponse.MatchDto(
                        "Jane Politician", 0.9, "peps", "pep-1", "PEP")),
                Instant.now());
        when(client.screen(any())).thenReturn(flagged);

        ScreeningResult result = service.screenName("Jane Politician", ScreeningResult.EntityType.PERSON);

        assertEquals(1, result.getMatchCount());
        assertEquals("PEP", result.getMatches().get(0).getPepLevel());
    }

    @Test
    void sanctionTypeIsDerivedPerMatchInsteadOfAlwaysTheSameGenericLiteral() {
        // W14-4 fix: sanctionType used to be the hardcoded literal "Sanctions match" for every
        // match. A PEP-level match and an OFAC list match must now produce distinguishable text.
        BackendSanctionsScreenResponse flagged = new BackendSanctionsScreenResponse(
                "Multi Hit",
                "FLAGGED",
                List.of(
                        new BackendSanctionsScreenResponse.MatchDto(
                                "Multi Hit", 0.95, "OFAC_SDN", "entity-1", null),
                        new BackendSanctionsScreenResponse.MatchDto(
                                "Multi Hit", 0.9, "peps", "entity-2", "CURRENT")),
                Instant.now());
        when(client.screen(any())).thenReturn(flagged);

        ScreeningResult result = service.screenName("Multi Hit", ScreeningResult.EntityType.PERSON);

        assertEquals(2, result.getMatchCount());
        assertEquals("Sanctions list match: OFAC_SDN", result.getMatches().get(0).getSanctionType());
        assertEquals("PEP match (CURRENT)", result.getMatches().get(1).getSanctionType());
    }

    @Test
    void merchantTradingNameHitIsCombinedWithLegalNameResult() {
        BackendSanctionsScreenResponse clear = new BackendSanctionsScreenResponse(
                "Acme Holdings", "CLEAR", List.of(), Instant.now());
        BackendSanctionsScreenResponse flagged = new BackendSanctionsScreenResponse(
                "Restricted Trading",
                "FLAGGED",
                List.of(new BackendSanctionsScreenResponse.MatchDto(
                        "Restricted Trading", 0.99, "OFAC", "entity-1", null)),
                Instant.now());
        when(client.screen(any())).thenReturn(clear, flagged);

        ScreeningResult result = service.screenMerchant("Acme Holdings", "Restricted Trading");

        assertEquals(ScreeningResult.ScreeningStatus.MATCH, result.getStatus());
        assertEquals(1, result.getMatchCount());
        assertEquals(0.99, result.getHighestMatchScore());
    }

    @Test
    void unknownProviderStatusIsUnavailableRatherThanClear() {
        when(client.screen(any())).thenReturn(new BackendSanctionsScreenResponse(
                "Jane Doe", "UNKNOWN_STATUS", List.of(), Instant.now()));

        ScreeningResult result = service.screenName("Jane Doe", ScreeningResult.EntityType.PERSON);

        assertEquals(ScreeningResult.ScreeningStatus.UNAVAILABLE, result.getStatus());
    }
}
