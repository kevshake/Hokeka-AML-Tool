package com.posgateway.aml.entity.converter;

import java.util.Base64;

/**
 * Test helper that controls the {@link AesGcmStringConverter} key via system properties
 * (the same source {@code VersionedAesGcmStringConverterTest} uses) and resets its cache.
 */
public final class ConverterKeyTestSupport {

    private ConverterKeyTestSupport() {
    }

    /** Installs the all-zero 32-byte test key and clears the cached key. */
    public static void useZeroKey() {
        System.setProperty("SECURITY_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(new byte[32]));
        AesGcmStringConverter.resetCachedKey();
    }

    /** Removes the system-property key sources and clears the cached key. */
    public static void clearKey() {
        System.clearProperty("SECURITY_ENCRYPTION_KEY");
        System.clearProperty("security.encryption.key");
        AesGcmStringConverter.resetCachedKey();
    }
}
