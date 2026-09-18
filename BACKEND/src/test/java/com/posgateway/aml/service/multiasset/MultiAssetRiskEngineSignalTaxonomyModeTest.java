package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.IngestTransactionRequest;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.psp.SignalTaxonomyMode;
import com.posgateway.aml.repository.PspRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MultiAssetRiskEngineSignalTaxonomyModeTest {
    @Test
    void reportingOnlyRetainsSignalsWithoutChangingDecision() {
        BlockchainAnalyticsClient blockchain = new BlockchainAnalyticsClient(
                false, "TEST", "http://localhost:1", "/screen", "", Duration.ofMillis(10));
        MultiAssetRiskEngine engine = new MultiAssetRiskEngine(blockchain, BigDecimal.valueOf(2000),
                BigDecimal.valueOf(1000), BigDecimal.valueOf(5), "GAMBLING", 2, 3,
                BigDecimal.valueOf(10000));
        Psp psp = new Psp();
        psp.setPspId(9L);
        psp.setSignalTaxonomyMode(SignalTaxonomyMode.REPORTING_ONLY);
        PspRepository psps = mock(PspRepository.class);
        when(psps.findById(9L)).thenReturn(Optional.of(psp));
        ReflectionTestUtils.setField(engine, "pspRepository", psps);
        MultiAssetCustomer customer = new MultiAssetCustomer();
        customer.setPspId(9L);
        customer.setExternalCustomerId("C-1");
        IngestTransactionRequest request = new IngestTransactionRequest(
                "T-1", 9L, null, null, AssetClass.E_MONEY, MultiAssetTransactionType.TOP_UP,
                BigDecimal.valueOf(500), null, "KES", null, null, null, LocalDateTime.now(),
                "KE", null, null, "M-1", null, null, null, null, null, null, null,
                "GAMBLING", Map.of(), ProductDomain.E_MONEY_MOBILE_MONEY);

        var assessment = engine.assess(customer, null, null, request, List.of());

        assertThat(assessment.signals()).isNotEmpty();
        assertThat(assessment.riskScore()).isZero();
        assertThat(assessment.decision()).isEqualTo(RiskDecision.ALLOW);
    }
}
