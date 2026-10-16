package com.posgateway.aml.config.startup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Turns the Spring Boot {@code mail} health indicator off when no SMTP host is configured.
 *
 * <p>{@code application.properties} binds {@code spring.mail.host=${MAIL_HOST:}}. With MAIL_HOST unset
 * the property is present but blank, so Boot still creates a {@code JavaMailSender} and its health
 * indicator, which then reports DOWN ("failed to connect") and drags the aggregate
 * {@code /actuator/health} — used by Docker and load-balancer probes — to 503 although mail is an
 * optional feature. When the host is blank and the operator has not set
 * {@code management.health.mail.enabled} explicitly, this processor adds a lowest-precedence
 * {@code management.health.mail.enabled=false}. A configured host keeps the indicator on, so a real
 * SMTP outage is still reported.
 */
public class MailHealthEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "hokekaMailHealthDefaults";
    static final String MAIL_HEALTH_ENABLED = "management.health.mail.enabled";

    /** Adds the default only when the host is blank and no explicit choice exists. */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(MAIL_HEALTH_ENABLED)) {
            return;
        }
        String host = environment.getProperty("spring.mail.host");
        if (host == null || host.isBlank()) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(MAIL_HEALTH_ENABLED, "false")));
        }
    }

    /** Run after the config-data processor so application*.properties are already loaded. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
