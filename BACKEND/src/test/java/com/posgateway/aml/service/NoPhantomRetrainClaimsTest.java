package com.posgateway.aml.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards gap-register item P1-9: the platform has no model trainer and no scheduled
 * retraining job, so production sources must not claim one (e.g. "nightly retrain").
 * Scans the shipped Java sources for such phrases and fails with the offending files.
 */
class NoPhantomRetrainClaimsTest {

    private static final Pattern PHANTOM_RETRAIN = Pattern.compile(
            "(nightly|daily|weekly|scheduled|automatic(ally)?)\\s+re-?train"
                    + "|re-?train(ed|ing)?\\s+(nightly|daily|weekly|every)");

    @Test
    void mainSourcesDoNotClaimAScheduledRetrain() throws IOException {
        Path root = Paths.get("src", "main", "java");
        assertThat(Files.isDirectory(root)).as("run from BACKEND/").isTrue();
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(NoPhantomRetrainClaimsTest::claimsRetrain)
                    .toList();
            assertThat(offenders).as("sources claiming a retrain loop that does not exist").isEmpty();
        }
    }

    /** True when the file text contains a scheduled/nightly retrain claim. */
    private static boolean claimsRetrain(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return PHANTOM_RETRAIN.matcher(text).find();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
