package com.hokeka.edge.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * The transport half of the edge→control-plane channel: a {@link RestClient} over TLS 1.3 with a
 * client certificate (mTLS), per contract §1. Nothing else in the edge opens an outbound socket.
 *
 * <p>Fail-closed properties:
 * <ul>
 *   <li>a {@code http://} base URL is refused at startup — there is no plaintext fallback;</li>
 *   <li>only {@code TLSv1.3} is offered, with the three AEAD suites;</li>
 *   <li>{@link #mutualTlsClient()} refuses to build without the client keystore, so bundle/metrics
 *       calls cannot silently degrade to server-authenticated TLS.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecureChannel {

    private static final Logger log = LoggerFactory.getLogger(SecureChannel.class);

    /** TLS 1.3 AEAD suites — the only ones offered. */
    static final String[] TLS13_CIPHERS = {
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_GCM_SHA256"
    };

    private final ControlPlaneProperties properties;
    private volatile RestClient mutualTls;
    private volatile RestClient bootstrap;

    public SecureChannel(ControlPlaneProperties properties) {
        this.properties = properties;
        requireHttps(properties.getBaseUrl());
    }

    /** @throws IllegalStateException if the configured base URL is plaintext HTTP. */
    static void requireHttps(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return; // unconfigured: the node stays unauthorized and reports it; see ActivationService
        }
        String scheme = baseUrl.trim().toLowerCase(Locale.ROOT);
        if (scheme.startsWith("https://")) {
            return;
        }
        throw new IllegalStateException("hokeka.controlplane.base-url must be https:// — refusing to start with '"
                + baseUrl + "'. The control-plane channel has no plaintext fallback (channel contract §2).");
    }

    public String baseUrl() {
        String url = properties.getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new SecurityException("hokeka.controlplane.base-url is not configured");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Client for {@code /bundle} and {@code /metrics}: mTLS, client certificate mandatory. */
    public RestClient mutualTlsClient() {
        RestClient c = mutualTls;
        if (c == null) {
            synchronized (this) {
                c = mutualTls;
                if (c == null) {
                    c = build(true);
                    mutualTls = c;
                }
            }
        }
        return c;
    }

    /**
     * Client for {@code /enroll} only. First boot happens before a client certificate exists, so this
     * one authenticates the server and carries the single-use enrollment code as its credential. It
     * presents the client certificate too once one has been provisioned.
     */
    public RestClient bootstrapClient() {
        RestClient c = bootstrap;
        if (c == null) {
            synchronized (this) {
                c = bootstrap;
                if (c == null) {
                    c = build(false);
                    bootstrap = c;
                }
            }
        }
        return c;
    }

    private RestClient build(boolean requireClientCertificate) {
        String base = baseUrl();
        SSLContext sslContext = sslContext(requireClientCertificate);

        SSLParameters params = sslContext.getDefaultSSLParameters();
        params.setProtocols(new String[]{"TLSv1.3"});
        params.setCipherSuites(TLS13_CIPHERS);
        params.setEndpointIdentificationAlgorithm("HTTPS"); // hostname verification, always on

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getRequestTimeout())
                .sslContext(sslContext)
                .sslParameters(params)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getRequestTimeout());

        log.info("Control-plane channel ready: {} (TLSv1.3{})", base,
                requireClientCertificate ? ", mTLS client certificate presented" : ", enrollment bootstrap");
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    private SSLContext sslContext(boolean requireClientCertificate) {
        try {
            KeyManagerFactory kmf = null;
            KeyStore identity = load(properties.getKeystore(), properties.getKeystorePassword(),
                    properties.getKeystoreType(), "mTLS client keystore (hokeka.controlplane.keystore)",
                    requireClientCertificate);
            if (identity != null) {
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(identity, chars(properties.getKeystorePassword()));
            }

            TrustManagerFactory tmf = null;
            KeyStore trust = load(properties.getTruststore(), properties.getTruststorePassword(),
                    properties.getTruststoreType(), "control-plane truststore (hokeka.controlplane.truststore)",
                    false);
            if (trust != null) {
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trust);
            }

            SSLContext ctx = SSLContext.getInstance("TLSv1.3");
            ctx.init(kmf == null ? null : kmf.getKeyManagers(),
                    tmf == null ? null : tmf.getTrustManagers(),
                    new SecureRandom());
            return ctx;
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("cannot initialise the control-plane TLS context: " + e.getMessage(), e);
        }
    }

    private static KeyStore load(String location, String password, String type, String what, boolean required)
            throws Exception {
        if (location == null || location.isBlank()) {
            if (required) {
                throw new SecurityException("missing " + what
                        + " — the edge cannot call the control plane without a client certificate");
            }
            return null;
        }
        Path path = Path.of(location.replaceFirst("^file:", ""));
        if (!Files.isReadable(path)) {
            throw new SecurityException(what + " is not readable at " + path);
        }
        KeyStore ks = KeyStore.getInstance(type == null || type.isBlank() ? "PKCS12" : type);
        try (InputStream in = Files.newInputStream(path)) {
            ks.load(in, chars(password));
        }
        return ks;
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }
}
