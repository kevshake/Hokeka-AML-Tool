package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Control Plane's half of the tunnel sealing scheme, re-implemented in the test from the
 * contract (EDGE_DATA_RESIDENCY_CONTRACT.md §3, §7.3, §7.4) and the BACKEND's
 * {@code TunnelFrameCodec} / {@code HokekaSecureEnvelope} wire format — deliberately sharing no
 * code with the Edge implementation under test, so an interop test proves byte compatibility rather
 * than self-consistency.
 *
 * <p>HSE-1: X25519 ECDH (ephemeral → recipient) → HKDF-SHA256(salt "HSE1", info
 * "hokeka-hse1-v1" + context) → ChaCha20-Poly1305 (AAD = magic|ver|epk|nonce) → Ed25519 over
 * header|ciphertext. Header = "HSE1" | 0x01 | epk(32, RFC 7748 LE) | nonce(12) | len(4, BE).
 */
public final class CloudSide {

    private static final byte[] MAGIC = {'H', 'S', 'E', '1'};
    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    final KeyPair cpX25519 = generate("X25519");
    final KeyPair cpEd25519 = generate("Ed25519");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> seenNonces = new HashSet<>();
    private final String edgeId;
    private final byte[] edgeX25519Raw;
    private final PublicKey edgeEd25519;

    /** A cloud that has pinned this Edge's public keys. */
    CloudSide(String edgeId, PublicKey edgeX25519Public, PublicKey edgeEd25519Public) {
        this.edgeId = edgeId;
        this.edgeX25519Raw = raw((XECPublicKey) edgeX25519Public);
        this.edgeEd25519 = edgeEd25519Public;
    }

    /** Generates a key pair. */
    public static KeyPair generate(String alg) {
        try {
            return KeyPairGenerator.getInstance(alg).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The Control Plane ingest key, raw 32 bytes base64 (as pinned on the Edge). */
    String ingestPublicB64() {
        return Base64.getEncoder().encodeToString(raw((XECPublicKey) cpX25519.getPublic()));
    }

    /** The Control Plane signing key, SPKI base64 (as pinned on the Edge). */
    String signingPublicB64() {
        return Base64.getEncoder().encodeToString(cpEd25519.getPublic().getEncoded());
    }

    /** A fresh frame id. */
    static String id() {
        return UUID.randomUUID().toString();
    }

    /** Seals a cloud→Edge frame of {@code type} bound to {@code sessionId}. */
    String seal(String type, String id, String corrId, String sessionId, JsonNode body, Instant now) {
        return seal(type, id, corrId, sessionId, edgeId, body, now, null);
    }

    /**
     * Seals a cloud→Edge frame. {@code tamper} may rewrite the plaintext before sealing (binding
     * tests), or be null.
     */
    String seal(String type, String id, String corrId, String sessionId, String addressedEdge, JsonNode body,
                Instant now, java.util.function.Consumer<ObjectNode> tamper) {
        long ts = now.toEpochMilli();
        byte[] nonceRaw = new byte[16];
        RANDOM.nextBytes(nonceRaw);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceRaw);
        ObjectNode frame = mapper.createObjectNode();
        frame.put("type", type);
        frame.put("id", id);
        if (corrId != null) {
            frame.put("corrId", corrId);
        }
        if (sessionId != null) {
            frame.put("sessionId", sessionId);
        }
        ObjectNode plaintext = mapper.createObjectNode();
        plaintext.put("nonce", nonce);
        plaintext.put("issuedAt", MILLIS.format(Instant.ofEpochMilli(ts)));
        plaintext.put("edgeId", addressedEdge);
        ObjectNode payload = plaintext.putObject("payload");
        payload.set("frame", frame);
        payload.set("body", body);
        if (tamper != null) {
            tamper.accept(plaintext);
        }
        byte[] sealed = hseSeal(plaintext.toString().getBytes(StandardCharsets.UTF_8), edgeX25519Raw,
                cpEd25519.getPrivate(), ("hokeka.tunnel." + type).getBytes(StandardCharsets.UTF_8));
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", type);
        outer.put("id", id);
        if (corrId != null) {
            outer.put("corrId", corrId);
        }
        outer.put("ts", ts);
        outer.put("nonce", nonce);
        outer.put("sealed", Base64.getEncoder().encodeToString(sealed));
        return outer.toString();
    }

    /** An unsealed ping/pong. */
    String unsealed(String type, String corrId, Instant now) {
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", type);
        outer.put("id", id());
        if (corrId != null) {
            outer.put("corrId", corrId);
        }
        outer.put("ts", now.toEpochMilli());
        return outer.toString();
    }

    /** An Edge frame as opened by the cloud: header fields plus the verified sealed body. */
    record Received(String type, String id, String corrId, String sessionId, ObjectNode body, ObjectNode outer) {
    }

    /**
     * Opens an Edge→cloud frame the way the Control Plane does (§7.4 steps 2–4): HSE-1 with the
     * ingest key and the pinned Edge signing key, replay check, edgeId, and binding.
     *
     * @param expectedSession the live session (null for hello)
     */
    Received open(String text, String expectedSession) throws Exception {
        ObjectNode outer = (ObjectNode) mapper.readTree(text);
        String type = outer.get("type").asText();
        if (type.equals("ping") || type.equals("pong")) {
            return new Received(type, outer.get("id").asText(), outer.path("corrId").asText(null), null, null, outer);
        }
        byte[] plaintext = hseOpen(Base64.getDecoder().decode(outer.get("sealed").asText()), cpX25519.getPrivate(),
                edgeEd25519, ("hokeka.tunnel." + type).getBytes(StandardCharsets.UTF_8));
        JsonNode p = mapper.readTree(plaintext);
        if (!edgeId.equals(p.get("edgeId").asText())) {
            throw new SecurityException("edgeId mismatch");
        }
        String nonce = p.get("nonce").asText();
        if (!seenNonces.add(nonce)) {
            throw new SecurityException("replayed nonce");
        }
        Instant issued = Instant.parse(p.get("issuedAt").asText());
        if (Math.abs(issued.getEpochSecond() - Instant.now().getEpochSecond()) > 300) {
            throw new SecurityException("skew");
        }
        JsonNode frame = p.get("payload").get("frame");
        String outerCorr = outer.hasNonNull("corrId") ? outer.get("corrId").asText() : null;
        String innerCorr = frame.hasNonNull("corrId") ? frame.get("corrId").asText() : null;
        String session = frame.hasNonNull("sessionId") ? frame.get("sessionId").asText() : null;
        boolean bound = type.equals(frame.get("type").asText()) && outer.get("id").asText().equals(frame.get("id").asText())
                && java.util.Objects.equals(outerCorr, innerCorr) && outer.get("nonce").asText().equals(nonce)
                && issued.toEpochMilli() == outer.get("ts").asLong()
                && (type.equals("hello") ? session == null : session != null && session.equals(expectedSession));
        if (!bound) {
            throw new SecurityException("binding mismatch");
        }
        return new Received(type, outer.get("id").asText(), outerCorr, session,
                (ObjectNode) p.get("payload").get("body"), outer);
    }

    // ── HSE-1 ────────────────────────────────────────────────────────────────────────────────────

    /** HSE-1 seal. */
    public static byte[] hseSeal(byte[] plaintext, byte[] recipientRaw, PrivateKey signer, byte[] context) {
        try {
            KeyPair eph = generate("X25519");
            byte[] epk = raw((XECPublicKey) eph.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(eph.getPrivate());
            ka.doPhase(fromRaw(recipientRaw), true);
            byte[] key = hkdf(ka.generateSecret(), context);
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            byte[] aad = concat(MAGIC, new byte[]{1}, epk, nonce);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            c.updateAAD(aad);
            byte[] ct = c.doFinal(plaintext);
            byte[] header = concat(aad, ByteBuffer.allocate(4).putInt(ct.length).array());
            byte[] signed = concat(header, ct);
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(signer);
            s.update(signed);
            return concat(signed, s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** HSE-1 open. */
    static byte[] hseOpen(byte[] env, PrivateKey recipient, PublicKey sender, byte[] context) throws Exception {
        if (!Arrays.equals(Arrays.copyOf(env, 4), MAGIC) || env[4] != 1) {
            throw new SecurityException("bad header");
        }
        int len = ByteBuffer.wrap(env, 49, 4).getInt();
        if (env.length != 53 + len + 64) {
            throw new SecurityException("bad length");
        }
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(sender);
        s.update(env, 0, 53 + len);
        if (!s.verify(Arrays.copyOfRange(env, 53 + len, env.length))) {
            throw new SecurityException("bad signature");
        }
        byte[] epk = Arrays.copyOfRange(env, 5, 37);
        byte[] nonce = Arrays.copyOfRange(env, 37, 49);
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(recipient);
        ka.doPhase(fromRaw(epk), true);
        byte[] key = hkdf(ka.generateSecret(), context);
        Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
        c.updateAAD(Arrays.copyOfRange(env, 0, 49));
        return c.doFinal(Arrays.copyOfRange(env, 53, 53 + len));
    }

    /** HKDF-SHA256 (RFC 5869) with salt "HSE1" and info "hokeka-hse1-v1" + context, 32 bytes. */
    private static byte[] hkdf(byte[] ikm, byte[] context) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(MAGIC, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update("hokeka-hse1-v1".getBytes(StandardCharsets.US_ASCII));
        mac.update(context);
        mac.update((byte) 1);
        return mac.doFinal();
    }

    /** RFC 7748 little-endian u-coordinate. */
    public static byte[] raw(XECPublicKey key) {
        byte[] be = key.getU().toByteArray();
        byte[] out = new byte[32];
        for (int i = 0; i < 32 && i < be.length; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    /** X25519 public key from its RFC 7748 encoding. */
    private static PublicKey fromRaw(byte[] le) throws Exception {
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = le[31 - i];
        }
        be[0] &= 0x7F;
        return KeyFactory.getInstance("X25519")
                .generatePublic(new XECPublicKeySpec(new NamedParameterSpec("X25519"), new BigInteger(1, be)));
    }

    /** Concatenates byte arrays. */
    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
