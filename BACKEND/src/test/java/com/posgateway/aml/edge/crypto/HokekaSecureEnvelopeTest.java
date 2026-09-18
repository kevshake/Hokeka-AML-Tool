package com.posgateway.aml.edge.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.XECPublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HokekaSecureEnvelopeTest {

    private final HokekaSecureEnvelope hse = new HokekaSecureEnvelope();

    private KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private KeyPair x25519() throws Exception {
        return KeyPairGenerator.getInstance("X25519").generateKeyPair();
    }

    @Test
    void sealAndOpenRoundTrip() throws Exception {
        KeyPair cpSigner = ed25519();
        KeyPair edge = x25519();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());
        byte[] context = "psp-42".getBytes(StandardCharsets.UTF_8);
        byte[] payload = "RULE-BUNDLE v37: {\"rules\":[...secret logic...]}".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = hse.seal(payload, edgePubRaw, cpSigner.getPrivate(), context);

        // Envelope must not contain the plaintext (logic does not leak in transit).
        assertTrue(indexOf(envelope, payload) < 0, "plaintext must not appear in the sealed envelope");

        byte[] opened = hse.open(envelope, edge.getPrivate(), cpSigner.getPublic(), context);
        assertArrayEquals(payload, opened);
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        KeyPair cpSigner = ed25519();
        KeyPair edge = x25519();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());
        byte[] envelope = hse.seal("payload".getBytes(), edgePubRaw, cpSigner.getPrivate(), null);

        // Flip a byte inside the ciphertext region (offset 53+).
        envelope[60] ^= 0x01;

        assertThrows(SecurityException.class,
                () -> hse.open(envelope, edge.getPrivate(), cpSigner.getPublic(), null));
    }

    @Test
    void envelopeFromAnUntrustedSignerIsRejected() throws Exception {
        KeyPair realCp = ed25519();
        KeyPair attacker = ed25519();
        KeyPair edge = x25519();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());

        // Attacker seals with its OWN signing key.
        byte[] forged = hse.seal("evil rules".getBytes(), edgePubRaw, attacker.getPrivate(), null);

        // Edge only trusts the real control plane's public key → rejected.
        assertThrows(SecurityException.class,
                () -> hse.open(forged, edge.getPrivate(), realCp.getPublic(), null));
    }

    @Test
    void wrongEdgeKeyCannotDecrypt() throws Exception {
        KeyPair cpSigner = ed25519();
        KeyPair edge = x25519();
        KeyPair otherEdge = x25519();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());
        byte[] envelope = hse.seal("secret".getBytes(), edgePubRaw, cpSigner.getPrivate(), null);

        // A different edge's private key derives a different ECDH secret → AEAD tag fails.
        assertThrows(SecurityException.class,
                () -> hse.open(envelope, otherEdge.getPrivate(), cpSigner.getPublic(), null));
    }

    @Test
    void contextBindingMustMatch() throws Exception {
        KeyPair cpSigner = ed25519();
        KeyPair edge = x25519();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());
        byte[] envelope = hse.seal("secret".getBytes(), edgePubRaw, cpSigner.getPrivate(),
                "psp-1".getBytes(StandardCharsets.UTF_8));

        // Opening with a different context derives a different key → fails.
        assertThrows(SecurityException.class, () -> hse.open(envelope, edge.getPrivate(),
                cpSigner.getPublic(), "psp-2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void x25519RawEncodingRoundTrips() throws Exception {
        KeyPair kp = x25519();
        byte[] raw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) kp.getPublic());
        assertEquals(32, raw.length);
        var rebuilt = HokekaSecureEnvelope.x25519PublicFromRaw(raw);
        byte[] raw2 = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) rebuilt);
        assertArrayEquals(raw, raw2);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
