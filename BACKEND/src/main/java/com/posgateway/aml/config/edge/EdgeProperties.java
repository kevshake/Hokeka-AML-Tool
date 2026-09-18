package com.posgateway.aml.config.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Control-plane configuration for the edge channel
 * ({@code docs/architecture/edge-channel-contract.md}).
 */
@Component
@ConfigurationProperties(prefix = "edge")
public class EdgeProperties {

    private final Bundle bundle = new Bundle();
    private final Metrics metrics = new Metrics();
    private final Mtls mtls = new Mtls();

    public Bundle getBundle() { return bundle; }
    public Metrics getMetrics() { return metrics; }
    public Mtls getMtls() { return mtls; }

    public static class Metrics {

        /**
         * Base64 PKCS#8 X25519 private key the control plane uses to open metrics envelopes sealed
         * by edges. Sourced from {@code EDGE_METRICS_INGEST_KEY}. Blank → ephemeral (dev only).
         */
        private String ingestKey = "";

        /** Optional matching public key; see {@link Bundle#getSigningPublicKey()}. */
        private String ingestPublicKey = "";

        /** Hard cap on a sealed metrics upload, in bytes. Anything larger is refused unread. */
        private int maxPayloadBytes = 262_144;

        public String getIngestKey() { return ingestKey; }
        public void setIngestKey(String ingestKey) { this.ingestKey = ingestKey; }

        public String getIngestPublicKey() { return ingestPublicKey; }
        public void setIngestPublicKey(String ingestPublicKey) { this.ingestPublicKey = ingestPublicKey; }

        public int getMaxPayloadBytes() { return maxPayloadBytes; }
        public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    }

    public static class Bundle {

        /**
         * Base64 (standard alphabet) of the control plane's Ed25519 signing key in PKCS#8 form.
         * Sourced from {@code EDGE_BUNDLE_SIGNING_KEY}. When blank, an ephemeral key is generated
         * at startup and a loud warning is logged — usable for local development only, because a
         * restart invalidates every edge's pinned copy of the public key.
         */
        private String signingKey = "";

        /**
         * Optional. Base64 of the matching Ed25519 public key — either an X.509
         * SubjectPublicKeyInfo (44 bytes) or the bare RFC-8032 32-byte encoding. Only required
         * when {@link #signingKey} is a PKCS#8 <i>v1</i> blob, which carries no public key.
         */
        private String signingPublicKey = "";

        public String getSigningKey() { return signingKey; }
        public void setSigningKey(String signingKey) { this.signingKey = signingKey; }

        public String getSigningPublicKey() { return signingPublicKey; }
        public void setSigningPublicKey(String signingPublicKey) { this.signingPublicKey = signingPublicKey; }
    }

    public static class Mtls {

        /**
         * Trust forwarded client-certificate headers ({@link #verifyHeader} /
         * {@link #subjectDnHeader}) when — and only when — the request arrives from a
         * {@link #trustedProxies trusted proxy}. Set false to require a real, JVM-terminated
         * client certificate.
         */
        private boolean forwardedClientCertEnabled = true;

        /**
         * Remote addresses permitted to assert forwarded client-certificate headers. Anything else
         * presenting those headers is ignored entirely, so a public client cannot spoof an edge
         * identity by adding headers.
         */
        private List<String> trustedProxies = new ArrayList<>(
                List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));

        /** nginx {@code ssl_client_verify}; must equal {@link #verifySuccessValue}. */
        private String verifyHeader = "X-SSL-Client-Verify";

        private String verifySuccessValue = "SUCCESS";

        /** nginx {@code ssl_client_s_dn} — the verified client certificate's subject DN. */
        private String subjectDnHeader = "X-SSL-Client-S-DN";

        public boolean isForwardedClientCertEnabled() { return forwardedClientCertEnabled; }
        public void setForwardedClientCertEnabled(boolean forwardedClientCertEnabled) {
            this.forwardedClientCertEnabled = forwardedClientCertEnabled;
        }

        public List<String> getTrustedProxies() { return trustedProxies; }
        public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }

        public String getVerifyHeader() { return verifyHeader; }
        public void setVerifyHeader(String verifyHeader) { this.verifyHeader = verifyHeader; }

        public String getVerifySuccessValue() { return verifySuccessValue; }
        public void setVerifySuccessValue(String verifySuccessValue) {
            this.verifySuccessValue = verifySuccessValue;
        }

        public String getSubjectDnHeader() { return subjectDnHeader; }
        public void setSubjectDnHeader(String subjectDnHeader) { this.subjectDnHeader = subjectDnHeader; }
    }
}
