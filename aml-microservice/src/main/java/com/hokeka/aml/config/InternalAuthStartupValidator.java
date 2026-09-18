package com.hokeka.aml.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-closed under production when the internal auth key is missing (W49-11).
 */
@Component
public class InternalAuthStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthStartupValidator.class);

    private final Environment environment;
    private final String expectedKey;

    public InternalAuthStartupValidator(Environment environment,
                                        @org.springframework.beans.factory.annotation.Value("${aml.internal-auth-key:}") String expectedKey) {
        this.environment = environment;
        this.expectedKey = expectedKey == null ? "" : expectedKey.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionKey() {
        if (!isProduction()) {
            return;
        }
        if (expectedKey.isEmpty()) {
            String msg = "FATAL: aml.internal-auth-key / AML_MS_INTERNAL_KEY must be set under the "
                    + "production profile. Refusing to serve /internal/** without authentication.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    private boolean isProduction() {
        for (String profile : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
