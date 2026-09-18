package com.hokeka.edge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

/**
 * Refuses to start the transaction API without real TLS.
 *
 * <p>The API carries live PSP transaction data, so a missing certificate must be a hard failure with
 * an actionable message — never a silent downgrade to plaintext HTTP (channel contract §2). It
 * checks that TLS is on, that the PKCS#12 keystore exists, opens with the configured password and
 * holds a key entry, and that only TLS 1.3 with AEAD suites is offered.
 *
 * <p>It is a {@link BeanFactoryPostProcessor} so it runs before the servlet container is created and
 * the connector binds — the operator gets this message, not a Tomcat stack trace.
 *
 * <p>The single exception is the {@code dev} profile, where {@code server.ssl.enabled=false} may be
 * set explicitly; that path logs a loud warning and is never reachable by omission.
 */
@Component
public class EdgeTlsGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(EdgeTlsGuard.class);

    private static final String HOW_TO =
            "Generate one with:\n"
            + "  keytool -genkeypair -alias edge -keyalg EC -groupname secp384r1 -sigalg SHA384withECDSA \\\n"
            + "     -validity 825 -storetype PKCS12 -keystore edge-tls.p12 -dname \"CN=hokeka-edge\" \\\n"
            + "     -ext \"SAN=dns:localhost,ip:127.0.0.1\" -storepass <password>\n"
            + "then set EDGE_TLS_KEYSTORE and EDGE_TLS_KEYSTORE_PASSWORD (deploy/install.sh does this for you).";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Ssl ssl = Binder.get(environment).bind("server.ssl", Bindable.of(Ssl.class)).orElseGet(Ssl::new);
        validate(ssl, environment.matchesProfiles("dev"));
    }

    /** @throws IllegalStateException with an actionable message when the edge would serve plaintext. */
    public static void validate(Ssl ssl, boolean devProfile) {
        if (ssl == null || !ssl.isEnabled()) {
            if (devProfile) {
                log.warn("TLS is DISABLED and the 'dev' profile is active — the transaction API is plaintext "
                        + "HTTP. This configuration must never reach a PSP site.");
                return;
            }
            throw new IllegalStateException("The edge transaction API refuses to start without TLS. "
                    + "server.ssl.enabled is false and no dev profile is active. " + HOW_TO);
        }

        String location = ssl.getKeyStore();
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("server.ssl.key-store (EDGE_TLS_KEYSTORE) is not set — "
                    + "the edge has no TLS identity. " + HOW_TO);
        }
        String password = ssl.getKeyStorePassword();
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("server.ssl.key-store-password (EDGE_TLS_KEYSTORE_PASSWORD) is not set "
                    + "for keystore " + location + ". " + HOW_TO);
        }

        if (!location.startsWith("classpath:")) {
            Path path = Path.of(location.replaceFirst("^file:", ""));
            if (!Files.isReadable(path)) {
                throw new IllegalStateException("TLS keystore " + path.toAbsolutePath()
                        + " is missing or unreadable — refusing to fall back to plaintext HTTP. " + HOW_TO);
            }
            loadKeyStore(path, ssl.getKeyStoreType(), password);
        }

        List<String> protocols = ssl.getEnabledProtocols() == null ? List.of()
                : Arrays.asList(ssl.getEnabledProtocols());
        if (!List.of("TLSv1.3").equals(protocols)) {
            throw new IllegalStateException("server.ssl.enabled-protocols must be exactly TLSv1.3, was "
                    + protocols + " — older protocol versions are not permitted on the transaction API.");
        }
        if (ssl.getCiphers() == null || ssl.getCiphers().length == 0) {
            throw new IllegalStateException("server.ssl.ciphers must list the permitted TLS 1.3 AEAD suites.");
        }
        log.info("Edge transaction API TLS: TLSv1.3, keystore {}, client-auth {}", location, ssl.getClientAuth());
    }

    private static void loadKeyStore(Path path, String type, String password) {
        KeyStore keyStore;
        try (InputStream in = Files.newInputStream(path)) {
            keyStore = KeyStore.getInstance(type == null || type.isBlank() ? "PKCS12" : type);
            keyStore.load(in, password.toCharArray());
        } catch (Exception e) {
            throw new IllegalStateException("TLS keystore " + path.toAbsolutePath()
                    + " could not be opened (wrong password or corrupt file): " + e.getMessage(), e);
        }
        try {
            if (!keyStore.aliases().hasMoreElements()) {
                throw new IllegalStateException("TLS keystore " + path.toAbsolutePath()
                        + " contains no entries — there is no certificate to serve. " + HOW_TO);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("TLS keystore " + path.toAbsolutePath()
                    + " could not be read: " + e.getMessage(), e);
        }
    }
}
