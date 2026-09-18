package com.posgateway.aml.config.startup;

import org.springframework.core.env.Environment;

import java.util.function.Predicate;

/**
 * Declarative spec for an environment variable the platform expects to be set.
 * Used by EnvVarStartupValidator to log/report missing values at startup.
 */
record EnvVarSpec(
        String name,
        String description,
        Predicate<Environment> requiredWhen
) {

    /** Required in every profile. */
    static EnvVarSpec required(String name, String description) {
        return new EnvVarSpec(name, description, env -> true);
    }

    /** Required only when the predicate matches the active environment. */
    static EnvVarSpec requiredIf(String name, Predicate<Environment> when, String description) {
        return new EnvVarSpec(name, description, when);
    }

    /** Required only when a related feature toggle is enabled (alias for requiredIf, reads more naturally). */
    static EnvVarSpec requiredWhen(String name, Predicate<Environment> featureEnabled, String description) {
        return new EnvVarSpec(name, description, featureEnabled);
    }

    /** Recommended but not required — boot proceeds without it, with degraded behaviour logged. */
    static EnvVarSpec recommended(String name, String description) {
        return new EnvVarSpec(name, description, env -> false);
    }

    boolean isRequiredForCurrentEnv(Environment env) {
        return requiredWhen.test(env);
    }

    boolean isSecret() {
        String upper = name.toUpperCase();
        return upper.contains("PASSWORD") || upper.contains("SECRET")
                || upper.contains("TOKEN") || upper.contains("KEY");
    }
}
