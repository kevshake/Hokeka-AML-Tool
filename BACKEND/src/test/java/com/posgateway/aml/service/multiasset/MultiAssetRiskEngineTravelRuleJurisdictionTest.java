package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.IngestTransactionRequest;
import com.posgateway.aml.entity.crypto.TravelRuleJurisdictionPolicy;
import com.posgateway.aml.entity.multiasset.AssetClass;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.entity.multiasset.MultiAssetTransactionType;
import com.posgateway.aml.entity.multiasset.ProductDomain;
import com.posgateway.aml.entity.multiasset.TravelRuleStatus;
import com.posgateway.aml.repository.crypto.TravelRulePolicyRepository;
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

/**
 * Verifies the fix for W21-7: MultiAssetRiskEngine used to gate every jurisdiction with a single
 * global travel-rule threshold (@Value constant), unlike VirtualAssetComplianceService, which
 * already looks up a per-jurisdiction TravelRuleJurisdictionPolicy. A per-jurisdiction policy
 * (when one exists for the transaction's PSP+countryCode) must now override the global default.
 */
class MultiAssetRiskEngineTravelRuleJurisdictionTest {

    private MultiAssetRiskEngine engine;
    private TravelRulePolicyRepository policyRepository;
    private MultiAssetCustomer customer;

    private static final BigDecimal GLOBAL_DEFAULT = BigDecimal.valueOf(1_000);

    private void setUp() {
        BlockchainAnalyticsClient blockchain = new BlockchainAnalyticsClient(
                false, "TEST_PROVIDER", "http://localhost:1", "/screen", "", Duration.ofMillis(50));
        engine = new MultiAssetRiskEngine(blockchain, BigDecimal.valueOf(2_000),
                GLOBAL_DEFAULT, BigDecimal.valueOf(5), "GAMBLING,CRYPTO_OTC", 2, 3, BigDecimal.valueOf(10_000));

        policyRepository = mock(TravelRulePolicyRepository.class);
        ReflectionTestUtils.setField(engine, "travelRulePolicyRepository", policyRepository);

        customer = new MultiAssetCustomer();
        customer.setExternalCustomerId("CUST-1");
        customer.setCountryCode("KE");
        customer.setPspId(7L);
    }

    private IngestTransactionRequest cryptoTransfer(BigDecimal fiatUsd) {
        return new IngestTransactionRequest(
                "TX-JUR-1", 1L, null, null, AssetClass.CRYPTO, MultiAssetTransactionType.TRANSFER,
                BigDecimal.ONE, fiatUsd, "BTC", "BTC", null, null,
                LocalDateTime.now(), "KE", null, null, "bc1-counterparty", null,
                "bitcoin", "bitcoin", null, null, null, null, null, Map.of(),
                ProductDomain.VIRTUAL_ASSET);
    }

    @Test
    void perJurisdictionPolicyOverridesTheGlobalDefaultThreshold() {
        setUp();
        // $700 is below the global default ($1,000) -- would resolve NOT_REQUIRED globally -- but
        // above a tighter KE-specific policy threshold ($500), and no travel-rule data is present.
        TravelRuleJurisdictionPolicy kePolicy = mock(TravelRuleJurisdictionPolicy.class);
        when(kePolicy.getThresholdUsd()).thenReturn(BigDecimal.valueOf(500));
        when(policyRepository.findActive(eq(7L), eq("KE"), any())).thenReturn(List.of(kePolicy));

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null,
                cryptoTransfer(BigDecimal.valueOf(700)), List.of());

        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.INCOMPLETE);
        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("CRYPTO_TRAVEL_RULE_INCOMPLETE");
    }

    @Test
    void fallsBackToTheGlobalDefaultWhenNoJurisdictionPolicyExists() {
        setUp();
        when(policyRepository.findActive(eq(7L), eq("KE"), any())).thenReturn(List.of());

        // $700 is below the global default ($1,000) -- with no override, must resolve NOT_REQUIRED.
        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null,
                cryptoTransfer(BigDecimal.valueOf(700)), List.of());

        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.NOT_REQUIRED);
        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .doesNotContain("CRYPTO_TRAVEL_RULE_INCOMPLETE");
    }

    @Test
    void skipsTheLookupEntirelyWhenNoRepositoryIsWired() {
        // The default MultiAssetRiskEngineTest setup (no ReflectionTestUtils.setField) -- proves
        // the null-guard means the engine still works exactly as before when unwired.
        BlockchainAnalyticsClient blockchain = new BlockchainAnalyticsClient(
                false, "TEST_PROVIDER", "http://localhost:1", "/screen", "", Duration.ofMillis(50));
        MultiAssetRiskEngine unwiredEngine = new MultiAssetRiskEngine(blockchain, BigDecimal.valueOf(2_000),
                GLOBAL_DEFAULT, BigDecimal.valueOf(5), "GAMBLING,CRYPTO_OTC", 2, 3, BigDecimal.valueOf(10_000));
        MultiAssetCustomer plainCustomer = new MultiAssetCustomer();
        plainCustomer.setExternalCustomerId("CUST-2");
        plainCustomer.setCountryCode("KE");
        plainCustomer.setPspId(7L);

        MultiAssetRiskEngine.Assessment result = unwiredEngine.assess(plainCustomer, null, null,
                cryptoTransfer(BigDecimal.valueOf(1_500)), List.of());

        // $1,500 is above the global default -> INCOMPLETE, using the global default exactly as
        // before this fix, with no NPE from the unwired repository field.
        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.INCOMPLETE);
    }
}
