package com.hokeka.edge.sanctions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-memory index is lossless (identical results to scoring every entry, the aml-microservice
 * scan path) on randomised lists and misspelt queries, at the default and at a low threshold, and
 * screens a large list in bounded time.
 */
class SanctionsIndexTest {

    private static final String[] SYLLABLES = {"al", "an", "ar", "ba", "da", "el", "fa", "ha", "ib", "ka", "li", "ma",
            "mo", "na", "or", "ra", "sa", "ta", "ul", "va", "wa", "ya", "za", "ch", "sh", "ov", "ev", "in"};

    /**
     * A name built from consonant-vowel syllables over the whole alphabet: the bigram spread of real
     * transliterated names (the syllable generator above is deliberately collision-heavy).
     */
    private static String realisticName(Random r) {
        String consonants = "bcdfghjklmnprstvwyzqx";
        String vowels = "aeiou";
        int tokens = 2 + r.nextInt(2);
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < tokens; t++) {
            if (t > 0) {
                sb.append(' ');
            }
            int syllables = 2 + r.nextInt(3);
            for (int s = 0; s < syllables; s++) {
                sb.append(consonants.charAt(r.nextInt(consonants.length())));
                sb.append(vowels.charAt(r.nextInt(vowels.length())));
                if (r.nextInt(4) == 0) {
                    sb.append(consonants.charAt(r.nextInt(consonants.length())));
                }
            }
        }
        return sb.toString();
    }

    /** A pseudo name of 1..4 tokens. */
    private static String name(Random r) {
        int tokens = 1 + r.nextInt(4);
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < tokens; t++) {
            if (t > 0) {
                sb.append(r.nextInt(10) == 0 ? "-" : " ");
            }
            int syllables = 1 + r.nextInt(4);
            for (int s = 0; s < syllables; s++) {
                String syl = SYLLABLES[r.nextInt(SYLLABLES.length)];
                sb.append(s == 0 && t == 0 ? Character.toUpperCase(syl.charAt(0)) + syl.substring(1) : syl);
            }
        }
        return sb.toString();
    }

    /** {@code s} with up to {@code edits} random insert/delete/substitute/transpose edits. */
    private static String misspell(String s, int edits, Random r) {
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < edits && sb.length() > 1; i++) {
            int pos = r.nextInt(sb.length());
            switch (r.nextInt(4)) {
                case 0 -> sb.insert(pos, SYLLABLES[r.nextInt(SYLLABLES.length)].charAt(0));
                case 1 -> sb.deleteCharAt(pos);
                case 2 -> sb.setCharAt(pos, SYLLABLES[r.nextInt(SYLLABLES.length)].charAt(1));
                default -> {
                    if (pos + 1 < sb.length()) {
                        char c = sb.charAt(pos);
                        sb.setCharAt(pos, sb.charAt(pos + 1));
                        sb.setCharAt(pos + 1, c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** A random list of {@code n} entries with 0..3 aliases. */
    private static List<SanctionsEntry> list(int n, Random r) {
        List<SanctionsEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<String> aliases = new ArrayList<>();
            int a = r.nextInt(4);
            String base = name(r);
            for (int k = 0; k < a; k++) {
                aliases.add(r.nextBoolean() ? misspell(base, 1 + r.nextInt(2), r) : name(r));
            }
            out.add(new SanctionsEntry(String.format("ofac-sdn:%06d", i), i % 50 == 0 ? "" : base, aliases,
                    "PERSON", i % 7 == 0 ? "PEP" : null));
        }
        return out;
    }

    /** Asserts index == full scan for queries derived from the list (hits, near misses, noise). */
    private static int assertLossless(SanctionsIndex index, List<SanctionsEntry> entries, int queries, Random r) {
        int withMatches = 0;
        for (int q = 0; q < queries; q++) {
            SanctionsEntry e = entries.get(r.nextInt(entries.size()));
            String base = e.name().isEmpty() && !e.aliases().isEmpty() ? e.aliases().get(0) : e.name();
            String query = switch (q % 5) {
                case 0 -> base;
                case 1 -> misspell(base, 1, r);
                case 2 -> misspell(base, 3, r);
                case 3 -> base.toUpperCase() + " " + name(r);
                default -> name(r);
            };
            List<SanctionsIndex.Match> indexed = index.screen(query);
            assertEquals(index.screenByFullScan(query), indexed, "query '" + query + "'");
            if (!indexed.isEmpty()) {
                withMatches++;
            }
        }
        return withMatches;
    }

    @Test
    void theIndexIsLosslessAtTheDefaultThreshold() {
        Random r = new Random(20261017L);
        List<SanctionsEntry> entries = list(3_000, r);
        SanctionsIndex index = new SanctionsIndex(entries, 0.8);
        int matched = assertLossless(index, entries, 1_500, r);
        assertTrue(matched > 400, "the queries exercise real matches: " + matched);
    }

    @Test
    void theIndexIsLosslessAtALowThresholdAndForVeryShortNames() {
        Random r = new Random(7L);
        List<SanctionsEntry> entries = new ArrayList<>(list(1_500, r));
        entries.add(new SanctionsEntry("ofac-sdn:short-1", "A", List.of("Al", "B"), "PERSON", null));
        SanctionsIndex low = new SanctionsIndex(entries, 0.5);
        assertLossless(low, entries, 800, r);
        SanctionsIndex strict = new SanctionsIndex(entries, 0.8);
        for (String q : List.of("a", "A", "b", "al", "la", "x", "ab", "a l")) {
            assertEquals(strict.screenByFullScan(q), strict.screen(q), q);
            assertEquals(low.screenByFullScan(q), low.screen(q), q);
        }
        assertFalse(strict.screen("A").isEmpty(), "an exact one-letter name still matches");
    }

    @Test
    void screeningALargeListStaysFast() {
        Random r = new Random(99L);
        List<SanctionsEntry> entries = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            String base = realisticName(r);
            List<String> aliases = i % 2 == 0 ? List.of(misspell(base, 1, r), realisticName(r)) : List.of();
            entries.add(new SanctionsEntry(String.format("ofac-sdn:%06d", i), base, aliases, "PERSON", null));
        }
        SanctionsIndex index = new SanctionsIndex(entries, 0.8);
        List<String> queries = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            SanctionsEntry e = entries.get(r.nextInt(entries.size()));
            queries.add(i % 2 == 0 ? misspell(e.name(), 1, r) : realisticName(r));
        }
        for (String q : queries.subList(0, 200)) {
            index.screen(q);
        }
        long start = System.nanoTime();
        for (String q : queries) {
            index.screen(q);
        }
        double avgMicros = (System.nanoTime() - start) / 1_000.0 / queries.size();
        System.out.printf("SanctionsIndex: %d entries, %d variants, %.1f us per screened name%n", index.size(),
                index.variantCount(), avgMicros);
        assertTrue(avgMicros < 2_000, "screening one name against 100k entries averages " + avgMicros + " us");
    }

    @Test
    void anInvalidThresholdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SanctionsIndex(List.of(), 0.0));
        assertThrows(IllegalArgumentException.class, () -> new SanctionsIndex(List.of(), 1.5));
        assertTrue(new SanctionsIndex(List.of(), 0.8).screen("anyone").isEmpty());
    }
}
