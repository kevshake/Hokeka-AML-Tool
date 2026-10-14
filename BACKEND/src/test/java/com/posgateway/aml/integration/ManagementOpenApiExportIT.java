package com.posgateway.aml.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exports the springdoc {@code management} group when CI provides Postgres via {@code DATABASE_URL}
 * and sets {@code MANAGEMENT_OPENAPI_EXPORT=true} (docs-site build job).
 */
@EnabledIfEnvironmentVariable(named = "MANAGEMENT_OPENAPI_EXPORT", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("testenv")
class ManagementOpenApiExportIT {

    private static final Path OUTPUT =
            Paths.get("..", "docs-site", "public", "management-api", "openapi.json").normalize();

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DATABASE_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DATABASE_USERNAME", "test"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DATABASE_PASSWORD", "test"));
        registry.add("kafka.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:9092");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        // OpenAPI export only needs the web layer + springdoc; avoid Flyway/Hibernate validate on ephemeral DB.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    /** Writes {@code docs-site/public/management-api/openapi.json} from the live springdoc group. */
    @Test
    void exportsManagementOpenApiDocument() throws Exception {
        ResponseEntity<String> response = fetchManagementOpenApi(restTemplate);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "management OpenAPI must be reachable, got " + response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"openapi\""), "payload must be OpenAPI JSON");
        assertTrue(body.contains("\"\\/users\"") || body.contains("\"/users\""),
                "management spec must include /users paths");

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, body, StandardCharsets.UTF_8);
    }

    /** Fetches the springdoc {@code management} group (context-path aware {@link TestRestTemplate}). */
    static ResponseEntity<String> fetchManagementOpenApi(TestRestTemplate restTemplate) {
        return restTemplate.getForEntity("/api-docs/management", String.class);
    }
}
