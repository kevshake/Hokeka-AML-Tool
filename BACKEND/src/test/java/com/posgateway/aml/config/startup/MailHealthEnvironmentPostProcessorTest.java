package com.posgateway.aml.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Mail health must not pull /actuator/health DOWN when SMTP is simply not configured. */
class MailHealthEnvironmentPostProcessorTest {

    private final MailHealthEnvironmentPostProcessor processor = new MailHealthEnvironmentPostProcessor();

    @Test
    void blankHostDisablesMailHealth() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.mail.host", "");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("management.health.mail.enabled")).isEqualTo("false");
    }

    @Test
    void missingHostDisablesMailHealth() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("management.health.mail.enabled")).isEqualTo("false");
    }

    @Test
    void configuredHostKeepsMailHealthOn() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.org");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("management.health.mail.enabled")).isNull();
    }

    @Test
    void explicitOperatorChoiceWins() {
        MockEnvironment env = new MockEnvironment().withProperty("management.health.mail.enabled", "true");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("management.health.mail.enabled")).isEqualTo("true");
    }

    @Test
    void processorIsRegisteredInSpringFactories() throws Exception {
        String factories = Files.readString(Path.of("src/main/resources/META-INF/spring.factories"));
        assertThat(factories).contains(MailHealthEnvironmentPostProcessor.class.getName());
    }
}
