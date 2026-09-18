package com.hokeka.edge.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

/**
 * Hokeka Secure Envelope, version 1 (HSE-1) — edge-side port.
 *
 * <p>Byte-identical to the control-plane implementation ({@code com.posgateway.aml.edge.crypto}) and
 * to the Rust {@code hse-crypto} crate; {@code edge-host} is a standalone module so the class is
 * copied rather than depended on. The edge uses {@link #open} for rule bundles and {@link #seal} for
 * aggregate metrics.
 *
 * <p>HSE-1 composes only vetted primitives — X25519 ECDH (forward secrecy) → HKDF-SHA256 →
 * ChaCha20-Poly1305 AEAD, with an Ed25519 signature over the whole envelope. It runs <b>underneath
 * TLS</b> as a second, independent layer so rule logic stays confidential even where TLS is
 * terminated by a PSP load balancer.
 *
 * <p><b>Wire format</b> (all multi-byte integers big-endian):
 * <pre>
 *   off  len  field
 *   0    4    magic  = "HSE1"
 *   4    1    version = 1
 *   5    32   ephemeral X25519 public key (RFC-7748 little-endian)
 *   37   12   ChaCha20-Poly1305 nonce
 *   49   4    ciphertext length N
 *   53   N    ciphertext = AEAD(payload) (includes 16-byte Poly1305 tag)
 *   53+N 64   Ed25519 signature over bytes [0 .. 53+N)
 * </pre>
 */
public final class HokekaSecureEnvelope {

    public static final byte[] MAGIC = {'H', 'S', 'E', '1'};
    public static final byte VERSION = 1;
    public static final int EPK_LEN = 32;
    public static final int NONCE_LEN = 12;
    public static final int SIG_LEN = 64;
    public static final int TAG_LEN = 16;
    private static final int HEADER_LEN = 4 + 1 + EPK_LEN + NONCE_LEN + 4; // 53
    private static final byte[] HKDF_INFO_PREFIX =
            "hokeka-hse1-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final SecureRandom random;

    public HokekaSecureEnvelope() {
        this(new SecureRandom());
    }

    /** Deterministic RNG constructor — used only to produce reproducible cross-language test vectors. */
    public HokekaSecureEnvelope(SecureRandom random) {
        this.random = random;
    }

    /**
     * Seal: encrypt {@code payload} for the peer and sign the envelope. At the edge this is used for
     * the aggregate metrics upload (recipient = control plane).
     *
     * @param payload             the plaintext
     * @param peerX25519PubRaw    the recipient's long-term X25519 public key, raw 32 bytes
     * @param senderEd25519Priv   the sender's Ed25519 signing key
     * @param context             binding context mixed into key derivation and the AEAD AAD
     */
    public byte[] seal(byte[] payload, byte[] peerX25519PubRaw, PrivateKey senderEd25519Priv, byte[] context) {
        return sealWithEphemeral(payload, peerX25519PubRaw, senderEd25519Priv, context, generateX25519());
    }

    /** Seal with a caller-supplied ephemeral keypair (for deterministic test vectors). */
    public byte[] sealWithEphemeral(byte[] payload, byte[] peerX25519PubRaw, PrivateKey senderEd25519Priv,
                                    byte[] context, KeyPair ephemeral) {
        try {
            PublicKey peerPub = x25519PublicFromRaw(peerX25519PubRaw);
            byte[] shared = ecdh(ephemeral.getPrivate(), peerPub);
            byte[] epkRaw = rawFromX25519Public((XECPublicKey) ephemeral.getPublic());

            byte[] nonce = new byte[NONCE_LEN];
            random.nextBytes(nonce);

            byte[] key = hkdfSha256(shared, MAGIC, hkdfInfo(context), 32);

            byte[] aadHeaderPrefix = concat(MAGIC, new byte[]{VERSION}, epkRaw, nonce); // 49 bytes, bound as AAD
            byte[] ciphertext = chacha20Poly1305Encrypt(key, nonce, payload, aadHeaderPrefix);

            ByteBuffer header = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.BIG_ENDIAN);
            header.put(MAGIC).put(VERSION).put(epkRaw).put(nonce).putInt(ciphertext.length);

            byte[] signedRegion = concat(header.array(), ciphertext);
            byte[] signature = ed25519Sign(senderEd25519Priv, signedRegion);

            return concat(signedRegion, signature);
        } catch (Exception e) {
            throw new IllegalStateException("HSE-1 seal failed", e);
        }
    }

    /**
     * Open: verify the signature and decrypt. Throws if the signature is invalid, the AEAD tag fails,
     * or the framing is malformed — HSE-1 never returns unauthenticated plaintext.
     *
     * @param envelope         the sealed bytes
     * @param ownX25519Priv    this party's long-term X25519 private key
     * @param senderEd25519Pub the sender's Ed25519 public key (pinned)
     * @param context          the same binding context used when sealing
     */
    public byte[] open(byte[] envelope, PrivateKey ownX25519Priv, PublicKey senderEd25519Pub, byte[] context) {
        try {
            if (envelope == null || envelope.length < HEADER_LEN + TAG_LEN + SIG_LEN) {
                throw new SecurityException("HSE-1 envelope too short");
            }
            ByteBuffer bb = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN);
            byte[] magic = new byte[4];
            bb.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new SecurityException("HSE-1 bad magic");
            }
            byte version = bb.get();
            if (version != VERSION) {
                throw new SecurityException("HSE-1 unsupported version " + version);
            }
            byte[] epkRaw = new byte[EPK_LEN];
            bb.get(epkRaw);
            byte[] nonce = new byte[NONCE_LEN];
            bb.get(nonce);
            int ctLen = bb.getInt();
            if (ctLen < TAG_LEN || envelope.length != HEADER_LEN + ctLen + SIG_LEN) {
                throw new SecurityException("HSE-1 bad length framing");
            }
            byte[] ciphertext = new byte[ctLen];
            bb.get(ciphertext);
            byte[] signature = new byte[SIG_LEN];
            bb.get(signature);

            // 1) Authenticity: signature over everything before it.
            int signedLen = HEADER_LEN + ctLen;
            byte[] signedRegion = Arrays.copyOfRange(envelope, 0, signedLen);
            if (!ed25519Verify(senderEd25519Pub, signedRegion, signature)) {
                throw new SecurityException("HSE-1 signature verification failed");
            }

            // 2) Confidentiality: ECDH → HKDF → AEAD decrypt (tag also checked).
            PublicKey epk = x25519PublicFromRaw(epkRaw);
            byte[] shared = ecdh(ownX25519Priv, epk);
            byte[] key = hkdfSha256(shared, MAGIC, hkdfInfo(context), 32);
            byte[] aadHeaderPrefix = concat(MAGIC, new byte[]{VERSION}, epkRaw, nonce);
            return chacha20Poly1305Decrypt(key, nonce, ciphertext, aadHeaderPrefix);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("HSE-1 open failed", e);
        }
    }

    // ── primitives ─────────────────────────────────────────────────────────────────────────────

    private byte[] hkdfInfo(byte[] context) {
        return context == null ? HKDF_INFO_PREFIX : concat(HKDF_INFO_PREFIX, context);
    }

    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] realSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
        mac.init(new SecretKeySpec(realSalt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = new byte[outLen];
        byte[] t = new byte[0];
        int pos = 0;
        byte counter = 1;
        while (pos < outLen) {
            mac.update(t);
            mac.update(info == null ? new byte[0] : info);
            mac.update(counter);
            t = mac.doFinal();
            int n = Math.min(t.length, outLen - pos);
            System.arraycopy(t, 0, okm, pos, n);
            pos += n;
            counter++;
        }
        return okm;
    }

    private static byte[] chacha20Poly1305Encrypt(byte[] key, byte[] nonce, byte[] plaintext, byte[] aad)
            throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] chacha20Poly1305Decrypt(byte[] key, byte[] nonce, byte[] ciphertext, byte[] aad)
            throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private static byte[] ecdh(PrivateKey priv, PublicKey pub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(priv);
        ka.doPhase(pub, true);
        return ka.generateSecret();
    }

    static byte[] ed25519Sign(PrivateKey priv, byte[] data) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(priv);
        s.update(data);
        return s.sign();
    }

    static boolean ed25519Verify(PublicKey pub, byte[] data, byte[] signature) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pub);
        s.update(data);
        return s.verify(signature);
    }

    public static KeyPair generateX25519() {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── X25519 raw (RFC-7748 little-endian) <-> JDK key conversion ───────────────────────────────

    /** RFC-7748 32-byte little-endian encoding of an X25519 public key's u-coordinate. */
    public static byte[] rawFromX25519Public(XECPublicKey key) {
        BigInteger u = key.getU();
        byte[] be = u.toByteArray(); // big-endian, possibly with a sign byte or short
        byte[] be32 = new byte[32];
        int copy = Math.min(be.length, 32);
        System.arraycopy(be, be.length - copy, be32, 32 - copy, copy);
        byte[] le = new byte[32];
        for (int i = 0; i < 32; i++) {
            le[i] = be32[31 - i];
        }
        return le;
    }

    /** Rebuild an X25519 public key from its RFC-7748 32-byte little-endian encoding. */
    public static PublicKey x25519PublicFromRaw(byte[] le) {
        try {
            if (le == null || le.length != 32) {
                throw new IllegalArgumentException("X25519 public key must be 32 bytes");
            }
            byte[] be = new byte[32];
            for (int i = 0; i < 32; i++) {
                be[i] = le[31 - i];
            }
            be[0] &= 0x7F; // RFC-7748: ignore the most-significant bit on decode
            BigInteger u = new BigInteger(1, be);
            XECPublicKeySpec spec = new XECPublicKeySpec(new NamedParameterSpec("X25519"), u);
            return KeyFactory.getInstance("X25519").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid X25519 public key", e);
        }
    }

    // ── small helpers ────────────────────────────────────────────────────────────────────────────

    static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
