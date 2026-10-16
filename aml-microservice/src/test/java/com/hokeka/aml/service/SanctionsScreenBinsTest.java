package com.hokeka.aml.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression (sim run 2026-10-16): the indexed screening query did not project the "pepLevel" bin,
 * so PEP/RCA matches were returned with pepLevel=null and classified as sanctions hits.
 */
class SanctionsScreenBinsTest {

    @Test
    void indexQueryProjectsPepLevel() {
        assertTrue(Arrays.asList(SanctionsService.SCREEN_BINS).contains("pepLevel"));
    }

    @Test
    void indexQueryProjectsEveryBinThatEvaluateRecordReads() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/hokeka/aml/service/SanctionsService.java"))
                .replace("\r\n", "\n");
        int start = src.indexOf("void evaluateRecord(");
        int end = src.indexOf("\n    }\n", start);
        Matcher m = Pattern.compile("record\\.getString\\(\"([A-Za-z]+)\"\\)").matcher(src.substring(start, end));
        Set<String> read = new LinkedHashSet<>();
        while (m.find()) {
            read.add(m.group(1));
        }
        assertTrue(!read.isEmpty(), "evaluateRecord reads bins");
        assertTrue(Arrays.asList(SanctionsService.SCREEN_BINS).containsAll(read),
                "SCREEN_BINS " + Arrays.toString(SanctionsService.SCREEN_BINS) + " must cover " + read);
    }
}
