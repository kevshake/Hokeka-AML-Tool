package com.posgateway.aml.service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Enhanced Encryption Service
 * 
 * Security improvements:
 * - AES-GCM mode (authenticated encryption) instead of plain AES/ECB
 * - Random IV per encryption (prevents pattern analysis)
 * - Key validation on startup
 * - Proper exception handling
 */
@Service
public class EncryptionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // Recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // Authentication tag bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(
            @Value("${security.encryption.key:}") String keyString) {

        // Validate key on startup
        if (keyString == null || keyString.isEmpty()) {
            log.warn("No encryption key configured. Using default (NOT SECURE FOR PRODUCTION!)");
            keyString = "0123456789ABCDEF0123456789ABCDEF"; // 32 chars = 256 bits
        }

        // Normalize key to 32 bytes (256 bits) for AES-256
        byte[] keyBytes = normalizeKey(keyString);
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.secureRandom = new SecureRandom();

        log.info("EncryptionService initialized with AES-256-GCM");
    }

    /**
     * Encrypt a string value using AES-GCM
     * Output format: Base64(IV + ciphertext + authTag)
     */
    public String encrypt(String value) {
        if (value == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher with GCM
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // Combine IV + encrypted data
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Error encrypting data", e);
        }
    }

    /**
     * Decrypt a string value encrypted with AES-GCM
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }

        try {
            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);

            // Extract IV and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt
            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Error decrypting data", e);
        }
    }

    /**
     * Hash a value for comparison (one-way)
     * Uses HMAC-SHA256 for consistent output
     */
    public String hash(String value) {
        if (value == null) {
            return null;
        }

        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Hashing failed", e);
            throw new EncryptionException("Error hashing data", e);
        }
    }

    /**
     * Normalize key to exactly 32 bytes (256 bits)
     */
    private byte[] normalizeKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] normalizedKey = new byte[32]; // 256 bits for AES-256

        // Pad or truncate to 32 bytes
        System.arraycopy(keyBytes, 0, normalizedKey, 0, Math.min(keyBytes.length, 32));

        return normalizedKey;
    }

    /**
     * Custom exception for encryption errors
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
