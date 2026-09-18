package com.posgateway.aml.service.risk;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.repository.*;
import com.posgateway.aml.repository.merchant.MerchantRiskScoreRepository;
import com.posgateway.aml.repository.risk.*;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.service.rules.RulesExecutionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskScoringServiceMlBlendTest {
    @Test
    void mlScoreParticipatesInConfigurableOverallBlend() {
        RulesExecutionService rules = mock(RulesExecutionService.class);
        when(rules.evaluateRules(anyLong(), anyMap(), anyDouble())).thenReturn(
                new RuleEvaluationResult(1L, "ALLOW", List.of(), List.of(), false, false, 0, 1));
        RiskScoringService service = new RiskScoringService(new SimpleMeterRegistry(), rules,
                mock(CountryRiskRepository.class), mock(MerchantRepository.class),
                mock(HighRiskCountryRepository.class), mock(BeneficialOwnerRepository.class),
                mock(TransactionRepository.class), mock(AlertRepository.class),
                mock(MerchantRiskScoreRepository.class), mock(AmlMicroserviceClient.class),
                mock(MccRiskConfig.class));
        ReflectionTestUtils.setField(service, "wOriginCountry", .3);
        ReflectionTestUtils.setField(service, "wDestCountry", .3);
        ReflectionTestUtils.setField(service, "wAmount", .4);
        ReflectionTestUtils.setField(service, "overallKrsWeight", 0d);
        ReflectionTestUtils.setField(service, "overallTrsWeight", 0d);
        ReflectionTestUtils.setField(service, "overallCraWeight", 0d);
        ReflectionTestUtils.setField(service, "overallMlWeight", 1d);

        Map<String, Object> result = service.calculateOverallRisk("1",
                Map.of("amount", 0), Map.of("mlScore", 87.5));

        assertEquals(87.5, (double) result.get("finalScore"), .0001);
    }
}
