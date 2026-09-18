package com.hokeka.edge.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration of the outbound control-plane channel (the only network path off the PSP premises).
 *
 * <p>Everything here is deployment-supplied; nothing has a permissive default. The pinned
 * control-plane keys are trust anchors — the edge accepts a rule bundle only if it is signed by
 * {@link #getSigningPublicKey()} and seals metrics only to {@link #getEncryptionPublicKey()}.
 */
@ConfigurationProperties(prefix = "hokeka.controlplane")
public class ControlPlaneProperties {

    /** Master switch for the outbound channel (poller + shipper + activation). */
    private boolean enabled = true;

    /** Control-plane base URL. Must be https:// — plaintext is rejected outright. */
    private String baseUrl = "";

    /** This node's identity, as registered during enrollment. */
    private String edgeId = "";
    private String pspId = "";

    /** Single-use operator-supplied enrollment code (24 h TTL) used on first boot only. */
    private String enrollmentCode = "";

    private Duration bundlePollInterval = Duration.ofSeconds(15);
    private Duration metricsInterval = Duration.ofSeconds(60);
    private Duration requestTimeout = Duration.ofSeconds(20);

    /** mTLS client identity presented to the control plane (PKCS#12). */
    private String keystore = "";
    private String keystorePassword = "";
    private String keystoreType = "PKCS12";

    /** Trust anchors for the control-plane server certificate. Empty = the JDK default trust store. */
    private String truststore = "";
    private String truststorePassword = "";
    private String truststoreType = "PKCS12";

    /** Pinned control-plane Ed25519 public key (base64 SPKI or raw 32 bytes) — verifies bundles. */
    private String signingPublicKey = "";

    /** Pinned control-plane X25519 public key (base64 SPKI or raw 32 bytes) — seals metrics. */
    private String encryptionPublicKey = "";

    /** Where this edge's own X25519 + Ed25519 private keys live (mode 600, generated at first boot). */
    private String identityFile = "/opt/hokeka/secrets/edge-identity.json";

    /** Replay-guard window and nonce cache size (contract §4: ≥ 10 000 entries, 300 s skew). */
    private Duration maxClockSkew = Duration.ofSeconds(300);
    private int nonceCacheSize = 10_000;

    /** Metrics reports buffered on failure before the oldest is dropped (store-and-forward). */
    private int metricsBufferSize = 240;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getPspId() {
        return pspId;
    }

    public void setPspId(String pspId) {
        this.pspId = pspId;
    }

    public String getEnrollmentCode() {
        return enrollmentCode;
    }

    public void setEnrollmentCode(String enrollmentCode) {
        this.enrollmentCode = enrollmentCode;
    }

    public Duration getBundlePollInterval() {
        return bundlePollInterval;
    }

    public void setBundlePollInterval(Duration bundlePollInterval) {
        this.bundlePollInterval = bundlePollInterval;
    }

    public Duration getMetricsInterval() {
        return metricsInterval;
    }

    public void setMetricsInterval(Duration metricsInterval) {
        this.metricsInterval = metricsInterval;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getTruststore() {
        return truststore;
    }

    public void setTruststore(String truststore) {
        this.truststore = truststore;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public String getTruststoreType() {
        return truststoreType;
    }

    public void setTruststoreType(String truststoreType) {
        this.truststoreType = truststoreType;
    }

    public String getSigningPublicKey() {
        return signingPublicKey;
    }

    public void setSigningPublicKey(String signingPublicKey) {
        this.signingPublicKey = signingPublicKey;
    }

    public String getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public void setEncryptionPublicKey(String encryptionPublicKey) {
        this.encryptionPublicKey = encryptionPublicKey;
    }

    public String getIdentityFile() {
        return identityFile;
    }

    public void setIdentityFile(String identityFile) {
        this.identityFile = identityFile;
    }

    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }

    public void setMaxClockSkew(Duration maxClockSkew) {
        this.maxClockSkew = maxClockSkew;
    }

    public int getNonceCacheSize() {
        return nonceCacheSize;
    }

    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }

    public int getMetricsBufferSize() {
        return metricsBufferSize;
    }

    public void setMetricsBufferSize(int metricsBufferSize) {
        this.metricsBufferSize = metricsBufferSize;
    }
}
