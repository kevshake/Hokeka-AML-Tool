package com.posgateway.aml.config.startup;

import com.posgateway.aml.entity.converter.AesGcmStringConverter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Boot-time check for the AES-256-GCM key used by {@link AesGcmStringConverter} to encrypt
 * columns at rest (merchant settlement and PSP trust bank account numbers, UBO identifiers,
 * customer e-mail, PSP integration secrets).
 *
 * <p>Runs during context initialisation, before the startup backfills that encrypt legacy
 * rows. It first bridges {@code security.encryption.key} from Spring configuration into the
 * converter (environment variables take precedence), then:
 * <ul>
 *   <li>key OK — one INFO line;</li>
 *   <li>key missing/invalid under {@code prod}/{@code production} — aborts the boot
 *       (fail-closed: the platform must not accept account numbers it cannot encrypt);</li>
 *   <li>key missing/invalid in any other profile — loud WARN: writes to encrypted columns
 *       will fail until a key is supplied.</li>
 * </ul>
 * Log lines name the variables only; the key value is never logged.
 */
@Component
public class FieldEncryptionKeyStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptionKeyStartupCheck.class);

    private final Environment environment;

    /** Uses the Spring environment for the profile and the optional configured key. */
    public FieldEncryptionKeyStartupCheck(Environment environment) {
        this.environment = environment;
    }

    /** Bridges the configured key, evaluates it and fails/warns per profile. */
    @PostConstruct
    void verifyKey() {
        AesGcmStringConverter.configureKeyIfAbsent(environment.getProperty("security.encryption.key", ""));
        AesGcmStringConverter.KeyStatus status = AesGcmStringConverter.keyStatus();
        evaluate(status, isProduction(environment));
    }

    /**
     * Applies the policy for a resolved key status.
     *
     * @return the status, for callers/tests
     * @throws IllegalStateException in production when the key is not usable
     */
    static AesGcmStringConverter.KeyStatus evaluate(AesGcmStringConverter.KeyStatus status, boolean production) {
        if (status == AesGcmStringConverter.KeyStatus.OK) {
            log.info("Field-level encryption key configured (AES-256-GCM); account numbers are encrypted at rest.");
            return status;
        }
        String reason = status == AesGcmStringConverter.KeyStatus.MISSING
                ? "is not set"
                : "is invalid (must be base64 of exactly 32 bytes)";
        String message = "Field-level encryption key (ENCRYPTION_KEY / SECURITY_ENCRYPTION_KEY / "
                + "security.encryption.key) " + reason + ".";
        if (production) {
            throw new IllegalStateException(message + " Refusing to start in production: settlement and "
                    + "trust-account numbers and other PII columns cannot be encrypted at rest.");
        }
        log.warn("{} Writes to encrypted columns (settlement/trust account numbers, UBO identifiers, "
                + "PSP secrets) will fail and legacy plaintext rows will not be encrypted until it is set.", message);
        return status;
    }

    /** True for the prod / production profiles. */
    private static boolean isProduction(Environment env) {
        return Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("production"));
    }
}
