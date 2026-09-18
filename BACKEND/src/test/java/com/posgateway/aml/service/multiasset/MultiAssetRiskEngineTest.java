package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.IngestTransactionRequest;
import com.posgateway.aml.entity.multiasset.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAssetRiskEngineTest {

    private MultiAssetRiskEngine engine;
    private MultiAssetCustomer customer;

    @BeforeEach
    void setUp() {
        BlockchainAnalyticsClient blockchain = new BlockchainAnalyticsClient(
                false, "TEST_PROVIDER", "http://localhost:1", "/screen", "", Duration.ofMillis(50));
        engine = new MultiAssetRiskEngine(blockchain, BigDecimal.valueOf(2_000),
                BigDecimal.valueOf(1_000), BigDecimal.valueOf(5), "GAMBLING,CRYPTO_OTC",
                2, 3, BigDecimal.valueOf(10_000));
        customer = new MultiAssetCustomer();
        customer.setExternalCustomerId("CUST-1");
        customer.setCountryCode("KE");
    }

    @Test
    void detectsMatchedSecuritiesOrdersAndRapidLiquidation() {
        LocalDateTime now = LocalDateTime.now();
        MultiAssetTransaction buy = transaction(AssetClass.SECURITIES, MultiAssetTransactionType.BUY,
                "ABC", "BROKER-9", BigDecimal.valueOf(12_000), now.minusMinutes(20));
        IngestTransactionRequest sell = request(AssetClass.SECURITIES, MultiAssetTransactionType.SELL,
                BigDecimal.valueOf(12_000), BigDecimal.valueOf(12_000), "USD", "ABC",
                BigDecimal.valueOf(2), now, "BROKER-9", null, null, null, null, null);

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, sell, List.of(buy));

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("SECURITIES_RAPID_LIQUIDATION", "SECURITIES_MATCHED_ORDER",
                        "SECURITIES_LOW_PRICE_HIGH_VALUE");
        assertThat(result.signals()).filteredOn(signal -> signal.code().equals("SECURITIES_MATCHED_ORDER"))
                .extracting(MultiAssetRiskEngine.SignalDraft::signalType)
                .containsOnly(FinancialCrimeSignalType.MARKET_ABUSE);
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    @Test
    void detectsStructuredEMoneyVelocityAndContextRisk() {
        LocalDateTime now = LocalDateTime.now();
        List<MultiAssetTransaction> history = List.of(
                transaction(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP, null, null,
                        BigDecimal.valueOf(450), now.minusHours(1)),
                transaction(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP, null, null,
                        BigDecimal.valueOf(450), now.minusHours(2)),
                transaction(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP, null, null,
                        BigDecimal.valueOf(450), now.minusHours(3)),
                transaction(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP, null, null,
                        BigDecimal.valueOf(450), now.minusHours(4)));
        history.forEach(tx -> tx.setDeviceFingerprint("KNOWN-DEVICE"));
        IngestTransactionRequest topUp = request(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP,
                BigDecimal.valueOf(450), null, "KES", null, null, now, "MERCHANT-1",
                "UG", "NEW-DEVICE", "GAMBLING", null, null);

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, topUp, history);

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("EMONEY_DAILY_VELOCITY", "EMONEY_STRUCTURED_TOPUPS",
                        "EMONEY_GEOLOCATION_MISMATCH", "EMONEY_NEW_DEVICE", "EMONEY_HIGH_RISK_MERCHANT");
        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void failsClosedWhenCryptoProviderIsUnavailableAndTravelRuleDataIsMissing() {
        IngestTransactionRequest transfer = request(AssetClass.CRYPTO, MultiAssetTransactionType.TRANSFER,
                BigDecimal.ONE, BigDecimal.valueOf(2_500), "BTC", "BTC", null,
                LocalDateTime.now(), "bc1-counterparty", null, null, null, "bitcoin", "ethereum");

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, transfer, List.of());

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("CRYPTO_SCREENING_UNAVAILABLE", "CRYPTO_CROSS_CHAIN_TRANSFER",
                        "CRYPTO_TRAVEL_RULE_INCOMPLETE");
        assertThat(result.signals()).filteredOn(signal -> signal.code().equals("CRYPTO_SCREENING_UNAVAILABLE"))
                .extracting(MultiAssetRiskEngine.SignalDraft::signalType)
                .containsOnly(FinancialCrimeSignalType.CRYPTO_EXPOSURE);
        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.INCOMPLETE);
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.cryptoScreening()).isNotNull();
        assertThat(result.cryptoScreening().address()).isEqualTo("bc1-counterparty");
        assertThat(result.cryptoScreening().result().available()).isFalse();
    }

    @Test
    void travelRuleDataRemainsPendingUntilSecureWorkflowCompletes() {
        IngestTransactionRequest transfer = new IngestTransactionRequest(
                "TX-TRAVEL-1", 1L, null, null, AssetClass.CRYPTO, MultiAssetTransactionType.TRANSFER,
                BigDecimal.ONE, BigDecimal.valueOf(2_500), "BTC", "BTC", null, null,
                LocalDateTime.now(), "KE", null, null, "bc1-beneficiary", null,
                "bitcoin", "bitcoin", "Alice", "A-1", "Bob", "B-1", null, Map.of(),
                ProductDomain.VIRTUAL_ASSET);

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, transfer, List.of());

        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.PENDING_VERIFICATION);
    }

    @Test
    void linksRapidMovementAcrossAssetClasses() {
        MultiAssetTransaction bankingDeposit = transaction(AssetClass.BANKING, MultiAssetTransactionType.DEPOSIT,
                null, "BANK-1", BigDecimal.valueOf(3_000), LocalDateTime.now().minusHours(2));
        IngestTransactionRequest crypto = request(AssetClass.CRYPTO, MultiAssetTransactionType.TRANSFER,
                BigDecimal.valueOf(1_500), BigDecimal.valueOf(1_500), "USDC", "USDC", null,
                LocalDateTime.now(), "0xabc", null, null, null, "ethereum", "ethereum");

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, crypto,
                List.of(bankingDeposit));

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("CROSS_ASSET_RAPID_MOVEMENT");
    }

    @Test
    void detectsRapidPrepaidRedemptionAndMissingProgrammeEvidence() {
        LocalDateTime now = LocalDateTime.now();
        MultiAssetTransaction load = transaction(AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP,
                null, null, BigDecimal.valueOf(1_000), now.minusMinutes(30));
        load.setProductDomain(ProductDomain.PREPAID_CLOSED_LOOP);
        IngestTransactionRequest redemption = new IngestTransactionRequest(
                "TX-PREPAID-1", 1L, null, null, AssetClass.E_MONEY, MultiAssetTransactionType.WITHDRAWAL,
                BigDecimal.valueOf(900), null, "KES", null, null, null, now, "KE", null, null,
                "CASH-AGENT", null, null, null, null, null, null, null, null, Map.of(),
                ProductDomain.PREPAID_CLOSED_LOOP);

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, redemption, List.of(load));

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("PREPAID_RAPID_LOAD_REDEMPTION", "PREPAID_PROGRAM_UNIDENTIFIED");
        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
    }

    @Test
    void tokenizedFiatUsesIssuerAndLedgerControlsWithoutCryptoScreening() {
        IngestTransactionRequest transfer = new IngestTransactionRequest(
                "TX-CBDC-1", 1L, null, null, AssetClass.TOKENIZED_FIAT, MultiAssetTransactionType.TRANSFER,
                BigDecimal.valueOf(12_000), BigDecimal.valueOf(12_000), "USD", "DIGITAL_USD", null, null,
                LocalDateTime.now(), "UG", null, null, "CBDC-BENEFICIARY", null, null, null,
                null, null, null, null, null, Map.of("issuerVerified", false),
                ProductDomain.TOKENIZED_FIAT_CBDC);

        MultiAssetRiskEngine.Assessment result = engine.assess(customer, null, null, transfer, List.of());

        assertThat(result.signals()).extracting(MultiAssetRiskEngine.SignalDraft::code)
                .contains("TOKENIZED_FIAT_ISSUER_UNVERIFIED", "TOKENIZED_FIAT_LEDGER_REFERENCE_MISSING",
                        "TOKENIZED_FIAT_DAILY_VELOCITY", "TOKENIZED_FIAT_CROSS_BORDER")
                .doesNotContain("CRYPTO_SCREENING_UNAVAILABLE", "CRYPTO_TRAVEL_RULE_INCOMPLETE");
        assertThat(result.travelRuleStatus()).isEqualTo(TravelRuleStatus.NOT_REQUIRED);
        assertThat(result.cryptoScreening()).isNull();
        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    private MultiAssetTransaction transaction(AssetClass assetClass, MultiAssetTransactionType type,
            String symbol, String counterparty, BigDecimal amount, LocalDateTime executedAt) {
        MultiAssetTransaction transaction = new MultiAssetTransaction();
        transaction.setAssetClass(assetClass);
        transaction.setProductDomain(ProductDomain.defaultFor(assetClass));
        transaction.setTransactionType(type);
        transaction.setAssetSymbol(symbol);
        transaction.setCounterpartyReference(counterparty);
        transaction.setAmount(amount);
        transaction.setCurrency(assetClass == AssetClass.E_MONEY ? "KES" : "USD");
        transaction.setFiatEquivalentUsd(amount);
        transaction.setExecutedAt(executedAt);
        return transaction;
    }

    private IngestTransactionRequest request(AssetClass assetClass, MultiAssetTransactionType type,
            BigDecimal amount, BigDecimal usd, String currency, String symbol, BigDecimal unitPrice,
            LocalDateTime executedAt, String counterparty, String country, String device,
            String merchantCategory, String sourceNetwork, String destinationNetwork) {
        return new IngestTransactionRequest("TX-1", 1L, null, null, assetClass, type, amount, usd,
                currency, symbol, null, unitPrice, executedAt, country, device, null, counterparty,
                null, sourceNetwork, destinationNetwork, null, null, null, null, merchantCategory, Map.of(), null);
    }
}
