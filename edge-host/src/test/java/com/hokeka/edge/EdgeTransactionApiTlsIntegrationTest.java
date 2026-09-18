package com.hokeka.edge;

import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.channel.MetricsShipper;
import com.hokeka.edge.channel.RuleBundlePoller;
import com.hokeka.edge.channel.SecureChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that the transaction API is TLS-only and fail-closed: a plaintext request cannot
 * be served, the TLS 1.3 request reports the node as {@code unauthorized} before activation, and the
 * unauthenticated {@code POST /edge/bundle} write path is absent outside the dev profile.
 *
 * <p>The control-plane channel is wired here too (with a dead endpoint), so the test also proves the
 * poller/shipper/activation beans assemble in the production profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EdgeTransactionApiTlsIntegrationTest {

    private static final String STORE_PASSWORD = "edge-test-password";
    private static Path keystore;

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void tlsAndChannel(DynamicPropertyRegistry registry) throws Exception {
        keystore = generateKeystore();
        Path identity = Files.createTempDirectory("edge-identity").resolve("edge-identity.json");

        registry.add("server.ssl.key-store", keystore::toString);
        registry.add("server.ssl.key-store-password", () -> STORE_PASSWORD);
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("hokeka.controlplane.enabled", () -> "true");
        registry.add("hokeka.controlplane.base-url", () -> "https://127.0.0.1:1");
        registry.add("hokeka.controlplane.edge-id", () -> "acme-eu-1");
        registry.add("hokeka.controlplane.psp-id", () -> "acme");
        registry.add("hokeka.controlplane.identity-file", identity::toString);
        registry.add("hokeka.controlplane.bundle-poll-interval", () -> "1h");
        registry.add("hokeka.controlplane.metrics-interval", () -> "1h");
    }

    @Test
    void plaintextHttpIsRefused() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/edge/status"))
                .GET().build();
        try {
            // The TLS connector either drops the connection or rejects the handshake bytes as a bad
            // request; either way the application never sees it and never answers with a decision.
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() >= 400,
                    "a plaintext request must not be served, got HTTP " + response.statusCode());
            assertFalse(response.body().contains("authorization"),
                    "the plaintext request reached the application: " + response.body());
        } catch (IOException connectionRefused) {
            assertNotNull(connectionRefused.getMessage());
        }
    }

    @Test
    void tls13RequestIsServedAndReportsUnauthorizedBeforeActivation() throws Exception {
        HttpResponse<String> response = tlsClient().send(
                HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/edge/status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"authorization\":\"" + AuthorizationGate.STATE_UNAUTHORIZED + "\""),
                response.body());
        // No native library is staged in this JVM, so the status must say so rather than imply the
        // fast path is live — a silent fallback to the interpreter is exactly what this surfaces.
        assertTrue(response.body().contains("\"evaluator\":\"" + EdgeEngine.EVALUATOR_INTERPRETER + "\""),
                response.body());
        assertTrue(response.body().contains("\"nativeCore\":\"absent\""), response.body());
        assertTrue(response.body().contains("\"nativeDegraded\":false"), response.body());
        assertTrue(response.body().contains("\"standbyBundleReady\":false"), response.body());
        assertTrue(response.body().contains("\"ruleBundleVersion\":-1"), response.body());
    }

    @Test
    void unauthenticatedBundleWritePathIsAbsent() throws Exception {
        HttpResponse<String> response = tlsClient().send(
                HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/edge/bundle"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"version\":1,\"rules\":[]}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode(), "POST /edge/bundle must not exist outside the dev profile");
    }

    @Test
    void controlPlaneChannelBeansAreWired() {
        assertTrue(context.getBeanNamesForType(SecureChannel.class).length == 1);
        assertTrue(context.getBeanNamesForType(RuleBundlePoller.class).length == 1);
        assertTrue(context.getBeanNamesForType(MetricsShipper.class).length == 1);
    }

    private static HttpClient tlsClient() throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            trust.load(in, STORE_PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext ssl = SSLContext.getInstance("TLSv1.3");
        ssl.init(null, tmf.getTrustManagers(), null);

        SSLParameters params = ssl.getDefaultSSLParameters();
        params.setProtocols(new String[]{"TLSv1.3"});
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .sslContext(ssl).sslParameters(params).build();
    }

    private static Path generateKeystore() throws Exception {
        Path dir = Files.createTempDirectory("edge-tls");
        Path store = dir.resolve("edge-tls.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(keytool.toString(),
                "-genkeypair", "-alias", "edge", "-keyalg", "EC", "-groupname", "secp384r1",
                "-sigalg", "SHA384withECDSA", "-validity", "825", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD,
                "-dname", "CN=hokeka-edge", "-ext", "SAN=dns:localhost,ip:127.0.0.1")
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(60, TimeUnit.SECONDS) || !Files.isReadable(store)) {
            throw new IllegalStateException("keytool could not create the test keystore: "
                    + new String(process.getInputStream().readAllBytes()));
        }
        return store;
    }
}
