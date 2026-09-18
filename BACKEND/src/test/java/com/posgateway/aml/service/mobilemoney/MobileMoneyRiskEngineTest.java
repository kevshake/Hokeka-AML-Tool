package com.posgateway.aml.service.mobilemoney;

import com.posgateway.aml.dto.mobilemoney.MobileMoneyDtos.IngestMobileMoneyRequest;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyEventType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyTransactionContext;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import com.posgateway.aml.entity.multiasset.MultiAssetTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MobileMoneyRiskEngineTest {
    private MobileMoneyRiskEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MobileMoneyRiskEngine(BigDecimal.valueOf(1_000), 3, 2,
                30, 30, 500, 0, 5);
    }

    @Test
    void detectsSharedControlAndCrossWalletStructuringFromObservedTelemetry() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        List<MobileMoneyTransactionContext> history = List.of(
                context("WALLET-1", "DEVICE-1", "0123456789abcdef", MobileMoneyEventType.CASH_IN,
                        "CP-1", BigDecimal.valueOf(100), now.minusMinutes(10), null, null),
                context("WALLET-2", "DEVICE-1", "0123456789abcdef", MobileMoneyEventType.CASH_IN,
                        "CP-2", BigDecimal.valueOf(100), now.minusMinutes(9), null, null),
                context("WALLET-1", "DEVICE-1", "0123456789abcdef", MobileMoneyEventType.CASH_IN,
                        "CP-3", BigDecimal.valueOf(100), now.minusMinutes(8), null, null),
                context("WALLET-2", "DEVICE-1", "0123456789abcdef", MobileMoneyEventType.CASH_IN,
                        "CP-4", BigDecimal.valueOf(100), now.minusMinutes(7), null, null));

        MobileMoneyRiskEngine.Assessment result = engine.assess(request(
                MultiAssetTransactionType.TOP_UP, MobileMoneyEventType.CASH_IN,
                "WALLET-3", "CP-5", "DEVICE-1", "0123456789abcdef",
                BigDecimal.valueOf(100), now, null, null, null, null, null), history);

        assertThat(result.signals()).extracting(MobileMoneyRiskEngine.SignalDraft::code)
                .contains("MOBILE_SHARED_DEVICE_MULTIPLE_WALLETS",
                        "MOBILE_SHARED_SIM_MULTIPLE_WALLETS", "MOBILE_MULTI_WALLET_STRUCTURING");
        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void detectsRecentSimSwapNewDeviceAndDormantWalletReactivation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 14, 0);
        MobileMoneyTransactionContext prior = context("WALLET-1", "OLD-DEVICE", "old-sim-0123456789",
                MobileMoneyEventType.P2P_TRANSFER, "CP-1", BigDecimal.valueOf(50),
                now.minusDays(2), null, null);

        MobileMoneyRiskEngine.Assessment result = engine.assess(request(
                MultiAssetTransactionType.TRANSFER, MobileMoneyEventType.P2P_TRANSFER,
                "WALLET-1", "CP-2", "NEW-DEVICE", "new-sim-0123456789",
                BigDecimal.valueOf(2_000), now, now.minusHours(2), now.minusDays(45),
                null, null, null), List.of(prior));

        assertThat(result.signals()).extracting(MobileMoneyRiskEngine.SignalDraft::code)
                .contains("MOBILE_SIM_SWAP_HIGH_VALUE", "MOBILE_NEW_DEVICE_HIGH_VALUE",
                        "MOBILE_DORMANT_WALLET_REACTIVATION");
    }

    @Test
    void detectsRapidCashOutImpossibleTravelAndAgentDeviceOverlap() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 3, 0);
        MobileMoneyTransactionContext cashIn = context("WALLET-1", "DEVICE-1", null,
                MobileMoneyEventType.CASH_IN, null, BigDecimal.valueOf(1_000),
                now.minusMinutes(20), BigDecimal.valueOf(-1.2921), BigDecimal.valueOf(36.8219));

        MobileMoneyRiskEngine.Assessment result = engine.assess(request(
                MultiAssetTransactionType.WITHDRAWAL, MobileMoneyEventType.CASH_OUT,
                "WALLET-1", null, "DEVICE-1", null, BigDecimal.valueOf(900), now,
                null, null, "DEVICE-1", BigDecimal.valueOf(4.0435), BigDecimal.valueOf(39.6682)),
                List.of(cashIn));

        assertThat(result.signals()).extracting(MobileMoneyRiskEngine.SignalDraft::code)
                .contains("MOBILE_RAPID_CASH_IN_CASH_OUT", "MOBILE_CUSTOMER_AGENT_SHARED_DEVICE",
                        "MOBILE_UNUSUAL_NIGHT_ACTIVITY", "MOBILE_IMPOSSIBLE_GEO_VELOCITY");
    }

    private IngestMobileMoneyRequest request(MultiAssetTransactionType transactionType,
            MobileMoneyEventType eventType, String wallet, String counterparty, String device,
            String simHash, BigDecimal usdValue, LocalDateTime executedAt, LocalDateTime simChangedAt,
            LocalDateTime previousActivityAt, String agentDevice, BigDecimal latitude, BigDecimal longitude) {
        return new IngestMobileMoneyRequest("TX-NEW", 1L, null, null, transactionType, eventType,
                usdValue, usdValue, "USD", executedAt, "KE", wallet, counterparty, counterparty,
                null, device, agentDevice, simHash, agentDevice == null ? null : "AGENT-1",
                null, null, latitude, longitude, null, null, null, simChangedAt,
                executedAt.minusDays(1), previousActivityAt, null, null, false, Map.of());
    }

    private MobileMoneyTransactionContext context(String wallet, String device, String simHash,
            MobileMoneyEventType eventType, String counterparty, BigDecimal usdValue,
            LocalDateTime executedAt, BigDecimal latitude, BigDecimal longitude) {
        MultiAssetTransaction transaction = new MultiAssetTransaction();
        transaction.setId((long) Math.abs((wallet + executedAt).hashCode()));
        transaction.setAmount(usdValue);
        transaction.setFiatEquivalentUsd(usdValue);
        transaction.setCurrency("USD");
        transaction.setExecutedAt(executedAt);
        MobileMoneyTransactionContext context = new MobileMoneyTransactionContext();
        context.setTransaction(transaction);
        context.setWalletReference(wallet);
        context.setCounterpartyWalletReference(counterparty);
        context.setDeviceFingerprint(device);
        context.setSimIdentifierHash(simHash);
        context.setEventType(eventType);
        context.setExecutedAt(executedAt);
        context.setLatitude(latitude);
        context.setLongitude(longitude);
        return context;
    }
}
