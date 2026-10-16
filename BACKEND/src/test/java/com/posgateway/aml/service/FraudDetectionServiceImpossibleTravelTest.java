package com.posgateway.aml.service;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.config.FraudProperties;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.VelocityRuleRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.enrichment.IpGeoService;
import com.posgateway.aml.service.enrichment.IpReputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudDetectionServiceImpossibleTravelTest {

    private FeatureCacheService featureCacheService;
    private FraudDetectionService service;

    /** Creates the mocks and service fixture used by each test. */
    @BeforeEach
    void setUp() {
        featureCacheService = mock(FeatureCacheService.class);
        IpReputationService ipReputation = new IpReputationService();
        ReflectionTestUtils.setField(ipReputation, "enabled", true);
        ipReputation.refreshRanges();
        service = new FraudDetectionService(
                mock(FraudProperties.class),
                mock(TransactionRepository.class),
                featureCacheService,
                mock(HighRiskCountryRepository.class),
                mock(VelocityRuleRepository.class),
                mock(IpGeoService.class),
                ipReputation,
                mock(AmlMicroserviceClient.class),
                "KP");
    }

    /** Verifies missing coordinates skip impossible travel and do not hold. */
    @Test
    void missingCoordinatesSkipImpossibleTravelAndDoNotHold() {
        Transaction txn = cardTxn("8.8.8.8", null, null, LocalDateTime.now());
        List<String> factors = new ArrayList<>();

        ReflectionTestUtils.invokeMethod(service, "assessIpRisk", txn, factors);

        assertFalse(factors.stream().anyMatch(f -> f.toUpperCase().contains("HOLD")));
        assertFalse(factors.stream().anyMatch(f -> f.contains("impossible travel")));
    }

    /** Verifies both points impossible travel mirrors mobile money signal. */
    @Test
    void bothPointsImpossibleTravelMirrorsMobileMoneySignal() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 12, 0);
        when(featureCacheService.getLastLocation("acc-1")).thenReturn(Optional.of(
                new FeatureCacheService.GeoPoint(-1.2921, 36.8219, now.minusHours(1))));
        Transaction txn = cardTxn("8.8.8.8", new BigDecimal("40.7128"), new BigDecimal("-74.0060"), now);
        List<String> factors = new ArrayList<>();

        Integer score = ReflectionTestUtils.invokeMethod(service, "assessIpRisk", txn, factors);

        assertTrue(score != null && score >= 45);
        assertTrue(factors.stream().anyMatch(f -> f.contains("impossible travel")));
        assertFalse(factors.stream().anyMatch(f -> f.toUpperCase().contains("HOLD")));
    }

    /** Builds test data for card txn. */
    private static Transaction cardTxn(String ip, BigDecimal lat, BigDecimal lon, LocalDateTime at) {
        Transaction txn = new Transaction();
        txn.setTransactionId("txn-travel");
        txn.setAccountNumber("acc-1");
        txn.setIpAddress(ip);
        txn.setLatitude(lat);
        txn.setLongitude(lon);
        txn.setTransactionTimestamp(at);
        return txn;
    }
}
