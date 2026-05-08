package com.posgateway.aml.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that transparently AES-256-GCM encrypts a
 * String column at write time and decrypts at read time.
 *
 * <p>The 256-bit symmetric key is read from the {@code SECURITY_ENCRYPTION_KEY}
 * environment variable. The key MUST be supplied as a base64-encoded 32-byte
 * value (URL-safe or standard, padded or not). {@code EnvVarStartupValidator}
 * already guards startup against a missing/short key.
 *
 * <p>Wire format (Postgres TEXT column): base64( IV(12 bytes) || GCM ciphertext+tag ).
 * A fresh 12-byte IV is generated per encryption with {@link SecureRandom}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code null}/empty plaintext → stored as {@code null}.</li>
 *   <li>If {@code SECURITY_ENCRYPTION_KEY} is unset at runtime, encryption is
 *       attempted lazily and a {@link RuntimeException} is thrown — startup
 *       validation should prevent this in production. We do NOT silently
 *       fall back to plaintext.</li>
 * </ul>
 */
@Converter
public class AesGcmStringConverter implements AttributeConverter<String, String> {

    private static final String ENV_VAR = "SECURITY_ENCRYPTION_KEY";
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_BYTES   = 12;
    private static final int TAG_BITS   = 128;

    private static final SecureRandom RNG = new SecureRandom();

    /** Lazy holder so we don't try to read the env var at class-load time. */
    private static volatile SecretKey CACHED_KEY;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(dbData);
            if (raw.length <= IV_BYTES) {
                throw new IllegalStateException("Ciphertext too short: " + raw.length + " bytes");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[raw.length - IV_BYTES];
            System.arraycopy(raw, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed: " + e.getMessage(), e);
        }
    }

    private static SecretKey key() {
        SecretKey k = CACHED_KEY;
        if (k != null) return k;
        synchronized (AesGcmStringConverter.class) {
            if (CACHED_KEY != null) return CACHED_KEY;
            String b64 = System.getenv(ENV_VAR);
            if (b64 == null) {
                b64 = System.getProperty(ENV_VAR);
            }
            if (b64 == null || b64.isBlank()) {
                throw new IllegalStateException(ENV_VAR + " is not set; refusing to encrypt PSP secrets");
            }
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(b64.trim());
            } catch (IllegalArgumentException ex) {
                // Allow URL-safe / unpadded encodings as well.
                keyBytes = Base64.getUrlDecoder().decode(b64.trim().replace("=", ""));
            }
            if (keyBytes.length != 32) {
                throw new IllegalStateException(ENV_VAR + " must decode to 32 bytes (got " + keyBytes.length + ")");
            }
            CACHED_KEY = new SecretKeySpec(keyBytes, AES);
            return CACHED_KEY;
        }
    }
}
