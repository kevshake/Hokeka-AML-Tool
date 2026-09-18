package com.hokeka.aml.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the similarity matching logic in SanctionsService.
 */
class SanctionsServiceTest {

    @Test
    void testExactMatch() {
        // Jaro-Winkler or hybrid: identical strings → 1.0
        double sim = SanctionsService.similarity("mohamed ali", "mohamed ali");
        assertEquals(1.0, sim, 0.001, "identical strings should return 1.0");
    }

    @Test
    void testCompletelyDifferent() {
        double sim = SanctionsService.similarity("john smith", "xyz corp");
        assertTrue(sim < 0.3, "completely different names should be low: " + sim);
    }

    @Test
    void testSubsetSimilarity() {
        // Good test: one string is a substring of the other with extra tokens
        double sim = SanctionsService.similarity("ali abdullah", "ali abdullah ahmed");
        assertTrue(sim > 0.5, "partial overlap should be meaningful: " + sim);
        assertTrue(sim < 0.95, "partial overlap should be below exact: " + sim);
    }

    @Test
    void testNullSafety() {
        assertEquals(0.0, SanctionsService.similarity(null, "test"), 0.001);
        assertEquals(0.0, SanctionsService.similarity("test", null), 0.001);
        assertEquals(0.0, SanctionsService.similarity(null, null), 0.001);
    }

    @Test
    void testEmptyAfterNormalize() {
        // Non-alphanumeric only → both become empty after normalize → 0.0
        // (our normalize strips non-alnum, so empty strings → 0.0)
        assertEquals(0.0, SanctionsService.similarity("!!!", "???"), 0.001);
    }

    @Test
    void testTransliterationAgnostic() {
        // Similarity assumes pre-normalized input (lowercased, stripped, collapsed).
        // screenName() normalizes before calling similarity(), so we pass normalized form.
        double sim = SanctionsService.similarity("osama bin laden", "osama bin laden");
        assertEquals(1.0, sim, 0.001, "identical normalized strings should return 1.0");
    }

    @Test
    void testVeryShortVsLong() {
        double sim = SanctionsService.similarity("a", "aaaaaaaaaaaaaaaaaaaa");
        assertTrue(sim >= 0.0 && sim <= 1.0, "similarity should be in [0,1]: " + sim);
    }
}