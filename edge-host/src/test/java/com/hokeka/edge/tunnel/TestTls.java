package com.hokeka.edge.tunnel;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Throw-away TLS material for the interop test, made with the JDK's {@code keytool}: a server
 * identity for {@code localhost} / {@code 127.0.0.1}, an Edge client identity whose CN is the edge
 * id (the mTLS identity of §1), and a truststore for each side holding the other's certificate.
 */
final class TestTls {

    static final String PASSWORD = "changeit";

    final Path serverKeystore;
    final Path serverTruststore;
    final Path clientKeystore;
    final Path clientTruststore;

    /** Creates all four stores in {@code dir}. */
    TestTls(Path dir, String edgeId) throws Exception {
        serverKeystore = dir.resolve("server.p12");
        serverTruststore = dir.resolve("server-trust.p12");
        clientKeystore = dir.resolve("client.p12");
        clientTruststore = dir.resolve("client-trust.p12");
        keytool("-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-keystore", serverKeystore.toString(),
                "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD);
        keytool("-genkeypair", "-alias", "edge", "-keyalg", "EC", "-groupname", "secp256r1", "-dname",
                "CN=" + edgeId + ",O=PSP", "-validity", "2", "-keystore", clientKeystore.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD);
        Path serverCert = dir.resolve("server.crt");
        Path clientCert = dir.resolve("client.crt");
        keytool("-exportcert", "-alias", "server", "-keystore", serverKeystore.toString(), "-storepass", PASSWORD,
                "-rfc", "-file", serverCert.toString());
        keytool("-exportcert", "-alias", "edge", "-keystore", clientKeystore.toString(), "-storepass", PASSWORD,
                "-rfc", "-file", clientCert.toString());
        keytool("-importcert", "-noprompt", "-alias", "server", "-file", serverCert.toString(), "-keystore",
                clientTruststore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        keytool("-importcert", "-noprompt", "-alias", "edge", "-file", clientCert.toString(), "-keystore",
                serverTruststore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
    }

    /** The server's SSLContext (its identity; trusts the Edge certificate). */
    SSLContext serverContext() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(load(serverKeystore), PASSWORD.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load(serverTruststore));
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    /** Loads a PKCS#12 store. */
    private static KeyStore load(Path p) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p)) {
            ks.load(in, PASSWORD.toCharArray());
        }
        return ks;
    }

    /** Runs keytool and fails loudly. */
    private static void keytool(String... args) throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        List<String> cmd = new ArrayList<>();
        cmd.add(keytool.toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }
}
