package com.hokeka.aml.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SanctionsService prefix extraction and new index-aware screening.
 */
class SanctionsServicePrefixTest {

    @Test
    void testExtractPrefixNormalName() {
        assertEquals("jo", SanctionsService.extractPrefix("john smith"), "first 2 chars of 'john smith'");
    }

    @Test
    void testExtractPrefixSingleToken() {
        assertEquals("ah", SanctionsService.extractPrefix("ahmed"), "first 2 chars");
    }

    @Test
    void testExtractPrefixSingleChar() {
        assertEquals("a", SanctionsService.extractPrefix("a"), "single char stays single");
    }

    @Test
    void testExtractPrefixEmpty() {
        assertEquals("", SanctionsService.extractPrefix(""), "empty stays empty");
    }

    @Test
    void testExtractPrefixNull() {
        assertEquals("", SanctionsService.extractPrefix(null), "null stays empty");
    }

    @Test
    void testExtractPrefixAfterNormalize() {
        // Normalize removes special chars and lowercases
        String normalized = "osama bin laden";
        assertEquals("os", SanctionsService.extractPrefix(normalized), "first 2 chars of normalized");
    }

    @Test
    void testSimilarityKnownValues() {
        // Same checks as original SanctionsServiceTest
        assertEquals(1.0, SanctionsService.similarity("mohamed ali", "mohamed ali"), 0.001);
        assertTrue(SanctionsService.similarity("john smith", "xyz corp") < 0.3);
    }
}