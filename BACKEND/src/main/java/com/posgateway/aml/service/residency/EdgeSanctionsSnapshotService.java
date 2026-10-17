package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.sanctions.SanctionsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Builds the sanctions/PEP list snapshots the Edge screens with (R10b, contract section 6.4).
 *
 * <p>{@code SanctionsListDownloadService} feeds every entity it parses from the OpenSanctions
 * download into a {@link Builder} — the same entities it pushes to aml-microservice — so the Edge
 * and the cloud matcher screen against the same names. Entries are written as JSON lines
 * ({@code sanctions-list-entry.schema.json}) into gzip chunks of at most {@value #CHUNK_ENTRIES}
 * entries, stored as they fill, so a million-entity list never sits in memory. When the download
 * completes the snapshot becomes READY, older ones are pruned, and every PSP with Edge Nodes is
 * re-issued its Edge config (whose {@code sanctions} section now names this snapshot), which
 * triggers {@code notify config.updated}.
 *
 * <p>Size: OpenSanctions "default" (≈1.2 M entities with PEPs) is roughly 120–180 MB of JSON lines
 * and ≈30–45 MB gzip, i.e. ≈250 chunks of ≈150 KB; the sanctions-only collection is ≈60 k entities
 * (≈12 chunks). {@code edge.sanctions.snapshot.include-pep=false} leaves PEP/RCA entries out of the
 * Edge snapshot to shrink it, because a PEP match never changes an Edge decision (section 6.5).
 */
@Service
public class EdgeSanctionsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(EdgeSanctionsSnapshotService.class);

    /** Entries per chunk (schema maximum is 10 000). */
    static final int CHUNK_ENTRIES = 5_000;
    /** Most chunks per snapshot (schema maximum). */
    static final int MAX_CHUNKS = 4096;
    /** Snapshots kept (the current one plus the previous, for nodes mid-download). */
    static final int KEEP_SNAPSHOTS = 2;
    /** Longest name or alias carried (schema maxLength); longer strings are left out. */
    static final int MAX_NAME_LENGTH = 512;
    /** Most aliases carried per entry (schema maxItems). */
    static final int MAX_ALIASES = 512;
    /** Manifest {@code format} (contract 6.4). */
    public static final String FORMAT = "hokeka.sanctions.jsonl.gzip.v1";

    private static final Pattern LIST_ID_ILLEGAL = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern ENTRY_ID_ILLEGAL = Pattern.compile("[^A-Za-z0-9._/-]+");
    private static final java.util.Set<String> TYPES = java.util.Set.of("PERSON", "ORGANIZATION", "VESSEL", "UNKNOWN");

    private final EdgeSanctionsSnapshotStore store;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean includePep;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public EdgeSanctionsSnapshotService(EdgeSanctionsSnapshotStore store, ApplicationEventPublisher events,
                                        @Value("${edge.sanctions.snapshot.include-pep:true}") boolean includePep) {
        this(store, events, includePep, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public EdgeSanctionsSnapshotService(EdgeSanctionsSnapshotStore store, ApplicationEventPublisher events,
                                        boolean includePep, Clock clock) {
        this.store = store;
        this.events = events;
        this.includePep = includePep;
        this.clock = clock;
    }

    /** Starts a snapshot build. Call {@link Builder#finish} or {@link Builder#abort} exactly once. */
    public Builder begin() {
        return new Builder();
    }

    /**
     * Streams entities into chunks. Not thread-safe: one download feeds one builder.
     */
    public final class Builder {

        private final List<String> chunkLines = new ArrayList<>();
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private int bufferedEntries;
        private int totalEntries;
        private long snapshotId = -1L;
        private boolean closed;
        private boolean failed;

        private Builder() {
        }

        /**
         * Adds one parsed entity (the map {@code SanctionsListDownloadService.parseSanctionEntity}
         * produces: entityId, name, aliases, type, listName, pepLevel?).
         *
         * @return true when it became an entry, false when it was left out (no usable id or name,
         *         or a PEP while PEPs are excluded)
         */
        public boolean add(Map<String, Object> entity) {
            if (closed) {
                throw new IllegalStateException("snapshot builder is closed");
            }
            if (failed) {
                return false;
            }
            ObjectNode entry = toEntry(entity);
            if (entry == null) {
                return false;
            }
            try {
                buffer.write(mapper.writeValueAsBytes(entry));
                buffer.write('\n');
                bufferedEntries++;
                totalEntries++;
                if (bufferedEntries >= CHUNK_ENTRIES) {
                    flushChunk();
                }
                return true;
            } catch (IOException | RuntimeException e) {
                // A snapshot with a missing chunk would still hash consistently, so any storage or
                // serialisation failure discards the whole build; the aml-microservice ingest the
                // download is also doing is unaffected, and the next download builds a fresh one.
                failed = true;
                log.error("Edge sanctions snapshot build abandoned after {} entries: {}", totalEntries,
                        e.getClass().getSimpleName());
                deleteQuietly();
                return false;
            }
        }

        /** True when the build was abandoned after a failure (finish then publishes nothing). */
        public boolean failed() {
            return failed;
        }

        /** Entries added so far. */
        public int entryCount() {
            return totalEntries;
        }

        /**
         * Completes the snapshot for the recorded list download and publishes it.
         *
         * @param source the {@code sanctions_lists} row of this download (version and id), or null
         * @return the READY snapshot (an existing identical one when nothing changed), or empty when
         *         the download produced no entries
         */
        public Optional<EdgeSanctionsSnapshotStore.Snapshot> finish(SanctionsList source) {
            if (closed) {
                throw new IllegalStateException("snapshot builder is closed");
            }
            closed = true;
            if (failed) {
                log.warn("Edge sanctions snapshot not published: the build was abandoned");
                return Optional.empty();
            }
            if (bufferedEntries > 0) {
                flushChunk();
            }
            if (totalEntries == 0) {
                deleteBuilding();
                log.warn("Edge sanctions snapshot not published: the download produced no entries");
                return Optional.empty();
            }
            String listHash = listHash(chunkLines);
            Optional<EdgeSanctionsSnapshotStore.Snapshot> same = store.readyByHash(listHash);
            if (same.isPresent()) {
                deleteBuilding();
                log.info("Edge sanctions snapshot unchanged ({} entries); keeping list {}", totalEntries,
                        same.get().listVersion());
                return same;
            }
            Instant published = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            String label = EdgeConfigDocumentBuilder.listVersion(source == null ? null : source.getVersion(), published);
            store.markReady(snapshotId, label, listHash, published, totalEntries, chunkLines.size(),
                    source == null ? null : source.getListId());
            int pruned = store.prune(KEEP_SNAPSHOTS);
            log.info("Edge sanctions snapshot {} published: {} entries in {} chunk(s), {} old snapshot(s) pruned",
                    listHash.substring(0, 12), totalEntries, chunkLines.size(), pruned);
            for (Long pspId : store.pspsWithEdgeNodes()) {
                events.publishEvent(new PspDataSettingsChangedEvent(pspId, "SANCTIONS_LIST"));
            }
            return store.readyByHash(listHash);
        }

        /** Discards everything written by this build (the download failed). */
        public void abort() {
            if (closed) {
                return;
            }
            closed = true;
            deleteBuilding();
        }

        /** Gzips the buffered JSON lines, stores them as the next chunk and resets the buffer. */
        private void flushChunk() {
            if (snapshotId < 0) {
                // The download's version is only known at finish(); the label is set by markReady.
                snapshotId = store.createBuilding("building");
            }
            int index = chunkLines.size();
            if (index >= MAX_CHUNKS) {
                throw new IllegalStateException("sanctions list exceeds " + MAX_CHUNKS + " chunks");
            }
            byte[] raw = buffer.toByteArray();
            String sha = CanonicalJson.sha256Hex(raw);
            store.insertChunk(snapshotId, index, sha, bufferedEntries, gzip(raw));
            chunkLines.add(index + ":" + sha + ":" + bufferedEntries);
            buffer = new ByteArrayOutputStream();
            bufferedEntries = 0;
        }

        /** {@link #deleteBuilding()} that never throws (used on the failure path). */
        private void deleteQuietly() {
            try {
                deleteBuilding();
            } catch (RuntimeException e) {
                log.warn("Could not delete the abandoned Edge sanctions snapshot (pruned later): {}",
                        e.getClass().getSimpleName());
                snapshotId = -1L;
            }
        }

        /** Deletes the BUILDING row (and its chunks) when one was created. */
        private void deleteBuilding() {
            if (snapshotId >= 0) {
                store.delete(snapshotId);
                snapshotId = -1L;
            }
        }
    }

    /**
     * {@code listHash} (contract 6.4): SHA-256 hex of one {@code <index>:<sha256>:<entryCount>\n} line
     * per chunk, in index order.
     */
    public static String listHash(List<String> chunkLines) {
        StringBuilder text = new StringBuilder();
        for (String line : chunkLines) {
            text.append(line).append('\n');
        }
        return CanonicalJson.sha256Hex(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Maps a parsed OpenSanctions entity onto a list entry, or null when it cannot be one. The aliases
     * are reproduced exactly as aml-microservice stores and splits them ({@code String.join("|")}
     * then split on {@code |}, blanks dropped), so both matchers see the same strings.
     */
    ObjectNode toEntry(Map<String, Object> entity) {
        if (entity == null) {
            return null;
        }
        String pepLevel = text(entity.get("pepLevel"));
        if (pepLevel != null && !"PEP".equals(pepLevel) && !"RCA".equals(pepLevel)) {
            pepLevel = null;
        }
        if (pepLevel != null && !includePep) {
            return null;
        }
        String id = entryId(text(entity.get("listName")), text(entity.get("entityId")));
        if (id == null) {
            return null;
        }
        String name = text(entity.get("name"));
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            name = "";
        }
        List<String> aliases = new ArrayList<>();
        if (entity.get("aliases") instanceof List<?> raw) {
            List<String> joined = new ArrayList<>();
            for (Object alias : raw) {
                joined.add(alias == null ? "" : alias.toString());
            }
            for (String alias : String.join("|", joined).split("\\|")) {
                if (!alias.isBlank() && alias.length() <= MAX_NAME_LENGTH && aliases.size() < MAX_ALIASES) {
                    aliases.add(alias);
                }
            }
        }
        if (name.isBlank() && aliases.isEmpty()) {
            return null;
        }
        String type = text(entity.get("type"));
        type = type == null ? "UNKNOWN" : type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            type = "UNKNOWN";
        }
        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", id);
        entry.put("name", name);
        ArrayNode list = entry.putArray("aliases");
        aliases.forEach(list::add);
        entry.put("type", type);
        if (pepLevel != null) {
            entry.put("pepLevel", pepLevel);
        }
        return entry;
    }

    /**
     * Public watchlist entry id {@code <listId>:<entryId>} matching
     * {@code common.schema.json#/$defs/watchlistEntryId}: the source dataset lower-cased with
     * {@code _} and other characters turned into {@code -}, then the source entity id with characters
     * outside {@code [A-Za-z0-9._/-]} turned into {@code -}. Null when no usable entity id remains.
     */
    static String entryId(String listName, String entityId) {
        String list = listName == null ? "" : LIST_ID_ILLEGAL.matcher(listName.toLowerCase(Locale.ROOT)).replaceAll("-");
        list = stripLeading(list, "-");
        if (list.length() > 32) {
            list = list.substring(0, 32);
        }
        if (list.isEmpty()) {
            list = "opensanctions";
        }
        String entry = entityId == null ? "" : ENTRY_ID_ILLEGAL.matcher(entityId.trim()).replaceAll("-");
        entry = stripLeading(entry, "._/-");
        if (entry.length() > 96) {
            entry = entry.substring(0, 96);
        }
        return entry.isEmpty() ? null : list + ":" + entry;
    }

    /** Removes leading characters contained in {@code chars}. */
    private static String stripLeading(String value, String chars) {
        int i = 0;
        while (i < value.length() && chars.indexOf(value.charAt(i)) >= 0) {
            i++;
        }
        return value.substring(i);
    }

    /** A non-null string value, or null. */
    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** gzip of {@code raw}. */
    static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException("gzip failed", e);
        }
        return out.toByteArray();
    }
}
