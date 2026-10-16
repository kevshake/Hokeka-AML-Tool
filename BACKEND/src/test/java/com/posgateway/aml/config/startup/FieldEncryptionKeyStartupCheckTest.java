package com.posgateway.aml.config.startup;

import com.posgateway.aml.entity.converter.AesGcmStringConverter;
import com.posgateway.aml.entity.converter.ConverterKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Production fail-closed / non-production warn policy for the field-encryption key. */
class FieldEncryptionKeyStartupCheckTest {

    @AfterEach
    void restore() {
        ConverterKeyTestSupport.useZeroKey();
    }

    @Test
    void productionFailsClosedWithoutLeakingAnyKeyMaterial() {
        assertThatThrownBy(() -> FieldEncryptionKeyStartupCheck.evaluate(AesGcmStringConverter.KeyStatus.MISSING, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not set")
                .hasMessageContaining("Refusing to start in production");
        assertThatThrownBy(() -> FieldEncryptionKeyStartupCheck.evaluate(AesGcmStringConverter.KeyStatus.INVALID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    @Test
    void nonProductionOnlyWarnsAndOkPasses() {
        assertThat(FieldEncryptionKeyStartupCheck.evaluate(AesGcmStringConverter.KeyStatus.MISSING, false))
                .isEqualTo(AesGcmStringConverter.KeyStatus.MISSING);
        assertThat(FieldEncryptionKeyStartupCheck.evaluate(AesGcmStringConverter.KeyStatus.OK, true))
                .isEqualTo(AesGcmStringConverter.KeyStatus.OK);
    }

    @Test
    void verifyKeyBridgesSpringPropertyAndBootsInProduction() {
        assumeTrue(System.getenv("SECURITY_ENCRYPTION_KEY") == null && System.getenv("ENCRYPTION_KEY") == null);
        ConverterKeyTestSupport.clearKey();
        MockEnvironment env = new MockEnvironment()
                .withProperty("security.encryption.key", Base64.getEncoder().encodeToString(new byte[32]));
        env.setActiveProfiles("production");
        assertThatCode(() -> new FieldEncryptionKeyStartupCheck(env).verifyKey()).doesNotThrowAnyException();
        assertThat(AesGcmStringConverter.keyStatus()).isEqualTo(AesGcmStringConverter.KeyStatus.OK);
    }

    @Test
    void verifyKeyAbortsProductionBootWhenNoKeyAnywhere() {
        assumeTrue(System.getenv("SECURITY_ENCRYPTION_KEY") == null && System.getenv("ENCRYPTION_KEY") == null);
        ConverterKeyTestSupport.clearKey();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new FieldEncryptionKeyStartupCheck(env).verifyKey())
                .isInstanceOf(IllegalStateException.class);
    }
}
