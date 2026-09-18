package com.posgateway.aml.service;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.config.FraudProperties;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.VelocityRuleRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.enrichment.IpGeoService;
import com.posgateway.aml.service.enrichment.IpReputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * End-to-end (service level) check that a transaction arriving with a VPN/proxy, private, or
 * malformed IP is classified correctly by the path {@code TransactionMonitoringService} uses.
 */
class FraudDetectionServiceVpnScenarioTest {

    private FraudDetectionService service;

    @BeforeEach
    void setUp() {
        IpReputationService ipReputation = new IpReputationService();
        ReflectionTestUtils.setField(ipReputation, "enabled", true);
        ReflectionTestUtils.setField(ipReputation, "anonymizingCidrsRaw", "45.83.0.0/16,185.220.100.0/22");
        ipReputation.refreshRanges();

        service = new FraudDetectionService(
                mock(FraudProperties.class),
                mock(TransactionRepository.class),
                mock(FeatureCacheService.class),
                mock(HighRiskCountryRepository.class),
                mock(VelocityRuleRepository.class),
                mock(IpGeoService.class),
                ipReputation,
                mock(AmlMicroserviceClient.class),
                "KP,IR,MM");
    }

    private TransactionEntity txn(String ip) {
        TransactionEntity t = new TransactionEntity();
        t.setIpAddress(ip);
        return t;
    }

    @ParameterizedTest
    @ValueSource(strings = {"45.83.1.1", "185.220.101.9"})
    void vpnProxyRangeIsDetected(String ip) {
        assertTrue(service.detectVpn(txn(ip)), ip + " should be flagged as VPN/proxy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.4", "192.168.0.9", "127.0.0.1", "172.16.0.1"})
    void privateRangesAreNotVpn(String ip) {
        assertFalse(service.detectVpn(txn(ip)), ip + " is private, not a VPN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "44.0.0.1"})
    void cleanPublicIpsAreNotVpn(String ip) {
        assertFalse(service.detectVpn(txn(ip)), ip + " is a clean public IP");
    }

    @Test
    void malformedIpIsSuspicious() {
        assertTrue(service.detectVpn(txn("999.1.1.1")));
        assertTrue(service.detectVpn(txn("not-an-ip")));
    }

    @Test
    void missingIpReturnsNull() {
        assertNull(service.detectVpn(txn(null)));
        assertNull(service.detectVpn(txn("")));
    }

    @Test
    void repeatedBacktestPassesAreStable() {
        for (int pass = 0; pass < 20; pass++) {
            assertTrue(service.detectVpn(txn("45.83.9.9")), "pass " + pass);
            assertFalse(service.detectVpn(txn("8.8.8.8")), "pass " + pass);
            assertFalse(service.detectVpn(txn("10.1.2.3")), "pass " + pass);
        }
    }
}
