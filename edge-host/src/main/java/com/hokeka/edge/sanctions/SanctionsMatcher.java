package com.hokeka.edge.sanctions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Port of the aml-microservice name matcher ({@code com.hokeka.aml.service.SanctionsService}:
 * {@code normalize}, {@code similarity}, {@code jaccard}, {@code levenshtein}, {@code round}), so the
 * Edge scores a counterparty exactly as the cloud scored it before R10b. Every method is a
 * line-for-line copy; {@code docs/contracts/parity/sanctions-matcher-vectors.json} pins both
 * implementations to the same values ({@code SanctionsMatcherParityVectorsTest} on both sides), so a
 * change to one that is not made to the other fails a build.
 */
public final class SanctionsMatcher {

    private SanctionsMatcher() {
    }

    /** Name normalisation: NFD, strip marks, lower-case, non {@code [a-z0-9 ]} to space, collapse spaces. */
    public static String normalize(String s) {
        if (s == null) return "";
        String decomposed = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Rounds a score to 4 decimals, as the aml-microservice response does. */
    public static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * Hybrid similarity of two normalised names: max of Jaccard token overlap and
     * 1 - (levenshtein / maxLen).
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        double jaccard = jaccard(a, b);
        double lev = 1.0 - ((double) levenshtein(a, b) / Math.max(a.length(), b.length()));
        if (lev < 0) lev = 0;
        return Math.max(jaccard, lev);
    }

    /** Jaccard index of the space-separated token sets. */
    static double jaccard(String a, String b) {
        Set<String> ta = new HashSet<>();
        Collections.addAll(ta, a.split(" "));
        Set<String> tb = new HashSet<>();
        Collections.addAll(tb, b.split(" "));
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    /** Levenshtein edit distance (two-row dynamic programme). */
    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
}
