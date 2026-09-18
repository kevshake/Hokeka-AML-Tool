package com.hokeka.edge.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The outbound hop refuses plaintext and refuses to drop the client certificate. */
class SecureChannelTest {

    private static ControlPlaneProperties props(String baseUrl) {
        ControlPlaneProperties p = new ControlPlaneProperties();
        p.setBaseUrl(baseUrl);
        p.setEdgeId("acme-eu-1");
        p.setPspId("acme");
        p.setKeystore("");
        return p;
    }

    @Test
    void refusesAPlaintextControlPlaneUrl() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new SecureChannel(props("http://edge.hokeka.com")));
        assertTrue(e.getMessage().contains("must be https://"), e.getMessage());

        assertThrows(IllegalStateException.class, () -> new SecureChannel(props("HTTP://edge.hokeka.com")));
        assertThrows(IllegalStateException.class, () -> new SecureChannel(props("ftp://edge.hokeka.com")));
    }

    @Test
    void acceptsHttpsAndNormalisesTheBaseUrl() {
        SecureChannel channel = new SecureChannel(props("https://edge.hokeka.com/"));
        assertEquals("https://edge.hokeka.com", channel.baseUrl());
    }

    @Test
    void refusesToBuildTheMtlsClientWithoutAClientCertificate() {
        SecureChannel channel = new SecureChannel(props("https://edge.hokeka.com"));
        SecurityException e = assertThrows(SecurityException.class, channel::mutualTlsClient);
        assertTrue(e.getMessage().contains("client certificate"), e.getMessage());
    }

    @Test
    void refusesAKeystorePathThatDoesNotExist() {
        ControlPlaneProperties p = props("https://edge.hokeka.com");
        p.setKeystore("/nonexistent/edge-client.p12");
        SecureChannel channel = new SecureChannel(p);
        assertThrows(SecurityException.class, channel::mutualTlsClient);
    }

    @Test
    void reportsAnUnconfiguredControlPlaneInsteadOfGuessing() {
        SecureChannel channel = new SecureChannel(props(""));
        assertThrows(SecurityException.class, channel::baseUrl);
    }

    @Test
    void offersOnlyTls13AeadSuites() {
        assertEquals(3, SecureChannel.TLS13_CIPHERS.length);
        for (String suite : SecureChannel.TLS13_CIPHERS) {
            assertTrue(suite.startsWith("TLS_AES_") || suite.startsWith("TLS_CHACHA20_"), suite);
        }
    }
}
