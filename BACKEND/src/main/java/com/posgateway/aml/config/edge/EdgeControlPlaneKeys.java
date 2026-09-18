package com.posgateway.aml.config.edge;

import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Holds the two long-term key pairs the control plane needs for the HSE-1 edge channel.
 *
 * <ul>
 *   <li><b>Ed25519 signing pair</b> — signs every bundle envelope the control plane emits. Each
 *       edge pins the public half so it can prove a bundle really came from Hokeka
 *       ({@code docs/architecture/edge-distributed-platform.md} §11).</li>
 *   <li><b>X25519 ingest pair</b> — the recipient key edges seal their metrics envelopes to. The
 *       control plane opens them with the private half.</li>
 * </ul>
 *
 * <p>Both public halves are handed to an edge exactly once, in the activation response, so the edge
 * can pin them at provisioning time.
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code edge.bundle.signing-key} / {@code edge.metrics.ingest-key} — base64 PKCS#8 private
 *       keys (env {@code EDGE_BUNDLE_SIGNING_KEY} / {@code EDGE_METRICS_INGEST_KEY}).</li>
 *   <li>{@code edge.bundle.signing-public-key} / {@code edge.metrics.ingest-public-key} —
 *       optional; base64 X.509 SubjectPublicKeyInfo or the bare 32-byte encoding. Required only
 *       when the private key was exported as PKCS#8 <i>v1</i>, which carries no public key.</li>
 * </ul>
 *
 * <p>Unconfigured keys are generated <b>ephemerally</b> at startup with a loud warning: the platform
 * stays usable for local development, but a restart rotates them and every provisioned edge would
 * reject what they produce. Deliberately noisy, never silent.
 */
@Component
public class EdgeControlPlaneKeys {

    private static final Logger log = LoggerFactory.getLogger(EdgeControlPlaneKeys.class);

    /** DER prefix of an X.509 SubjectPublicKeyInfo wrapping a 32-byte Ed25519 key (OID 1.3.101.112). */
    private static final byte[] ED25519_SPKI_PREFIX =
            {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    /** DER prefix of an X.509 SubjectPublicKeyInfo wrapping a 32-byte X25519 key (OID 1.3.101.110). */
    private static final byte[] X25519_SPKI_PREFIX =
            {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00};

    private final KeyPair signingKeyPair;
    private final KeyPair ingestKeyPair;
    private final boolean ephemeralSigningKey;
    private final boolean ephemeralIngestKey;

    public EdgeControlPlaneKeys(EdgeProperties properties) {
        String signingPrivate = properties.getBundle().getSigningKey();
        if (isSet(signingPrivate)) {
            this.signingKeyPair = load("Ed25519", ED25519_SPKI_PREFIX, signingPrivate.trim(),
                    properties.getBundle().getSigningPublicKey(), "edge.bundle.signing-key");
            this.ephemeralSigningKey = false;
            log.info("Edge bundle signing key loaded from configuration (Ed25519)");
        } else {
            this.signingKeyPair = generate("Ed25519");
            this.ephemeralSigningKey = true;
            log.warn("EDGE_BUNDLE_SIGNING_KEY is not set — generated an EPHEMERAL Ed25519 bundle "
                    + "signing key. Bundles signed with it become unverifiable after a restart. "
                    + "Configure edge.bundle.signing-key before provisioning any real edge node.");
        }

        String ingestPrivate = properties.getMetrics().getIngestKey();
        if (isSet(ingestPrivate)) {
            this.ingestKeyPair = load("X25519", X25519_SPKI_PREFIX, ingestPrivate.trim(),
                    properties.getMetrics().getIngestPublicKey(), "edge.metrics.ingest-key");
            this.ephemeralIngestKey = false;
            log.info("Edge metrics ingest key loaded from configuration (X25519)");
        } else {
            this.ingestKeyPair = generate("X25519");
            this.ephemeralIngestKey = true;
            log.warn("EDGE_METRICS_INGEST_KEY is not set — generated an EPHEMERAL X25519 metrics "
                    + "ingest key. Metrics sealed to it become undecryptable after a restart. "
                    + "Configure edge.metrics.ingest-key before provisioning any real edge node.");
        }
    }

    // ── signing (control plane → edge) ───────────────────────────────────────────────────────────

    public PrivateKey signingPrivateKey() {
        return signingKeyPair.getPrivate();
    }

    public PublicKey signingPublicKey() {
        return signingKeyPair.getPublic();
    }

    /** RFC-8032 compressed 32-byte Ed25519 public key, base64 — what an edge pins. */
    public String signingPublicKeyBase64() {
        return Base64.getEncoder()
                .encodeToString(rawEd25519Public((EdECPublicKey) signingKeyPair.getPublic()));
    }

    // ── ingest (edge → control plane) ────────────────────────────────────────────────────────────

    public PrivateKey ingestPrivateKey() {
        return ingestKeyPair.getPrivate();
    }

    /** RFC-7748 32-byte little-endian X25519 public key, base64 — what an edge seals metrics to. */
    public String ingestPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) ingestKeyPair.getPublic()));
    }

    /** True when either key was generated at startup rather than configured. */
    public boolean isEphemeral() {
        return ephemeralSigningKey || ephemeralIngestKey;
    }

    // ── helpers used by the metrics ingest path ──────────────────────────────────────────────────

    /** Rebuild an Ed25519 public key from its RFC-8032 compressed 32-byte encoding. */
    public static PublicKey ed25519PublicFromRaw(byte[] raw32) {
        return publicFromRaw("Ed25519", ED25519_SPKI_PREFIX, raw32);
    }

    /** RFC-8032 compressed Ed25519 public key: 32-byte little-endian y with x-parity in the top bit. */
    public static byte[] rawEd25519Public(EdECPublicKey pub) {
        var point = pub.getPoint();
        byte[] be = point.getY().toByteArray();
        byte[] be32 = new byte[32];
        int copy = Math.min(be.length, 32);
        System.arraycopy(be, be.length - copy, be32, 32 - copy, copy);
        byte[] le = new byte[32];
        for (int i = 0; i < 32; i++) {
            le[i] = be32[31 - i];
        }
        if (point.isXOdd()) {
            le[31] |= (byte) 0x80;
        }
        return le;
    }

    // ── loading ──────────────────────────────────────────────────────────────────────────────────

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static KeyPair generate(String algorithm) {
        try {
            return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " unavailable in this JVM", e);
        }
    }

    private static KeyPair load(String algorithm, byte[] spkiPrefix, String base64Pkcs8,
                                String base64PublicKey, String propertyName) {
        PrivateKey priv;
        try {
            byte[] pkcs8 = Base64.getDecoder().decode(base64Pkcs8);
            priv = KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException(
                    propertyName + " is not a valid base64 PKCS#8 " + algorithm + " private key", e);
        }

        byte[] raw = null;
        if (isSet(base64PublicKey)) {
            raw = rawFromConfiguredPublicKey(base64PublicKey.trim(), spkiPrefix, propertyName);
        }
        if (raw == null) {
            raw = extractPublicFromPkcs8V2(priv.getEncoded());
        }
        if (raw == null) {
            throw new IllegalStateException(propertyName + " is a PKCS#8 v1 blob that carries no "
                    + "public key. Set " + propertyName + "-public-key (base64 X.509 SPKI or the raw "
                    + "32 bytes), or re-export the key in PKCS#8 v2 form.");
        }
        return new KeyPair(publicFromRaw(algorithm, spkiPrefix, raw), priv);
    }

    /** Accepts either an X.509 SubjectPublicKeyInfo or the bare 32-byte encoding. */
    private static byte[] rawFromConfiguredPublicKey(String base64PublicKey, byte[] spkiPrefix,
                                                     String propertyName) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64PublicKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(propertyName + "-public-key is not valid base64", e);
        }
        if (decoded.length == 32) {
            return decoded;
        }
        if (decoded.length == spkiPrefix.length + 32) {
            byte[] raw = new byte[32];
            System.arraycopy(decoded, spkiPrefix.length, raw, 0, 32);
            return raw;
        }
        throw new IllegalStateException(propertyName + "-public-key must decode to 32 bytes (raw) "
                + "or " + (spkiPrefix.length + 32) + " bytes (X.509 SPKI); got " + decoded.length);
    }

    private static PublicKey publicFromRaw(String algorithm, byte[] spkiPrefix, byte[] raw32) {
        if (raw32 == null || raw32.length != 32) {
            throw new IllegalArgumentException(algorithm + " public key must be 32 bytes");
        }
        try {
            byte[] spki = new byte[spkiPrefix.length + 32];
            System.arraycopy(spkiPrefix, 0, spki, 0, spkiPrefix.length);
            System.arraycopy(raw32, 0, spki, spkiPrefix.length, 32);
            return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new IllegalStateException("cannot rebuild the " + algorithm + " public key", e);
        }
    }

    /**
     * Pull the 32-byte public key out of a PKCS#8 v2 (RFC 5958 OneAsymmetricKey) blob: it lives in
     * the {@code [1] IMPLICIT BIT STRING} attribute, i.e. the 32 bytes following the
     * {@code 0x81 0x21 0x00} tag/length/unused-bits triple.
     *
     * @return the raw key, or {@code null} for a v1 blob that has no public-key attribute
     */
    private static byte[] extractPublicFromPkcs8V2(byte[] pkcs8) {
        if (pkcs8 == null) {
            return null;
        }
        for (int i = 0; i + 35 <= pkcs8.length; i++) {
            if ((pkcs8[i] & 0xFF) == 0x81 && (pkcs8[i + 1] & 0xFF) == 0x21 && pkcs8[i + 2] == 0x00) {
                byte[] raw = new byte[32];
                System.arraycopy(pkcs8, i + 3, raw, 0, 32);
                return raw;
            }
        }
        return null;
    }
}
