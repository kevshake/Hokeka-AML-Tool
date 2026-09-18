package com.hokeka.edge.config;

import com.hokeka.edge.EdgeHostApplication;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.Ssl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The transaction API must refuse to serve plaintext, loudly and with an actionable message. */
class EdgeTlsGuardTest {

    private static Ssl ssl(String keyStore, String password) {
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setKeyStore(keyStore);
        ssl.setKeyStorePassword(password);
        ssl.setKeyStoreType("PKCS12");
        ssl.setEnabledProtocols(new String[]{"TLSv1.3"});
        ssl.setCiphers(new String[]{"TLS_AES_256_GCM_SHA384"});
        return ssl;
    }

    @Test
    void refusesToStartWhenTlsIsDisabled() {
        Ssl disabled = new Ssl();
        disabled.setEnabled(false);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> EdgeTlsGuard.validate(disabled, false));
        assertTrue(e.getMessage().contains("refuses to start without TLS"), e.getMessage());
        assertTrue(e.getMessage().contains("keytool"), "the message must say how to fix it");

        assertThrows(IllegalStateException.class, () -> EdgeTlsGuard.validate(null, false));
    }

    @Test
    void allowsPlaintextOnlyUnderTheDevProfile() {
        Ssl disabled = new Ssl();
        disabled.setEnabled(false);
        assertDoesNotThrow(() -> EdgeTlsGuard.validate(disabled, true));
    }

    @Test
    void refusesAMissingKeystoreOrPassword(@TempDir Path dir) {
        assertThrows(IllegalStateException.class,
                () -> EdgeTlsGuard.validate(ssl("", "secret"), false));
        assertThrows(IllegalStateException.class,
                () -> EdgeTlsGuard.validate(ssl(dir.resolve("edge-tls.p12").toString(), ""), false));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> EdgeTlsGuard.validate(ssl(dir.resolve("absent.p12").toString(), "secret"), false));
        assertTrue(e.getMessage().contains("refusing to fall back to plaintext"), e.getMessage());
    }

    @Test
    void refusesACorruptKeystoreOrWrongPassword(@TempDir Path dir) throws Exception {
        Path bogus = dir.resolve("bogus.p12");
        Files.write(bogus, "not a keystore".getBytes());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> EdgeTlsGuard.validate(ssl(bogus.toString(), "secret"), false));
        assertTrue(e.getMessage().contains("could not be opened"), e.getMessage());
    }

    @Test
    void refusesWeakProtocolConfiguration(@TempDir Path dir) throws Exception {
        Path keystore = generateKeystore(dir, "changeit");

        Ssl legacy = ssl(keystore.toString(), "changeit");
        legacy.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        assertThrows(IllegalStateException.class, () -> EdgeTlsGuard.validate(legacy, false));

        Ssl noCiphers = ssl(keystore.toString(), "changeit");
        noCiphers.setCiphers(new String[0]);
        assertThrows(IllegalStateException.class, () -> EdgeTlsGuard.validate(noCiphers, false));
    }

    @Test
    void acceptsARealPkcs12KeystoreOnTls13(@TempDir Path dir) throws Exception {
        Path keystore = generateKeystore(dir, "changeit");
        assertDoesNotThrow(() -> EdgeTlsGuard.validate(ssl(keystore.toString(), "changeit"), false));
    }

    @Test
    void theApplicationRefusesToBootWithoutTlsMaterial() {
        // Boots the real application with a keystore path that does not exist: the guard must abort
        // the context before Tomcat ever binds a connector.
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                new SpringApplicationBuilder(EdgeHostApplication.class)
                        .run("--server.port=0",
                                "--server.ssl.key-store=/nonexistent/edge-tls.p12",
                                "--server.ssl.key-store-password=whatever",
                                "--hokeka.controlplane.enabled=false")
                        .close());

        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("refusing to fall back to plaintext"), root.getMessage());
    }

    /** Generates a self-signed PKCS#12 exactly the way deploy/install.sh does. */
    private static Path generateKeystore(Path dir, String password) throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        Assumptions.assumeTrue(Files.isExecutable(keytool), "keytool is required for this test");

        Path keystore = dir.resolve("edge-tls.p12");
        Process process = new ProcessBuilder(keytool.toString(),
                "-genkeypair", "-alias", "edge", "-keyalg", "EC", "-groupname", "secp384r1",
                "-sigalg", "SHA384withECDSA", "-validity", "825", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", password, "-keypass", password,
                "-dname", "CN=hokeka-edge", "-ext", "SAN=dns:localhost,ip:127.0.0.1")
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "keytool timed out");
        assertTrue(Files.isReadable(keystore), "keytool did not produce a keystore: "
                + new String(process.getInputStream().readAllBytes()));
        return keystore;
    }
}
