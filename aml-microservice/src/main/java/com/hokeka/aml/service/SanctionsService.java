package com.hokeka.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexCollectionType;
import com.aerospike.client.query.Statement;
import com.hokeka.aml.model.SanctionsIngestRequest;
import com.hokeka.aml.model.SanctionsIngestRequest.SanctionsEntity;
import com.hokeka.aml.model.SanctionsScreenResponse;
import com.hokeka.aml.model.SanctionsScreenResponse.MatchDto;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.language.DoubleMetaphone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the {@code aml_cache.sanctions} Aerospike set: bulk ingest + name screening.
 *
 * <p>Screening uses Aerospike secondary indexes for O(log n) candidate retrieval
 * instead of full-set scans. During ingest, the first TWO letters of the normalized
 * name are stored as a {@code prefix} bin. A secondary index
 * ({@code idx_sanctions_prefix}) allows query by that prefix, narrowing the candidate
 * set to ~1/676th of the total — a 676× reduction vs full scan.
 *
 * <p>The index is auto-created at startup via {@link #ensureIndex()} if it doesn't
 * already exist. The fallback when the index creation fails (e.g. insufficient
 * Aerospike privileges) is the original scanAll behaviour.
 *
 * <p>Bins per record: {@code name}, {@code aliases} (CSV), {@code type},
 * {@code listName}, {@code country}, {@code birthDate}, {@code prefix}.
 * Key is the upstream {@code entityId} so re-ingest is idempotent (REPLACE).
 */
@Service
public class SanctionsService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsService.class);
    // W22-2 fix: externalized (was hardcoded, duplicated identically in AmlCheckService and
    // AerospikeCacheService); defaults to the same value so existing deployments are unaffected.
    // Field default (not just the @Value default) matters here since this class is directly
    // instantiated with `new SanctionsService()` in SanctionsAvailabilityTest, bypassing Spring.
    @Value("${aerospike.namespace:aml_cache}")
    private String namespace = "aml_cache";
    private static final String SET_NAME = "sanctions";
    private static final String IDX_SEARCH_KEYS = "idx_sanctions_search_keys_v2";
    private static final String BIN_SEARCH_KEYS = "searchKeys";
    private static final DoubleMetaphone DOUBLE_METAPHONE = new DoubleMetaphone();

    private static final int TOP_N = 5;
    private static final double FLAGGED_THRESHOLD = 0.95;

    /** Aerospike secondary index creation is a cluster admin operation; it may
     *  be unavailable to application-only credentials. We fall back to scan. */
    private boolean useSearchIndex = false;

    @Autowired(required = false)
    private AerospikeClient aerospikeClient;

    @Value("${sanctions.match.similarity.threshold:0.8}")
    private double similarityThreshold;

    // ─────────────────────────────────────────────────────────────────────
    // Startup: create the prefix secondary index if it doesn't exist
    // ─────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void ensureIndex() {
        if (aerospikeClient == null || !aerospikeClient.isConnected()) return;
        try {
            aerospikeClient.createIndex(null, namespace, SET_NAME, IDX_SEARCH_KEYS, BIN_SEARCH_KEYS,
                    com.aerospike.client.query.IndexType.STRING, IndexCollectionType.LIST)
                    .waitTillComplete(2000);
            log.info("Aerospike sanctions search index '{}' ready on {}.{}",
                    IDX_SEARCH_KEYS, namespace, SET_NAME);
            useSearchIndex = true;
        } catch (Exception e) {
            // Index may already exist → try to signal the client to use index anyway
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                useSearchIndex = true;
                log.info("Aerospike secondary index '{}' already exists on {}.{} — using it",
                        IDX_SEARCH_KEYS, namespace, SET_NAME);
            } else {
                useSearchIndex = false;
                log.warn("Cannot create Aerospike secondary index '{}' — falling back to scan. Reason: {}",
                        IDX_SEARCH_KEYS, e.getMessage());
            }
        }
        if (useSearchIndex) {
            int rebuilt = rebuildSearchIndex();
            log.info("Rebuilt sanctions candidate keys for {} Aerospike records", rebuilt);
        }
    }

    public boolean isAerospikeConnected() {
        return aerospikeClient != null && aerospikeClient.isConnected();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Screen a single name. Datastore failures return UNAVAILABLE and are never
     * represented as a clean screening result.
     */
    public SanctionsScreenResponse screenName(String name, String type) {
        Instant checkedAt = Instant.now();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (!isAerospikeConnected()) {
            log.error("Aerospike not connected; sanctions screen for '{}' is unavailable", name);
            return unavailable(name, checkedAt);
        }

        String normalizedQuery = normalize(name);
        List<MatchDto> matches = new ArrayList<>();

        boolean completed;
        if (useSearchIndex) {
            completed = screenBySearchKeys(normalizedQuery, type, matches);
        } else {
            completed = screenByScan(normalizedQuery, type, matches);
        }

        if (!completed) {
            return unavailable(name, checkedAt);
        }

        matches.sort(Comparator.comparingDouble(MatchDto::getSimilarityScore).reversed());
        List<MatchDto> top = matches.size() > TOP_N ? matches.subList(0, TOP_N) : matches;

        String status;
        if (top.isEmpty()) {
            // Fail-closed on empty data: "no matches" is indistinguishable from "the
            // watchlist was never loaded". If the dataset is empty we must NOT report
            // CLEAR (that would clear every entity) — return UNAVAILABLE so callers hold.
            if (!hasSanctionsData()) {
                log.error("Sanctions dataset is EMPTY — screening '{}' fails closed as UNAVAILABLE, not CLEAR", name);
                return unavailable(name, checkedAt);
            }
            status = "CLEAR";
        } else if (top.get(0).getSimilarityScore() >= FLAGGED_THRESHOLD) {
            status = "FLAGGED";
        } else {
            status = "REVIEW";
        }

        return new SanctionsScreenResponse(name, status, new ArrayList<>(top), checkedAt);
    }

    private SanctionsScreenResponse unavailable(String name, Instant checkedAt) {
        return new SanctionsScreenResponse(name, "UNAVAILABLE", new ArrayList<>(), checkedAt);
    }

    // Cached "is the sanctions dataset populated?" check. Guards the CLEAR path against a
    // fail-open on an empty/unloaded watchlist without counting on every clean screen.
    private volatile long lastDataCheckMs = 0L;
    private volatile boolean dataPresentCache = false;
    private static final long DATA_CHECK_TTL_MS = 60_000L;

    private boolean hasSanctionsData() {
        long now = System.currentTimeMillis();
        if (now - lastDataCheckMs > DATA_CHECK_TTL_MS) {
            try {
                dataPresentCache = count() > 0;
            } catch (Exception e) {
                // If we cannot even count, treat as "no data known" → fail closed.
                log.warn("Sanctions data-presence check failed: {}", e.getMessage());
                dataPresentCache = false;
            }
            lastDataCheckMs = now;
        }
        return dataPresentCache;
    }

    /** Index-based query — ~676× fewer records examined than full scan. */
    private boolean screenBySearchKeys(String normalizedQuery, String type, List<MatchDto> matches) {
        Set<String> evaluatedEntities = Collections.synchronizedSet(new HashSet<>());
        try {
            for (String searchKey : buildSearchKeys(normalizedQuery, List.of())) {
                Statement stmt = new Statement();
                stmt.setNamespace(namespace);
                stmt.setSetName(SET_NAME);
                stmt.setBinNames("name", "aliases", "type", "listName", "entityId");
                stmt.setFilter(Filter.contains(BIN_SEARCH_KEYS, IndexCollectionType.LIST, searchKey));
                aerospikeClient.query(null, stmt, (key, record) -> {
                    if (record == null) return;
                    if (evaluatedEntities.add(recordIdentity(key, record))) {
                        evaluateRecord(normalizedQuery, type, key, record, matches);
                    }
                });
            }
            if (evaluatedEntities.isEmpty()) {
                log.info("No indexed sanctions candidates for '{}'; using full scan for recall", normalizedQuery);
                return screenByScan(normalizedQuery, type, matches);
            }
            return true;
        } catch (Exception e) {
            log.warn("Aerospike sanctions candidate query failed for '{}': {}; falling back to scan",
                    normalizedQuery, e.getMessage());
            matches.clear();
            return screenByScan(normalizedQuery, type, matches);
        }
    }

    /** Full scan — original behaviour for fallback. */
    private boolean screenByScan(String normalizedQuery, String type, List<MatchDto> matches) {
        ScanPolicy policy = new ScanPolicy();
        policy.includeBinData = true;
        policy.concurrentNodes = true;
        try {
            aerospikeClient.scanAll(policy, namespace, SET_NAME, (key, record) -> {
                if (record == null) return;
                evaluateRecord(normalizedQuery, type, key, record, matches);
            });
            return true;
        } catch (Exception e) {
            log.error("Aerospike scan failed during sanctions screen for '{}': {}", normalizedQuery, e.getMessage());
            return false;
        }
    }

    /** Shared record evaluation used by both index query and scan. */
    private void evaluateRecord(String normalizedQuery, String type, Key key, Record record, List<MatchDto> matches) {
        String candidateName = record.getString("name");
        String aliasesCsv = record.getString("aliases");
        String candidateType = record.getString("type");

        // Type filter
        if (type != null && !type.isBlank() && candidateType != null
                && !type.equalsIgnoreCase(candidateType)) {
            return;
        }

        double best = 0.0;
        String bestMatched = candidateName;
        if (candidateName != null) {
            best = similarity(normalizedQuery, normalize(candidateName));
        }
        if (aliasesCsv != null && !aliasesCsv.isEmpty()) {
            for (String alias : aliasesCsv.split("\\|")) {
                if (alias.isBlank()) continue;
                double s = similarity(normalizedQuery, normalize(alias));
                if (s > best) {
                    best = s;
                    bestMatched = alias;
                }
            }
        }

        if (best >= similarityThreshold) {
            String entityId = key.userKey != null ? key.userKey.toString()
                    : (record.getString("entityId") != null ? record.getString("entityId") : "");
            String listName = record.getString("listName");
            String pepLevel = record.getString("pepLevel");
            MatchDto match = new MatchDto(bestMatched, round(best), listName, entityId);
            if (pepLevel != null && !pepLevel.isBlank()) {
                match.setPepLevel(pepLevel);
            }
            synchronized (matches) {
                matches.add(match);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Ingest
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bulk-write entities. REPLACE semantics so re-ingest from a fresh OpenSanctions
     * pull cleanly overwrites prior bins. Also writes the {@code prefix} bin for
     * secondary-index query.
     */
    public int ingestEntities(List<SanctionsEntity> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        if (!isAerospikeConnected()) {
            log.warn("Aerospike not connected — sanctions ingest of {} entities SKIPPED", entities.size());
            return 0;
        }

        WritePolicy wp = new WritePolicy();
        wp.recordExistsAction = RecordExistsAction.REPLACE;
        wp.sendKey = true;

        AtomicInteger ok = new AtomicInteger();
        for (SanctionsEntity e : entities) {
            if (e == null || e.getEntityId() == null || e.getEntityId().isBlank()) continue;
            try {
                Key key = new Key(namespace, SET_NAME, e.getEntityId());
                String aliasesCsv = e.getAliases() == null ? "" : String.join("|", e.getAliases());
                List<String> searchKeys = buildSearchKeys(e.getName(), e.getAliases());

                aerospikeClient.put(wp, key,
                        new Bin("entityId", e.getEntityId()),
                        new Bin("name", nullToEmpty(e.getName())),
                        new Bin("aliases", aliasesCsv),
                        new Bin("type", nullToEmpty(e.getType())),
                        new Bin("listName", nullToEmpty(e.getListName())),
                        new Bin("country", nullToEmpty(e.getCountry())),
                        new Bin("birthDate", nullToEmpty(e.getBirthDate())),
                        new Bin("pepLevel", nullToEmpty(e.getPepLevel())),
                        new Bin(BIN_SEARCH_KEYS, searchKeys));
                ok.incrementAndGet();
            } catch (Exception ex) {
                log.warn("Failed to ingest sanctions entity {}: {}", e.getEntityId(), ex.getMessage());
            }
        }
        log.info("Sanctions ingest: {} of {} entities written", ok.get(), entities.size());
        return ok.get();
    }

    public int ingest(SanctionsIngestRequest request) {
        return ingestEntities(request != null ? request.getEntities() : null);
    }

    /**
     * Count records. Uses the secondary index if available for O(log n) count,
     * otherwise scans keys only.
     */
    public long count() {
        if (!isAerospikeConnected()) return -1L;
        if (useSearchIndex) {
            try {
                Statement stmt = new Statement();
                stmt.setNamespace(namespace);
                stmt.setSetName(SET_NAME);
                stmt.setBinNames(BIN_SEARCH_KEYS);
                AtomicInteger c = new AtomicInteger();
                aerospikeClient.query(null, stmt, (key, record) -> c.incrementAndGet());
                return c.get();
            } catch (Exception e) {
                log.warn("Sanctions count query failed: {}", e.getMessage());
                return -1L;
            }
        }
        // Fallback: key-only scan
        ScanPolicy policy = new ScanPolicy();
        policy.includeBinData = false;
        policy.concurrentNodes = true;
        AtomicInteger c = new AtomicInteger();
        try {
            aerospikeClient.scanAll(policy, namespace, SET_NAME, (key, record) -> c.incrementAndGet());
        } catch (Exception e) {
            log.warn("Sanctions count scan failed: {}", e.getMessage());
            return -1L;
        }
        return c.get();
    }

    // -------------------- internals --------------------

    /** First 2 characters of normalized name — our query partition key. */
    static String extractPrefix(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        int end = Math.min(2, normalized.length());
        return normalized.substring(0, end);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String normalize(String s) {
        if (s == null) return "";
        String decomposed = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static List<String> buildSearchKeys(String name, List<String> aliases) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addNameSearchKeys(keys, name);
        if (aliases != null) {
            for (String alias : aliases) addNameSearchKeys(keys, alias);
        }
        return new ArrayList<>(keys);
    }

    private static void addNameSearchKeys(Set<String> keys, String rawName) {
        String normalized = normalize(rawName);
        if (normalized.isEmpty()) return;
        keys.add("e:" + normalized);
        for (String token : normalized.split(" ")) {
            if (token.isBlank()) continue;
            keys.add("p:" + extractPrefix(token));
            addPhoneticKeys(keys, token);
        }
        String compact = normalized.replace(" ", "");
        addPhoneticKeys(keys, compact);
        if (compact.length() == 1) {
            keys.add("g:" + compact);
        } else {
            for (int index = 0; index < compact.length() - 1; index++) {
                keys.add("g:" + compact.substring(index, index + 2));
            }
        }
    }

    private static void addPhoneticKeys(Set<String> keys, String value) {
        String primary = DOUBLE_METAPHONE.doubleMetaphone(value, false);
        String alternate = DOUBLE_METAPHONE.doubleMetaphone(value, true);
        if (primary != null && !primary.isBlank()) keys.add("m:" + primary);
        if (alternate != null && !alternate.isBlank()) keys.add("m:" + alternate);
    }

    public int rebuildSearchIndex() {
        if (!isAerospikeConnected()) return 0;
        AtomicInteger updated = new AtomicInteger();
        ScanPolicy policy = new ScanPolicy();
        policy.includeBinData = true;
        policy.concurrentNodes = true;
        WritePolicy writePolicy = new WritePolicy();
        try {
            aerospikeClient.scanAll(policy, namespace, SET_NAME, (key, record) -> {
                if (record == null) return;
                String aliasesCsv = record.getString("aliases");
                List<String> aliases = aliasesCsv == null || aliasesCsv.isBlank()
                        ? List.of()
                        : List.of(aliasesCsv.split("\\|"));
                List<String> searchKeys = buildSearchKeys(record.getString("name"), aliases);
                if (!searchKeys.isEmpty()) {
                    aerospikeClient.put(writePolicy, key, new Bin(BIN_SEARCH_KEYS, searchKeys));
                    updated.incrementAndGet();
                }
            });
            return updated.get();
        } catch (Exception exception) {
            log.error("Could not rebuild Aerospike sanctions search keys: {}", exception.getMessage(), exception);
            useSearchIndex = false;
            return updated.get();
        }
    }

    private String recordIdentity(Key key, Record record) {
        if (key != null && key.digest != null) {
            return java.util.HexFormat.of().formatHex(key.digest);
        }
        String entityId = record.getString("entityId");
        if (entityId != null && !entityId.isBlank()) return entityId;
        return String.valueOf(System.identityHashCode(record));
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * Hybrid similarity: max of Jaccard token overlap and 1-(levenshtein/maxLen).
     * Cheap, bounded, and dependency-free.
     */
    static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        double jaccard = jaccard(a, b);
        double lev = 1.0 - ((double) levenshtein(a, b) / Math.max(a.length(), b.length()));
        if (lev < 0) lev = 0;
        return Math.max(jaccard, lev);
    }

    private static double jaccard(String a, String b) {
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

    private static int levenshtein(String a, String b) {
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
