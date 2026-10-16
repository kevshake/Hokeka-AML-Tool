package com.posgateway.aml.entity.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Key resolution status and Spring-property bridging of {@link AesGcmStringConverter}. */
class AesGcmKeyStatusTest {

    @AfterEach
    void restore() {
        ConverterKeyTestSupport.useZeroKey();
    }

    private static void assumeNoEnvKey() {
        assumeTrue(System.getenv("SECURITY_ENCRYPTION_KEY") == null && System.getenv("ENCRYPTION_KEY") == null,
                "an encryption key env var is set on this machine");
    }

    @Test
    void reportsOkMissingAndInvalid() {
        assumeNoEnvKey();
        ConverterKeyTestSupport.useZeroKey();
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.OK);

        ConverterKeyTestSupport.clearKey();
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.MISSING);

        System.setProperty("SECURITY_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(new byte[16]));
        AesGcmStringConverter.resetCachedKey();
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.INVALID);

        System.setProperty("SECURITY_ENCRYPTION_KEY", "not base64 at all !!");
        AesGcmStringConverter.resetCachedKey();
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.INVALID);
    }

    @Test
    void springPropertyKeyIsBridgedWhenNoOtherSourceAndEncryptionWorks() {
        assumeNoEnvKey();
        ConverterKeyTestSupport.clearKey();
        byte[] key = new byte[32];
        key[0] = 7;
        AesGcmStringConverter.configureKeyIfAbsent(Base64.getEncoder().encodeToString(key));
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.OK);
        AesGcmStringConverter converter = new AesGcmStringConverter();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("ACC-1"))).isEqualTo("ACC-1");
    }
}
