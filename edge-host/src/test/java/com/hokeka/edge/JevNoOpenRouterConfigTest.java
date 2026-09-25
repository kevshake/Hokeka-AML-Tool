package com.hokeka.edge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guard: Edge Node must never reference OpenRouter URLs or API key configuration.
 */
class JevNoOpenRouterConfigTest {

    @Test
    void edgeHostHasNoOpenRouterReferences() throws Exception {
        Path root = Path.of("src/main");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".yml")
                            || p.toString().endsWith(".yaml") || p.toString().endsWith(".properties"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            assertFalse(content.contains("openrouter.ai"),
                                    p + " must not reference OpenRouter");
                            assertFalse(content.contains("OPENROUTER_API_KEY"),
                                    p + " must not reference OpenRouter API key");
                            assertFalse(content.contains("JEV_MODEL"),
                                    p + " must not reference JEV model config");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
