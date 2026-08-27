package com.posgateway.aml.service.risk;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.merchant.MerchantRiskScoreRepository;
import com.posgateway.aml.repository.risk.CountryRiskRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.rules.RulesExecutionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Verifies the fix for W19-3: TRS/KRS/CRA weights were hardcoded static final constants,
 * requiring a redeploy to tune the risk model. Externalized via @Value; this test proves the
 * weights actually flow into the calculation (not just that the defaults preserve prior behavior).
 */
class RiskScoringServiceWeightsTest {

    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        service = new RiskScoringService(
                new SimpleMeterRegistry(),
                mock(RulesExecutionService.class),
                mock(CountryRiskRepository.class),
                mock(MerchantRepository.class),
                mock(HighRiskCountryRepository.class),
                mock(BeneficialOwnerRepository.class),
                mock(TransactionRepository.class),
                mock(AlertRepository.class),
                mock(MerchantRiskScoreRepository.class),
                mock(AmlMicroserviceClient.class),
                mock(MccRiskConfig.class));
    }

    @Test
    void defaultWeightsPreserveThePriorHardcodedTrsBehavior() {
        ReflectionTestUtils.setField(service, "wOriginCountry", 0.3);
        ReflectionTestUtils.setField(service, "wDestCountry", 0.3);
        ReflectionTestUtils.setField(service, "wAmount", 0.4);

        // Unknown countries (null) fall back to NEUTRAL_RISK=50.0; null amount -> 0.0 amount risk.
        // weightedSum = 50*0.3 + 50*0.3 + 0*0.4 = 30; sumWeights = 1.0 -> trs = 30.0.
        Double trs = service.calculateTrs(null, null, null);
        assertEquals(30.0, trs, 0.0001);
    }

    @Test
    void configuredWeightsActuallyChangeTheTrsOutput() {
        // Extreme weighting: only the amount component counts.
        ReflectionTestUtils.setField(service, "wOriginCountry", 0.0);
        ReflectionTestUtils.setField(service, "wDestCountry", 0.0);
        ReflectionTestUtils.setField(service, "wAmount", 1.0);

        // amount=750 -> calculateAmountRisk band <1000 -> 10.0. With all weight on amount, trs must
        // equal exactly that, proving the configured weight (not the old hardcoded 0.4) drives it.
        Double trs = service.calculateTrs(null, null, new BigDecimal("750"));
        assertEquals(10.0, trs, 0.0001);
    }
}
