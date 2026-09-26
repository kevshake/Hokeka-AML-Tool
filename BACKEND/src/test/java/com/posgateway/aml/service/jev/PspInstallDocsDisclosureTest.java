package com.posgateway.aml.service.jev;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PSP-facing install docs must not disclose AI vendor, model, or internal JEV naming.
 */
class PspInstallDocsDisclosureTest {

    private static final List<String> PSP_DOC_FILES = List.of(
            "01-client-edge-node.md",
            "06-psp-api-dual-post-integration.md");

    /** AI-vendor patterns only — avoids JWT / tokenization examples in API samples. */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("openrouter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjev\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("JEV_MODEL|OPENROUTER_API_KEY|LAYA_API_KEY", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\blaya\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("anthropic|openai|claude|gpt-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("promptVersion|maxTokens|inputTokens|estimatedCost", Pattern.CASE_INSENSITIVE));

    @Test
    void pspInstallDocsDoNotDiscloseAiVendorDetails() throws Exception {
        Path installDir = Path.of("..", "docs", "install").normalize();
        for (String filename : PSP_DOC_FILES) {
            Path doc = installDir.resolve(filename);
            String content = Files.readString(doc);
            for (Pattern pattern : FORBIDDEN) {
                assertFalse(pattern.matcher(content).find(),
                        filename + " must not disclose AI vendor/model details (matched " + pattern + ")");
            }
        }
    }
}
