package com.posgateway.aml.integration.verifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerifiWebhookSignatureVerifierTest {

    private static final String SECRET = "01234567890123456789012345678901";

    private VerifiWebhookSignatureVerifier legacyVerifier;
    private VerifiJwsAuthenticator jwsAuthenticator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        legacyVerifier = new VerifiWebhookSignatureVerifier(mapper);
        jwsAuthenticator = new VerifiJwsAuthenticator(mapper);
    }

    @Test
    void verifyApiKey_acceptsMatchingHeader() {
        assertTrue(legacyVerifier.verifyApiKey(
                Map.of("X-Api-Key", "secret-key"),
                "secret-key"));
    }

    @Test
    void verifyApiKey_rejectsMismatch() {
        assertFalse(legacyVerifier.verifyApiKey(
                Map.of("X-Api-Key", "wrong"),
                "secret-key"));
    }

    @Test
    void verifyBearerToken_acceptsValidJws() throws Exception {
        String token = buildJws(SECRET, "token-1", Instant.now().getEpochSecond());
        assertTrue(jwsAuthenticator.verifyBearerToken("Bearer " + token, SECRET));
    }

    @Test
    void verifyBearerToken_rejectsReplayedJti() throws Exception {
        long now = Instant.now().getEpochSecond();
        String token = buildJws(SECRET, "replay-jti", now);
        assertTrue(jwsAuthenticator.verifyBearerToken("Bearer " + token, SECRET));
        assertFalse(jwsAuthenticator.verifyBearerToken("Bearer " + token, SECRET));
    }

    @Test
    void verifyBearerToken_rejectsWrongSecret() throws Exception {
        String token = buildJws(SECRET, "token-2", Instant.now().getEpochSecond());
        assertFalse(jwsAuthenticator.verifyBearerToken("Bearer " + token, "wrong-secret-wrong-secret-wrong-sec"));
    }

    @Test
    void verifyBearerToken_acceptsPs256SignedNestedRsaJwe() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        VerifiRdrProperties properties = rsaProperties(keyPair);
        String token = buildRsaToken(keyPair, "rsa-token-1", Instant.now().getEpochSecond());

        assertTrue(jwsAuthenticator.verifyBearerToken("Bearer " + token, properties));
        assertFalse(jwsAuthenticator.verifyBearerToken("Bearer " + token, properties), "jti must not be replayable");
    }

    @Test
    void verifyBearerToken_rsaModeRejectsHs256Downgrade() throws Exception {
        VerifiRdrProperties properties = rsaProperties(rsaKeyPair());
        String token = buildJws(SECRET, "downgrade-token", Instant.now().getEpochSecond());

        assertFalse(jwsAuthenticator.verifyBearerToken("Bearer " + token, properties));
    }

    private static String buildJws(String secret, String jti, long iat) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = String.format(
                "{\"jti\":\"%s\",\"iat\":%d,\"exp\":%d}", jti, iat, iat + 300);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private static String buildRsaToken(KeyPair keyPair, String jti, long iat) throws Exception {
        String claims = String.format("{\"jti\":\"%s\",\"iat\":%d,\"exp\":%d}", jti, iat, iat + 300);
        String jweHeader = encode("{\"alg\":\"RSA-OAEP-256\",\"enc\":\"A256GCM\",\"kid\":\"enc-1\"}");
        byte[] cek = new byte[32];
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(cek);
        random.nextBytes(iv);

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] encryptedKey = rsa.doFinal(cek);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, iv));
        aes.updateAAD(jweHeader.getBytes(StandardCharsets.US_ASCII));
        byte[] sealed = aes.doFinal(claims.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = java.util.Arrays.copyOf(sealed, sealed.length - 16);
        byte[] tag = java.util.Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length);
        String jwe = String.join(".", jweHeader, encode(encryptedKey), encode(iv), encode(ciphertext), encode(tag));

        String jwsHeader = encode("{\"alg\":\"PS256\",\"cty\":\"JWE\",\"kid\":\"sig-1\"}");
        String payload = encode(jwe.getBytes(StandardCharsets.UTF_8));
        String signingInput = jwsHeader + "." + payload;
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.initSign(keyPair.getPrivate());
        signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encode(signature.sign());
    }

    private static VerifiRdrProperties rsaProperties(KeyPair keyPair) {
        VerifiRdrProperties properties = new VerifiRdrProperties();
        properties.setAuthMode("RSA");
        properties.setJwsKeyId("sig-1");
        properties.setJweKeyId("enc-1");
        properties.setJwsVerificationPublicKey(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        properties.setJweDecryptionPrivateKey(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        return properties;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String type, byte[] bytes) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
