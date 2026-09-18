package com.posgateway.aml.service.edge;

import com.posgateway.aml.config.edge.EdgeProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Optional;

/**
 * Establishes <b>which edge</b> is calling {@code /edge/bundle} and {@code /edge/metrics}.
 *
 * <h2>What is actually trusted</h2>
 * The contract (§5) authenticates these endpoints with "mTLS + edge id". This resolver therefore
 * derives the identity from a <b>verified client certificate</b> only, in this precedence:
 *
 * <ol>
 *   <li><b>JVM-terminated mTLS.</b> The servlet container populates the
 *       {@code jakarta.servlet.request.X509Certificate} attribute only after it has validated the
 *       chain against the configured trust store. The edge id is the certificate subject's
 *       {@code CN}. This is the strongest form and always wins.</li>
 *   <li><b>Proxy-terminated mTLS.</b> When nginx terminates TLS (the deployed topology — see
 *       {@code BACKEND/nginx/fraud-detector-api.conf}), it forwards
 *       {@code X-SSL-Client-Verify: SUCCESS} and {@code X-SSL-Client-S-DN: …CN=acme-eu-1…}. Those
 *       headers are honoured <b>only</b> when the request's remote address is on the configured
 *       {@code edge.mtls.trusted-proxies} allow-list, so a public client cannot forge them: an
 *       attacker would have to originate the connection from the reverse proxy itself.</li>
 * </ol>
 *
 * <h2>What is never trusted</h2>
 * A bare {@code X-Hokeka-Edge-Id} header is <b>not</b> an authentication factor and can never, on
 * its own, identify an edge. It is accepted only as a redundant assertion / routing hint: when
 * present it must equal the certificate-derived id, otherwise the request is refused. This keeps a
 * misconfigured edge from silently pulling another tenant's bundle, while never letting a header
 * alone grant access.
 *
 * <p>When no verified certificate identity can be established the resolver returns
 * {@link Optional#empty()} and the caller answers {@code 403} with no bundle bytes (contract §2).
 */
@Component
public class EdgeIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(EdgeIdentityResolver.class);

    /** Servlet attribute the container sets after a successful mTLS handshake. */
    public static final String X509_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    /**
     * Redundant, non-authenticating assertion of the edge id — the header the edge host sends on
     * {@code /bundle} and {@code /metrics} ({@code RuleBundlePoller} / {@code MetricsShipper}).
     * Treated purely as a routing hint and cross-checked against the certificate identity; it is
     * never, on its own, sufficient to identify or authorise an edge.
     */
    public static final String EDGE_ID_HEADER = "X-Hokeka-Edge-Id";

    /** Self-reported edge-host software version, informational only. */
    public static final String AGENT_VERSION_HEADER = "X-Edge-Agent-Version";

    private final EdgeProperties properties;

    public EdgeIdentityResolver(EdgeProperties properties) {
        this.properties = properties;
    }

    /** Where the identity came from — logged and returned so failures are diagnosable. */
    public enum Source { CLIENT_CERTIFICATE, TRUSTED_PROXY_HEADER }

    public record EdgeIdentity(String edgeId, Source source) {}

    /** Thrown when a presented identity is internally inconsistent — always answered as 403. */
    public static class EdgeIdentityConflictException extends RuntimeException {
        public EdgeIdentityConflictException(String message) {
            super(message);
        }
    }

    public Optional<EdgeIdentity> resolve(HttpServletRequest request) {
        Optional<EdgeIdentity> identity = fromClientCertificate(request);
        if (identity.isEmpty()) {
            identity = fromTrustedProxyHeaders(request);
        }
        identity.ifPresent(id -> crossCheckAssertedHeader(request, id));
        return identity;
    }

    public String agentVersion(HttpServletRequest request) {
        return request.getHeader(AGENT_VERSION_HEADER);
    }

    // ── (1) real, JVM-terminated mTLS ────────────────────────────────────────────────────────────

    private Optional<EdgeIdentity> fromClientCertificate(HttpServletRequest request) {
        Object attribute = request.getAttribute(X509_ATTRIBUTE);
        if (!(attribute instanceof X509Certificate[] chain) || chain.length == 0) {
            return Optional.empty();
        }
        X500Principal subject = chain[0].getSubjectX500Principal();
        String cn = commonName(subject.getName(X500Principal.RFC2253));
        if (cn == null) {
            log.warn("Edge client certificate has no CN in its subject DN — refusing");
            return Optional.empty();
        }
        return Optional.of(new EdgeIdentity(cn, Source.CLIENT_CERTIFICATE));
    }

    // ── (2) proxy-terminated mTLS, headers accepted only from a trusted proxy ────────────────────

    private Optional<EdgeIdentity> fromTrustedProxyHeaders(HttpServletRequest request) {
        EdgeProperties.Mtls mtls = properties.getMtls();
        if (!mtls.isForwardedClientCertEnabled()) {
            return Optional.empty();
        }
        if (!isTrustedProxy(request.getRemoteAddr())) {
            if (request.getHeader(mtls.getVerifyHeader()) != null) {
                log.warn("Ignoring forwarded client-certificate headers from untrusted remote {}",
                        request.getRemoteAddr());
            }
            return Optional.empty();
        }
        String verify = request.getHeader(mtls.getVerifyHeader());
        if (verify == null || !mtls.getVerifySuccessValue().equalsIgnoreCase(verify.trim())) {
            return Optional.empty();
        }
        String dn = request.getHeader(mtls.getSubjectDnHeader());
        String cn = commonName(dn);
        if (cn == null) {
            log.warn("Forwarded client certificate subject DN carries no CN: {}", dn);
            return Optional.empty();
        }
        return Optional.of(new EdgeIdentity(cn, Source.TRUSTED_PROXY_HEADER));
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        for (String trusted : properties.getMtls().getTrustedProxies()) {
            if (trusted != null && trusted.trim().equalsIgnoreCase(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    // ── cross-check ──────────────────────────────────────────────────────────────────────────────

    private void crossCheckAssertedHeader(HttpServletRequest request, EdgeIdentity identity) {
        String asserted = request.getHeader(EDGE_ID_HEADER);
        if (asserted != null && !asserted.isBlank() && !asserted.trim().equals(identity.edgeId())) {
            throw new EdgeIdentityConflictException(
                    EDGE_ID_HEADER + " does not match the client certificate identity");
        }
    }

    /**
     * Extract {@code CN=…} from an RFC-2253 / OpenSSL-style DN. Handles both {@code CN=a,OU=b} and
     * nginx's slash-separated {@code /CN=a/OU=b} rendering.
     */
    static String commonName(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }
        for (String part : dn.split("[,/]")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("CN=")) {
                String cn = trimmed.substring(3).trim();
                return cn.isEmpty() ? null : cn;
            }
        }
        return null;
    }
}
