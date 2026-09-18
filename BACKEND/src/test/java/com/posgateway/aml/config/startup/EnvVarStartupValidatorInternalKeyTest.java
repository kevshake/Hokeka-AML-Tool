package com.posgateway.aml.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class EnvVarStartupValidatorInternalKeyTest {
    @Test
    void productionFailsClosedWithoutInternalKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class,
                () -> EnvVarStartupValidator.failClosedInternalAuthKey(environment));
    }

    @Test
    void productionAcceptsConfiguredInternalKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aml.internal-auth-key", "shared-secret");
        environment.setActiveProfiles("production");
        assertDoesNotThrow(() -> EnvVarStartupValidator.failClosedInternalAuthKey(environment));
    }
}
