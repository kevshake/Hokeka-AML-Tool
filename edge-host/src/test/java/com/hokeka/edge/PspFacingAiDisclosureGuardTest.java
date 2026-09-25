package com.hokeka.edge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guard: shipped edge-host artefacts must not disclose AI vendor, model, or internal gateway names.
 */
class PspFacingAiDisclosureGuardTest {

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("openrouter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjev\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("OPENROUTER_API_KEY", Pattern.CASE_INSENSITIVE),
            Pattern.compile("JEV_MODEL", Pattern.CASE_INSENSITIVE),
            Pattern.compile("anthropic", Pattern.CASE_INSENSITIVE),
            Pattern.compile("openai", Pattern.CASE_INSENSITIVE),
            Pattern.compile("promptVersion|maxTokens|inputTokens|outputTokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("estimatedCost|spendCap|tokenCount", Pattern.CASE_INSENSITIVE));

    @Test
    void edgeHostShippedSourcesDoNotDiscloseAiVendorDetails() throws Exception {
        Path root = Path.of("src/main");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> {
                String name = p.toString();
                return (name.endsWith(".java") || name.endsWith(".yml")
                        || name.endsWith(".yaml") || name.endsWith(".properties"))
                        && !name.contains("RuleBundlePoller")
                        && !name.contains("MetricsShipper")
                        && !name.contains("EdgeEngine");
            }).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    for (Pattern pattern : FORBIDDEN) {
                        assertFalse(pattern.matcher(content).find(),
                                p + " must not disclose AI vendor details (matched " + pattern + ")");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
