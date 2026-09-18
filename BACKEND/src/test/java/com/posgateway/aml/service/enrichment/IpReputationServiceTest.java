package com.posgateway.aml.service.enrichment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario coverage for IP-manipulation and anonymised-connection (VPN / proxy / datacenter)
 * detection — the checks a PSP-fed transaction must pass for its customer/PSP IP to be trusted.
 */
class IpReputationServiceTest {

    private IpReputationService service;

    @BeforeEach
    void setUp() {
        service = new IpReputationService();
        ReflectionTestUtils.setField(service, "enabled", true);
        // Two illustrative VPN/Tor-exit/datacenter ranges (ops supply real threat-intel in prod).
        ReflectionTestUtils.setField(service, "anonymizingCidrsRaw", "45.83.0.0/16,185.220.100.0/22");
        service.refreshRanges();
    }

    @Test
    void publicResidentialIpIsClean() {
        IpReputationService.IpAssessment a = service.assess("8.8.8.8", null, null);
        assertTrue(a.present());
        assertFalse(a.manipulated(), "a normal public IP must not be flagged");
        assertEquals("PUBLIC", a.category());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",       // loopback
            "10.0.0.5",        // RFC-1918
            "192.168.1.10",    // RFC-1918
            "172.16.5.4",      // RFC-1918
            "169.254.10.10",   // link-local
            "100.64.1.1",      // carrier-grade NAT
            "0.0.0.0",         // wildcard
            "224.0.0.1"        // multicast
    })
    void privateOrReservedIpsAreFlaggedAsManipulated(String ip) {
        IpReputationService.IpAssessment a = service.assess(ip, null, null);
        assertTrue(a.privateOrReserved(), ip + " should be private/reserved");
        assertTrue(a.manipulated(), ip + " should count as manipulated/masked");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-ip", "999.999.1.1", "12.34", "256.1.1.1", "1.2.3.4.5", "", "   "})
    void malformedOrMissingIpsAreHandled(String ip) {
        IpReputationService.IpAssessment a = service.assess(ip.isBlank() ? ip : ip, null, null);
        if (ip.isBlank()) {
            assertFalse(a.present());
        } else {
            assertTrue(a.malformed(), ip + " should be malformed");
            assertTrue(a.manipulated());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"45.83.0.1", "45.83.200.17", "185.220.101.5"})
    void vpnProxyDatacenterRangesAreFlaggedAnonymizing(String ip) {
        IpReputationService.IpAssessment a = service.assess(ip, null, null);
        assertTrue(a.anonymizing(), ip + " is in a configured VPN/proxy range");
        assertTrue(a.manipulated());
        assertEquals("ANONYMIZING", a.category());
    }

    @Test
    void publicIpOutsideAnonymizingRangesIsNotAnonymizing() {
        assertFalse(service.assess("44.0.0.1", null, null).anonymizing());
        assertFalse(service.assess("186.0.0.1", null, null).anonymizing());
    }

    @ParameterizedTest
    @CsvSource({
            "8.8.8.8, KE, US, true",   // customer claims Kenya, connects from US → mismatch
            "8.8.8.8, US, US, false",  // aligned
            "8.8.8.8, us, US, false",  // case-insensitive
            "8.8.8.8, KE, , false",    // unknown IP country → no mismatch asserted
            "8.8.8.8, , US, false"     // no declared country → no mismatch asserted
    })
    void geoMismatchIsDetectedWhenBothCountriesKnown(String ip, String declared, String ipGeo, boolean expected) {
        IpReputationService.IpAssessment a = service.assess(ip, blankToNull(declared), blankToNull(ipGeo));
        assertEquals(expected, a.geoMismatch());
    }

    @Test
    void ipv6LoopbackIsReserved() {
        assertTrue(service.assess("::1", null, null).privateOrReserved());
    }

    @Test
    void disabledServiceFlagsNothing() {
        ReflectionTestUtils.setField(service, "enabled", false);
        IpReputationService.IpAssessment a = service.assess("10.0.0.1", "KE", "US");
        assertFalse(a.manipulated());
        assertFalse(a.geoMismatch());
    }

    @Test
    void backtestManyIpsIsStableAcrossRepeatedPasses() {
        // Re-run the same battery several times: results must be deterministic (no flakiness,
        // no external dependency, no DNS).
        String[] clean = {"8.8.8.8", "1.1.1.1", "44.0.0.1"};
        String[] dirty = {"10.0.0.1", "127.0.0.1", "45.83.5.5", "999.1.1.1"};
        for (int pass = 0; pass < 25; pass++) {
            for (String ip : clean) {
                assertFalse(service.assess(ip, null, null).manipulated(), ip + " pass " + pass);
            }
            for (String ip : dirty) {
                assertTrue(service.assess(ip, null, null).manipulated(), ip + " pass " + pass);
            }
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
