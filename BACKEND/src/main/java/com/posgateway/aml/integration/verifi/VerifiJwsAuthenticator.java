package com.posgateway.aml.integration.verifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies Verifi API 3.0 bearer credentials in either shared-secret JWS mode
 * or certificate mode (PS256 JWS containing an RSA-OAEP-256/AES-GCM JWE).
 */
@Component
public class VerifiJwsAuthenticator {

    private static final long JTI_REPLAY_WINDOW_SECONDS = 360;
    private static final long MAX_IAT_FUTURE_SECONDS = 60;
    private static final long MAX_IAT_PAST_SECONDS = 300;
    private static final long MAX_EXP_AFTER_IAT_SECONDS = 300;
    private static final PSSParameterSpec PS256_PARAMETERS =
            new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    private static final OAEPParameterSpec RSA_OAEP_256_PARAMETERS =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final ObjectMapper objectMapper;
    private final Cache<String, Boolean> recentJti;

    public VerifiJwsAuthenticator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.recentJti = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(JTI_REPLAY_WINDOW_SECONDS))
                .maximumSize(10_000)
                .build();
    }

    /** Backward-compatible shared-secret entry point used by existing callers and tests. */
    public boolean verifyBearerToken(String authorizationHeader, String sharedSecret) {
        VerifiRdrProperties properties = new VerifiRdrProperties();
        properties.setAuthMode("HS256");
        properties.setWebhookSecret(sharedSecret);
        return verifyBearerToken(authorizationHeader, properties);
    }

    public boolean verifyBearerToken(String authorizationHeader, VerifiRdrProperties properties) {
        if (authorizationHeader == null || authorizationHeader.isBlank() || properties == null) return false;
        String token = authorizationHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) return false;

        try {
            JsonNode header = objectMapper.readTree(new String(decode(parts[0]), StandardCharsets.UTF_8));
            String mode = properties.getAuthMode() == null ? "HS256" : properties.getAuthMode().trim().toUpperCase();
            String payload;
            if ("RSA".equals(mode)) {
                if (!"PS256".equals(header.path("alg").asText())
                        || !matchesKid(header, properties.getJwsKeyId())
                        || !verifyPs256(parts, readPublicKey(properties.getJwsVerificationPublicKey()))) {
                    return false;
                }
                String nestedJwe = new String(decode(parts[1]), StandardCharsets.UTF_8);
                payload = decryptNestedJwe(nestedJwe, properties);
            } else if ("HS256".equals(mode)) {
                if (!"HS256".equals(header.path("alg").asText())
                        || properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()
                        || !verifyHs256(parts, properties.getWebhookSecret())) {
                    return false;
                }
                payload = new String(decode(parts[1]), StandardCharsets.UTF_8);
            } else {
                return false;
            }
            return validateClaims(objectMapper.readTree(payload));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean verifyHs256(String[] parts, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return MessageDigest.isEqual(expected, decode(parts[2]));
    }

    private boolean verifyPs256(String[] parts, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.initVerify(publicKey);
        signature.setParameter(PS256_PARAMETERS);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return signature.verify(decode(parts[2]));
    }

    private String decryptNestedJwe(String compactJwe, VerifiRdrProperties properties) throws Exception {
        String[] parts = compactJwe.split("\\.", -1);
        if (parts.length != 5) throw new IllegalArgumentException("A nested compact JWE is required");
        JsonNode header = objectMapper.readTree(new String(decode(parts[0]), StandardCharsets.UTF_8));
        String enc = header.path("enc").asText();
        if (!"RSA-OAEP-256".equals(header.path("alg").asText())
                || !("A256GCM".equals(enc) || "A128GCM".equals(enc))
                || !matchesKid(header, properties.getJweKeyId())) {
            throw new IllegalArgumentException("Unsupported JWE protection parameters");
        }

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, readPrivateKey(properties.getJweDecryptionPrivateKey()),
                RSA_OAEP_256_PARAMETERS);
        byte[] contentEncryptionKey = rsa.doFinal(decode(parts[1]));
        int expectedKeyLength = "A256GCM".equals(enc) ? 32 : 16;
        if (contentEncryptionKey.length != expectedKeyLength) {
            throw new IllegalArgumentException("Invalid JWE content-encryption key length");
        }

        byte[] ciphertext = decode(parts[3]);
        byte[] tag = decode(parts[4]);
        byte[] sealed = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, sealed, 0, ciphertext.length);
        System.arraycopy(tag, 0, sealed, ciphertext.length, tag.length);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"),
                new GCMParameterSpec(128, decode(parts[2])));
        aes.updateAAD(parts[0].getBytes(StandardCharsets.US_ASCII));
        return new String(aes.doFinal(sealed), StandardCharsets.UTF_8);
    }

    private boolean validateClaims(JsonNode claims) {
        String jti = claims.path("jti").asText(null);
        if (jti == null || jti.isBlank() || recentJti.getIfPresent(jti) != null
                || !claims.has("iat") || !claims.has("exp")) return false;

        long now = Instant.now().getEpochSecond();
        long iat = claims.get("iat").asLong(Long.MIN_VALUE);
        long exp = claims.get("exp").asLong(Long.MIN_VALUE);
        if (iat == Long.MIN_VALUE || exp == Long.MIN_VALUE
                || iat > now + MAX_IAT_FUTURE_SECONDS
                || iat < now - MAX_IAT_PAST_SECONDS
                || exp <= now
                || exp > iat + MAX_EXP_AFTER_IAT_SECONDS) return false;

        recentJti.put(jti, Boolean.TRUE);
        return true;
    }

    private static boolean matchesKid(JsonNode header, String expectedKid) {
        return expectedKid == null || expectedKid.isBlank()
                || expectedKid.equals(header.path("kid").asText());
    }

    private static PublicKey readPublicKey(String pem) throws Exception {
        if (pem == null || pem.isBlank()) throw new IllegalArgumentException("JWS verification key is missing");
        String normalized = normalizePem(pem);
        if (normalized.contains("BEGIN CERTIFICATE")) {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(pemBytes(normalized)))
                    .getPublicKey();
        }
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pemBytes(normalized)));
    }

    private static PrivateKey readPrivateKey(String pem) throws Exception {
        if (pem == null || pem.isBlank()) throw new IllegalArgumentException("JWE decryption key is missing");
        String normalized = normalizePem(pem);
        byte[] encoded = pemBytes(normalized);
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) encoded = wrapPkcs1InPkcs8(encoded);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static String normalizePem(String pem) {
        return pem.replace("\\n", "\n").trim();
    }

    private static byte[] pemBytes(String pem) {
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] rsaAlgorithm = der(0x30, concat(
                der(0x06, new byte[]{0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01}),
                der(0x05, new byte[0])));
        return der(0x30, concat(der(0x02, new byte[]{0x00}), rsaAlgorithm, der(0x04, pkcs1)));
    }

    private static byte[] der(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 128) {
            out.write(length);
        } else {
            int bytes = 4 - Integer.numberOfLeadingZeros(length) / 8;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write((length >>> (8 * i)) & 0xff);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) out.writeBytes(array);
        return out.toByteArray();
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
