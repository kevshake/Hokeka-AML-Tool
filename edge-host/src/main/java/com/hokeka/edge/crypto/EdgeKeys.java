package com.hokeka.edge.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Key generation and the base64 codecs used on the edge↔control-plane wire.
 *
 * <p>Encoding rules (both sides must agree):
 * <ul>
 *   <li><b>Public keys</b> travel as base64 of the X.509 SubjectPublicKeyInfo DER (RFC 8410, 44
 *       bytes for both Ed25519 and X25519). Raw 32-byte encodings are also accepted on decode so a
 *       pinned key can be configured either way.</li>
 *   <li><b>Private keys</b> are persisted as base64 of the PKCS#8 DER, inside the credentials file
 *       (mode 600). They never leave the host.</li>
 *   <li>HSE-1 itself uses the RFC-7748 raw 32-byte X25519 form; {@link #x25519RawPublic} converts.</li>
 * </ul>
 */
public final class EdgeKeys {

    /** SPKI prefix for an Ed25519 public key: SEQUENCE{SEQUENCE{OID 1.3.101.112}, BIT STRING}. */
    private static final byte[] ED25519_SPKI_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
    /** SPKI prefix for an X25519 public key (OID 1.3.101.110). */
    private static final byte[] X25519_SPKI_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00};

    private EdgeKeys() {
    }

    public static KeyPair generateX25519() {
        return generate("X25519");
    }

    public static KeyPair generateEd25519() {
        return generate("Ed25519");
    }

    private static KeyPair generate(String algorithm) {
        try {
            return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("cannot generate " + algorithm + " keypair", e);
        }
    }

    public static String encodePrivate(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encodePublic(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PrivateKey decodeX25519Private(String base64Pkcs8) {
        return decodePrivate("X25519", base64Pkcs8);
    }

    public static PrivateKey decodeEd25519Private(String base64Pkcs8) {
        return decodePrivate("Ed25519", base64Pkcs8);
    }

    private static PrivateKey decodePrivate(String algorithm, String base64Pkcs8) {
        try {
            byte[] der = Base64.getDecoder().decode(require(base64Pkcs8, algorithm + " private key"));
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("invalid " + algorithm + " private key encoding", e);
        }
    }

    /** Decode a pinned Ed25519 public key from base64 SPKI DER (44 bytes) or raw 32 bytes. */
    public static PublicKey decodeEd25519Public(String base64) {
        byte[] bytes = Base64.getDecoder().decode(require(base64, "Ed25519 public key"));
        byte[] der = bytes.length == 32 ? concat(ED25519_SPKI_PREFIX, bytes) : bytes;
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("invalid Ed25519 public key encoding", e);
        }
    }

    /**
     * Decode an X25519 public key from base64 SPKI DER or raw 32 bytes into the RFC-7748 raw form
     * that {@link HokekaSecureEnvelope#seal} expects.
     */
    public static byte[] decodeX25519PublicRaw(String base64) {
        byte[] bytes = Base64.getDecoder().decode(require(base64, "X25519 public key"));
        if (bytes.length == 32) {
            return bytes;
        }
        if (bytes.length == X25519_SPKI_PREFIX.length + 32
                && Arrays.equals(Arrays.copyOf(bytes, X25519_SPKI_PREFIX.length), X25519_SPKI_PREFIX)) {
            return Arrays.copyOfRange(bytes, X25519_SPKI_PREFIX.length, bytes.length);
        }
        throw new IllegalStateException("X25519 public key must be raw 32 bytes or SPKI DER");
    }

    /** RFC-7748 raw 32-byte form of an X25519 public key. */
    public static byte[] x25519RawPublic(PublicKey key) {
        return HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) key);
    }

    private static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing " + what
                    + " — configure it before the edge can talk to the control plane");
        }
        return value.trim();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
