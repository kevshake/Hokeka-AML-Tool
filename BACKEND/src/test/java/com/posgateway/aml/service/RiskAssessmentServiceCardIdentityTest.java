package com.posgateway.aml.service;

import com.posgateway.aml.service.security.PiiLookupHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The legacy-model adapter never forwards a raw card number into pan_hash. */
class RiskAssessmentServiceCardIdentityTest {

    private static RiskAssessmentService service(PiiLookupHasher hasher) {
        return new RiskAssessmentService(mock(AmlService.class), mock(FraudDetectionService.class),
                mock(com.posgateway.aml.service.risk.RiskRulesEngine.class),
                mock(com.posgateway.aml.repository.MerchantRepository.class),
                mock(com.posgateway.aml.repository.BeneficialOwnerRepository.class),
                mock(com.posgateway.aml.service.analytics.LinkAnalysisService.class),
                mock(com.posgateway.aml.service.analytics.BehavioralProfilingService.class),
                mock(com.posgateway.aml.service.compliance.CashStructuringDetectionService.class),
                hasher);
    }

    @Test
    void tokenisedIdentifierPassesThrough() {
        assertThat(service(new PiiLookupHasher("")).cardIdentityKey("acct-token-123")).isEqualTo("acct-token-123");
    }

    @Test
    void rawPanIsHashedWithTheIngestHmacWhenConfigured() {
        PiiLookupHasher hasher = new PiiLookupHasher("unit-test-pii-lookup-key-0123456789abcdef");
        assertThat(service(hasher).cardIdentityKey("4111111111111111"))
                .isEqualTo(hasher.hashIdentifier("4111111111111111"));
    }

    @Test
    void rawPanIsDroppedWhenNoHmacKey() {
        assertThat(service(new PiiLookupHasher("")).cardIdentityKey("4111111111111111")).isNull();
    }
}
