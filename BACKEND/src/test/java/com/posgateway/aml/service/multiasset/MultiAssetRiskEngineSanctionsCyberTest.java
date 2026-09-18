package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.IngestTransactionRequest;
import com.posgateway.aml.entity.multiasset.AssetClass;
import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.entity.multiasset.MultiAssetTransactionType;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.EntityType;
import com.posgateway.aml.model.ScreeningResult.ScreeningStatus;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiAssetRiskEngineSanctionsCyberTest {

    private MultiAssetRiskEngine engine;
    private MultiAssetCustomer customer;
    private AerospikeSanctionsScreeningService sanctions;

    @BeforeEach
    void setUp() {
        BlockchainAnalyticsClient blockchain = new BlockchainAnalyticsClient(
                false, "TEST_PROVIDER", "http://localhost:1", "/screen", "", Duration.ofMillis(50));
        engine = new MultiAssetRiskEngine(blockchain, BigDecimal.valueOf(2_000),
                BigDecimal.valueOf(1_000), BigDecimal.valueOf(5), "GAMBLING,CRYPTO_OTC",
                2, 3, BigDecimal.valueOf(10_000));
        sanctions = mock(AerospikeSanctionsScreeningService.class);
        ReflectionTestUtils.setField(engine, "sanctionsScreeningService", sanctions);

        customer = new MultiAssetCustomer();
        customer.setDisplayName("Jane Doe");
        customer.setCountryCode("KE");
    }

    @Test
    void sanctionsMatchProducesSanctionsSignalType() {
        when(sanctions.screenName(eq("Listed Person"), eq(EntityType.PERSON))).thenReturn(
                ScreeningResult.builder()
                        .screenedName("Listed Person")
                        .entityType(EntityType.PERSON)
                        .status(ScreeningStatus.MATCH)
                        .matchCount(1)
                        .highestMatchScore(0.99)
                        .build());

        IngestTransactionRequest request = request("Listed Person", null);
        MultiAssetRiskEngine.Assessment assessment = engine.assess(customer, null, null, request, List.of());

        assertThat(assessment.signals()).filteredOn(s -> s.code().equals("SANCTIONS_NAME_MATCH"))
                .extracting(MultiAssetRiskEngine.SignalDraft::signalType)
                .containsExactly(FinancialCrimeSignalType.SANCTIONS);
    }

    @Test
    void cyberIncidentMetadataProducesCyberSignalType() {
        when(sanctions.screenName(eq("Jane Doe"), eq(EntityType.PERSON))).thenReturn(
                ScreeningResult.builder()
                        .screenedName("Jane Doe")
                        .entityType(EntityType.PERSON)
                        .status(ScreeningStatus.CLEAR)
                        .build());
        IngestTransactionRequest request = request(null, Map.of("cyberIncidentNumber", "CYB-2026-001"));
        MultiAssetRiskEngine.Assessment assessment = engine.assess(customer, null, null, request, List.of());

        assertThat(assessment.signals()).filteredOn(s -> s.code().equals("CYBER_INCIDENT_LINKED"))
                .extracting(MultiAssetRiskEngine.SignalDraft::signalType)
                .containsExactly(FinancialCrimeSignalType.CYBER);
    }

    private static IngestTransactionRequest request(String counterparty, Map<String, Object> metadata) {
        return new IngestTransactionRequest(
                "EXT-1", 1L, null, null, AssetClass.E_MONEY, MultiAssetTransactionType.TRANSFER,
                BigDecimal.valueOf(100), null, "USD", null, null, null, LocalDateTime.now(), "KE",
                null, null, counterparty, null, null, null, null, null, null, null, null, metadata, null);
    }
}
