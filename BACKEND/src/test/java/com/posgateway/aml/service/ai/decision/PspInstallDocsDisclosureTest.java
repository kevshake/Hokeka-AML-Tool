package com.posgateway.aml.service.ai.decision;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PSP-facing docs must not disclose the AI vendor, model, or internal JEV naming. Any AI output is
 * described to PSPs only as a "Hokeka AI recommendation".
 */
class PspInstallDocsDisclosureTest {

    /** PSP-facing documents, relative to the repository's {@code docs/} directory. */
    private static final List<String> PSP_DOC_FILES = List.of(
            "install/01-client-edge-node.md",
            "edge-client-install-guide.md",
            "PSP_API_GUIDE.md",
            "features/API_INTEGRATION.md",
            "features/TRANSACTION_MONITORING.md");

    /** AI-vendor patterns only — avoids JWT / tokenization examples in API samples. */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("openrouter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjev\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("JEV_MODEL|OPENROUTER_API_KEY|LAYA_API_KEY", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\blaya\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("anthropic|openai|claude|gpt-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("promptVersion|maxTokens|inputTokens|estimatedCost", Pattern.CASE_INSENSITIVE));

    /** Fails when any PSP-facing doc is missing or matches a forbidden AI-vendor pattern. */
    @Test
    void pspDocsDoNotDiscloseAiVendorDetails() throws Exception {
        Path docsDir = Path.of("..", "docs").normalize();
        for (String relative : PSP_DOC_FILES) {
            Path doc = docsDir.resolve(relative);
            assertTrue(Files.isRegularFile(doc), relative + " must exist (PSP-facing doc list is stale)");
            String content = Files.readString(doc);
            for (Pattern pattern : FORBIDDEN) {
                assertFalse(pattern.matcher(content).find(),
                        relative + " must not disclose AI vendor/model details (matched " + pattern + ")");
            }
        }
    }
}
