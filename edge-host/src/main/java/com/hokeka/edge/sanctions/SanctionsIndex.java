package com.hokeka.edge.sanctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable in-memory index of one sanctions/PEP list snapshot that screens a name with exactly the
 * aml-microservice result (threshold, best of name and aliases, 4-decimal score, top 5) while
 * scoring only a small candidate set, so {@code /edge/evaluate} latency stays bounded.
 *
 * <p>Candidate retrieval is lossless: every entry the full scan would return is scored. The score is
 * {@code max(jaccard, lev)}, so a pair reaches threshold {@code t} only through one of them:
 * <ul>
 *   <li><b>Jaccard ≥ t</b> ⇒ the token sets overlap in at least {@code ceil(t·|T|)} tokens for either
 *       side's set {@code T}, so (prefix filtering, tokens ordered by ascending list frequency) the
 *       first {@code |T| − ceil(t·|T|) + 1} tokens of both sets share a token. Each variant indexes
 *       its token prefix; a query probes its own.</li>
 *   <li><b>1 − d/M ≥ t</b> (d edit distance, M the longer length) ⇒ {@code d ≤ floor((1−t)·M)} and,
 *       because the other string is at most {@code L/t} long, {@code d ≤ τ(L) = floor((1−t)·L/t)} for a
 *       variant of length L. Partition filter (pigeonhole): split the variant into {@code τ+1}
 *       disjoint segments; d edits touch at most d of them, so one segment occurs unchanged in the
 *       query, starting within {@code τ} of its own offset. Each variant indexes its segments under
 *       (length, segment number, segment text); a query probes, for each feasible length, every
 *       segment at every shift. A variant shorter than {@code τ+1} cannot be partitioned and is
 *       scanned for every query in its length window.</li>
 * </ul>
 * Candidates are first checked cheaply (exact Jaccard; length difference then a banded edit distance
 * that stops past {@code floor((1−t)·M)}), and every entry with a passing variant is scored over the
 * whole entry with {@link SanctionsMatcher}, exactly as {@code SanctionsService.evaluateRecord}, so
 * the index never changes a result ({@code SanctionsIndexTest} compares it with a full scan).
 *
 * <p>Memory is roughly 6 ints per name variant plus the strings; build time is linear in the list.
 */
public final class SanctionsIndex {

    /** Matches returned per screened name (aml-microservice TOP_N). */
    public static final int TOP_N = 5;
    /** Float slack in the filter bounds; always in the direction that keeps more candidates. */
    private static final double EPS = 1e-9;

    /**
     * One screening match.
     *
     * @param entry   the list entry
     * @param score   similarity, rounded to 4 decimals
     * @param matched the listed name or alias that scored best (as listed)
     */
    public record Match(SanctionsEntry entry, double score, String matched) {
    }

    private final double threshold;
    private final SanctionsEntry[] entries;
    private final String[] normName;
    private final String[][] normAliases;
    private final int[] variantEntry;
    private final String[] variantNorm;
    private final Map<String, int[]> tokenPostings;
    private final Map<String, Integer> tokenFrequency;
    private final LongPostings segmentPostings;
    private final Map<Integer, int[]> unpartitionedByLength;

    /**
     * Builds the index.
     *
     * @param list      the snapshot's entries
     * @param threshold similarity threshold (aml-microservice {@code sanctions.match.similarity.threshold})
     */
    public SanctionsIndex(List<SanctionsEntry> list, double threshold) {
        if (!(threshold > 0.0) || threshold > 1.0) {
            throw new IllegalArgumentException("similarity threshold must be in (0, 1]");
        }
        this.threshold = threshold;
        this.entries = list.toArray(new SanctionsEntry[0]);
        this.normName = new String[entries.length];
        this.normAliases = new String[entries.length][];
        List<String> variants = new ArrayList<>();
        IntList owners = new IntList();
        for (int e = 0; e < entries.length; e++) {
            normName[e] = SanctionsMatcher.normalize(entries[e].name());
            List<String> aliases = entries[e].aliases();
            normAliases[e] = new String[aliases.size()];
            Set<String> distinct = new LinkedHashSet<>();
            if (!normName[e].isEmpty()) {
                distinct.add(normName[e]);
            }
            for (int a = 0; a < aliases.size(); a++) {
                normAliases[e][a] = SanctionsMatcher.normalize(aliases.get(a));
                if (!normAliases[e][a].isEmpty()) {
                    distinct.add(normAliases[e][a]);
                }
            }
            for (String v : distinct) {
                variants.add(v);
                owners.add(e);
            }
        }
        this.variantNorm = variants.toArray(new String[0]);
        this.variantEntry = owners.toArray();
        int n = variantNorm.length;

        Map<String, Integer> tokenFreq = new HashMap<>();
        for (String v : variantNorm) {
            for (String t : distinctTokens(v)) {
                tokenFreq.merge(t, 1, Integer::sum);
            }
        }
        this.tokenFrequency = tokenFreq;
        Map<String, IntList> tokens = new HashMap<>();
        Map<Integer, IntList> unpartitioned = new HashMap<>();
        LongPostings.Builder segments = new LongPostings.Builder();
        for (int v = 0; v < n; v++) {
            String s = variantNorm[v];
            String[] t = sortTokens(distinctTokens(s), tokenFreq);
            int tp = tokenPrefix(t.length);
            for (int i = 0; i < tp; i++) {
                tokens.computeIfAbsent(t[i], k -> new IntList()).add(v);
            }
            int length = s.length();
            int parts = tau(length) + 1;
            if (parts > length) {
                unpartitioned.computeIfAbsent(length, k -> new IntList()).add(v);
                continue;
            }
            for (int k = 0; k < parts; k++) {
                int start = segmentStart(length, parts, k);
                int end = segmentStart(length, parts, k + 1);
                segments.add(segmentKey(length, k, s, start, end), v);
            }
        }
        this.tokenPostings = freeze(tokens);
        this.unpartitionedByLength = freeze(unpartitioned);
        this.segmentPostings = segments.build();
    }

    /** Number of entries. */
    public int size() {
        return entries.length;
    }

    /** Number of indexed (distinct, non-empty) name variants. */
    public int variantCount() {
        return variantEntry.length;
    }

    /** The threshold this index screens with. */
    public double threshold() {
        return threshold;
    }

    /**
     * Screens one name: the entries whose best name/alias similarity reaches the threshold, highest
     * score first (equal scores by entry id), at most {@link #TOP_N}.
     *
     * @param rawName the name as received (normalised here)
     */
    public List<Match> screen(String rawName) {
        String q = SanctionsMatcher.normalize(rawName);
        if (q.isEmpty()) {
            return List.of();
        }
        Set<String> queryTokens = new HashSet<>(Arrays.asList(q.split(" ")));
        int[][] candidates = candidates(q);
        Set<Integer> passing = new HashSet<>();
        for (int v : candidates[0]) {
            if (!passing.contains(variantEntry[v]) && jaccardReaches(queryTokens, v)) {
                passing.add(variantEntry[v]);
            }
        }
        for (int v : candidates[1]) {
            if (!passing.contains(variantEntry[v]) && levenshteinReaches(q, v)) {
                passing.add(variantEntry[v]);
            }
        }
        List<Match> matches = new ArrayList<>();
        for (int e : passing) {
            Match m = evaluate(q, e);
            if (m != null) {
                matches.add(m);
            }
        }
        return top(matches);
    }

    /** Full scan over every entry (the aml-microservice scan path); used by tests to prove the index lossless. */
    List<Match> screenByFullScan(String rawName) {
        String q = SanctionsMatcher.normalize(rawName);
        if (q.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (int e = 0; e < entries.length; e++) {
            Match m = evaluate(q, e);
            if (m != null) {
                matches.add(m);
            }
        }
        return top(matches);
    }

    /** Highest score first, equal scores by entry id, at most {@link #TOP_N}. */
    private static List<Match> top(List<Match> matches) {
        matches.sort(Comparator.comparingDouble(Match::score).reversed().thenComparing(m -> m.entry().id()));
        return matches.size() > TOP_N ? List.copyOf(matches.subList(0, TOP_N)) : List.copyOf(matches);
    }

    /**
     * Candidate variants for the normalised query {@code q}: [0] those sharing a token-prefix token
     * (the only ones whose Jaccard index can reach the threshold), [1] those sharing a partition
     * segment at a feasible shift, or unpartitioned in the length window (the only ones whose edit
     * similarity can). Each array is sorted and duplicate-free.
     */
    int[][] candidates(String q) {
        IntList byToken = new IntList();
        String[] tokens = sortTokens(distinctTokens(q), tokenFrequency);
        int tp = tokenPrefix(tokens.length);
        for (int i = 0; i < tp; i++) {
            byToken.addAll(tokenPostings.get(tokens[i]));
        }
        IntList byEdit = new IntList();
        int la = q.length();
        int minLength = Math.max(1, (int) Math.ceil(threshold * la - EPS));
        int maxLength = (int) Math.floor(la / threshold + EPS);
        for (int lb = minLength; lb <= maxLength; lb++) {
            byEdit.addAll(unpartitionedByLength.get(lb));
            int tau = tau(lb);
            int parts = tau + 1;
            if (parts > lb) {
                continue;
            }
            for (int k = 0; k < parts; k++) {
                int offset = segmentStart(lb, parts, k);
                int segLength = segmentStart(lb, parts, k + 1) - offset;
                for (int shift = -tau; shift <= tau; shift++) {
                    int start = offset + shift;
                    if (start < 0 || start + segLength > la) {
                        continue;
                    }
                    byEdit.addAll(segmentPostings.get(segmentKey(lb, k, q, start, start + segLength)));
                }
            }
        }
        return new int[][]{byToken.sortedDistinct(), byEdit.sortedDistinct()};
    }

    /**
     * Whether the variant's Jaccard index with the query tokens reaches the threshold (computed
     * exactly; a small slack keeps borderline pairs for the exact scoring).
     */
    private boolean jaccardReaches(Set<String> queryTokens, int v) {
        Set<String> other = new HashSet<>(Arrays.asList(variantNorm[v].split(" ")));
        int small = Math.min(queryTokens.size(), other.size());
        int large = Math.max(queryTokens.size(), other.size());
        if ((double) small / large < threshold - EPS) {
            return false;
        }
        int inter = 0;
        for (String t : other) {
            if (queryTokens.contains(t)) {
                inter++;
            }
        }
        int union = queryTokens.size() + other.size() - inter;
        return (double) inter / union >= threshold - EPS;
    }

    /**
     * Whether {@code 1 − d/M} can reach the threshold: the length difference and then a banded edit
     * distance bounded by {@code k = floor((1−t)·M)}.
     */
    private boolean levenshteinReaches(String q, int v) {
        String s = variantNorm[v];
        int m = Math.max(q.length(), s.length());
        int k = (int) Math.floor((1.0 - threshold) * m + EPS);
        if (Math.abs(q.length() - s.length()) > k) {
            return false;
        }
        return boundedLevenshtein(q, s, k) <= k;
    }

    /** Edit distance when it is at most {@code k}, otherwise a value greater than {@code k}. */
    static int boundedLevenshtein(String a, String b, int k) {
        int n = a.length();
        int m = b.length();
        int big = k + 1;
        if (Math.abs(n - m) > k) {
            return big;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = Math.min(j, big);
        }
        for (int i = 1; i <= n; i++) {
            Arrays.fill(curr, big);
            curr[0] = Math.min(i, big);
            int from = Math.max(1, i - k);
            int to = Math.min(m, i + k);
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = from; j <= to; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                curr[j] = Math.min(value, big);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > k) {
                return big;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * {@code SanctionsService.evaluateRecord}: best similarity over the listed name then each alias
     * (strictly greater replaces), kept when at or above the threshold, score rounded.
     */
    private Match evaluate(String q, int e) {
        SanctionsEntry entry = entries[e];
        double best = SanctionsMatcher.similarity(q, normName[e]);
        String bestMatched = entry.name();
        List<String> aliases = entry.aliases();
        for (int a = 0; a < aliases.size(); a++) {
            if (aliases.get(a).isBlank()) {
                continue;
            }
            double s = SanctionsMatcher.similarity(q, normAliases[e][a]);
            if (s > best) {
                best = s;
                bestMatched = aliases.get(a);
            }
        }
        return best >= threshold ? new Match(entry, SanctionsMatcher.round(best), bestMatched) : null;
    }

    /** Most edits a partner of a length-{@code length} string can be away while still reaching t. */
    private int tau(int length) {
        return (int) Math.floor((1.0 - threshold) * length / threshold + EPS);
    }

    /** Start of segment {@code k} of an even {@code parts}-way partition of a length-{@code length} string. */
    static int segmentStart(int length, int parts, int k) {
        return (int) ((long) length * k / parts);
    }

    /** Key of (variant length, segment number, segment text); a hash collision only adds candidates. */
    static long segmentKey(int length, int segment, String s, int start, int end) {
        long h = 0xcbf29ce484222325L;
        for (int i = start; i < end; i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= ((long) length << 32) ^ ((long) segment << 48) ^ (end - start);
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 31);
    }

    /** Token prefix length for a set of {@code n} tokens: {@code n − ceil(t·n) + 1}. */
    private int tokenPrefix(int n) {
        if (n == 0) {
            return 0;
        }
        int overlap = Math.max(1, (int) Math.ceil(threshold * n - EPS));
        return Math.min(n, n - overlap + 1);
    }

    /** Distinct space-separated tokens (the Jaccard sets). */
    static String[] distinctTokens(String s) {
        return new LinkedHashSet<>(Arrays.asList(s.split(" "))).toArray(new String[0]);
    }

    /** Tokens in the global order: ascending list frequency, then text. */
    private static String[] sortTokens(String[] tokens, Map<String, Integer> frequency) {
        String[] out = tokens.clone();
        Arrays.sort(out, Comparator.comparingInt((String t) -> frequency.getOrDefault(t, 0))
                .thenComparing(t -> t));
        return out;
    }

    /** Growable int list map → int[] map. */
    private static <K> Map<K, int[]> freeze(Map<K, IntList> lists) {
        Map<K, int[]> out = new HashMap<>(Math.max(16, lists.size() * 4 / 3 + 1));
        lists.forEach((k, v) -> out.put(k, v.toArray()));
        return out;
    }

    /** Minimal growable int array. */
    static final class IntList {
        private int[] values = new int[8];
        private int size;

        /** Appends a value. */
        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        /** Appends every value of {@code more} (null = none). */
        void addAll(int[] more) {
            if (more == null || more.length == 0) {
                return;
            }
            if (size + more.length > values.length) {
                values = Arrays.copyOf(values, Math.max(values.length * 2, size + more.length));
            }
            System.arraycopy(more, 0, values, size, more.length);
            size += more.length;
        }

        /** The values sorted, duplicates removed. */
        int[] sortedDistinct() {
            int[] out = Arrays.copyOf(values, size);
            Arrays.sort(out);
            int w = 0;
            for (int i = 0; i < out.length; i++) {
                if (i == 0 || out[i] != out[i - 1]) {
                    out[w++] = out[i];
                }
            }
            return Arrays.copyOf(out, w);
        }

        /** The values as a right-sized array. */
        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    /**
     * Compact long key → int postings: keys sorted with their postings in one CSR array, looked up
     * by binary search (no boxing, about 12 bytes per key plus 4 per posting).
     */
    static final class LongPostings {
        private final long[] keys;
        private final int[] offsets;
        private final int[] postings;

        private LongPostings(long[] keys, int[] offsets, int[] postings) {
            this.keys = keys;
            this.offsets = offsets;
            this.postings = postings;
        }

        /** Postings of {@code key}, or null. */
        int[] get(long key) {
            int i = Arrays.binarySearch(keys, key);
            return i < 0 ? null : Arrays.copyOfRange(postings, offsets[i], offsets[i + 1]);
        }

        /** Accumulates (key, value) pairs, then sorts them into a {@link LongPostings}. */
        static final class Builder {
            private long[] keys = new long[1024];
            private int[] values = new int[1024];
            private int size;

            /** Adds one pair. */
            void add(long key, int value) {
                if (size == keys.length) {
                    keys = Arrays.copyOf(keys, size * 2);
                    values = Arrays.copyOf(values, size * 2);
                }
                keys[size] = key;
                values[size] = value;
                size++;
            }

            /** Sorts by key and groups. */
            LongPostings build() {
                sort(keys, values, 0, size - 1);
                int distinct = 0;
                for (int i = 0; i < size; i++) {
                    if (i == 0 || keys[i] != keys[i - 1]) {
                        distinct++;
                    }
                }
                long[] k = new long[distinct];
                int[] offsets = new int[distinct + 1];
                int[] postings = Arrays.copyOf(values, size);
                int d = -1;
                for (int i = 0; i < size; i++) {
                    if (i == 0 || keys[i] != keys[i - 1]) {
                        d++;
                        k[d] = keys[i];
                        offsets[d] = i;
                    }
                }
                offsets[distinct] = size;
                return new LongPostings(k, offsets, postings);
            }

            /** In-place quicksort of parallel arrays by key (iterative on the larger side). */
            private static void sort(long[] k, int[] v, int lo, int hi) {
                while (lo < hi) {
                    long pivot = k[(lo + hi) >>> 1];
                    int i = lo;
                    int j = hi;
                    while (i <= j) {
                        while (k[i] < pivot) {
                            i++;
                        }
                        while (k[j] > pivot) {
                            j--;
                        }
                        if (i <= j) {
                            long tk = k[i];
                            k[i] = k[j];
                            k[j] = tk;
                            int tv = v[i];
                            v[i] = v[j];
                            v[j] = tv;
                            i++;
                            j--;
                        }
                    }
                    if (j - lo < hi - i) {
                        sort(k, v, lo, j);
                        lo = i;
                    } else {
                        sort(k, v, i, hi);
                        hi = j;
                    }
                }
            }
        }
    }
}
