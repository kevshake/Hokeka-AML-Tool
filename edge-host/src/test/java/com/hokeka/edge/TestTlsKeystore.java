package com.hokeka.edge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Test helper: a throwaway PKCS12 TLS keystore made with this JVM's {@code keytool}, the same way
 * {@code deploy/install.sh} provisions one, so Spring contexts pass {@code EdgeTlsGuard}.
 */
public final class TestTlsKeystore {

    private TestTlsKeystore() {
    }

    /**
     * Generates an EC P-384 keystore (alias {@code edge}, SAN localhost / 127.0.0.1) in a fresh
     * temporary directory.
     *
     * @param password store and key password
     * @return path of the new keystore
     * @throws IllegalStateException when keytool fails or times out
     */
    public static Path generate(String password) throws Exception {
        Path dir = Files.createTempDirectory("edge-tls");
        Path store = dir.resolve("edge-tls.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(keytool.toString(),
                "-genkeypair", "-alias", "edge", "-keyalg", "EC", "-groupname", "secp384r1",
                "-sigalg", "SHA384withECDSA", "-validity", "825", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", password, "-keypass", password,
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
