package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.entity.sanctions.SanctionsList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * R10b: the Edge list snapshot is built chunk by chunk from the downloaded entities, every entry
 * conforms to sanctions-list-entry.schema.json, the listHash is the contract's hash over the chunk
 * lines, an unchanged re-download publishes nothing new, and a storage failure abandons the build.
 */
class EdgeSanctionsSnapshotServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T02:15:00Z");

    private InMemorySanctionsSnapshotStore store;
    private ApplicationEventPublisher events;
    private EdgeSanctionsSnapshotService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new InMemorySanctionsSnapshotStore();
        store.pspIds.addAll(List.of(12L, 13L));
        events = mock(ApplicationEventPublisher.class);
        service = new EdgeSanctionsSnapshotService(store, events, true, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** One entity as SanctionsListDownloadService.parseSanctionEntity produces it. */
    private static Map<String, Object> entity(String id, String name, List<String> aliases, String pep) {
        Map<String, Object> e = new HashMap<>();
        e.put("entityId", id);
        e.put("name", name);
        e.put("aliases", aliases);
        e.put("type", "PERSON");
        e.put("listName", "us_ofac_sdn");
        e.put("country", "ru");
        if (pep != null) {
            e.put("pepLevel", pep);
        }
        return e;
    }

    /** The download's sanctions_lists row. */
    private static SanctionsList source() {
        SanctionsList row = new SanctionsList();
        row.setListId(9);
        row.setVersion("20261017020000-abc");
        return row;
    }

    @Test
    void entitiesBecomeSchemaValidEntriesInFiveThousandEntryChunks() throws Exception {
        EdgeSanctionsSnapshotService.Builder builder = service.begin();
        for (int i = 0; i < 12_003; i++) {
            assertTrue(builder.add(entity("NK-" + i, "Person " + i, List.of("Alias " + i), i % 3 == 0 ? "PEP" : null)));
        }
        Optional<EdgeSanctionsSnapshotStore.Snapshot> published = builder.finish(source());
        assertTrue(published.isPresent());
        EdgeSanctionsSnapshotStore.Snapshot snapshot = published.get();
        assertEquals(12_003, snapshot.entryCount());
        assertEquals(3, snapshot.chunkCount());
        assertEquals("20261017020000-abc", snapshot.listVersion());

        List<EdgeSanctionsSnapshotStore.ChunkMeta> chunks = store.chunks(snapshot.id());
        assertEquals(List.of(5000, 5000, 2003), chunks.stream().map(EdgeSanctionsSnapshotStore.ChunkMeta::entryCount).toList());
        List<String> lines = new ArrayList<>();
        for (EdgeSanctionsSnapshotStore.ChunkMeta meta : chunks) {
            byte[] raw = gunzip(store.chunk(snapshot.listHash(), meta.index()).orElseThrow().gzip());
            assertEquals(meta.sha256(), CanonicalJson.sha256Hex(raw), "chunk sha256 is over the uncompressed bytes");
            lines.add(meta.index() + ":" + meta.sha256() + ":" + meta.entryCount());
            if (meta.index() == 0) {
                String[] entries = new String(raw, StandardCharsets.UTF_8).split("\n");
                assertEquals(5000, entries.length);
                JsonNode first = mapper.readTree(entries[0]);
                assertEquals("us-ofac-sdn:NK-0", first.get("id").asText(), "watchlist id = listId:entryId");
                assertEquals("PEP", first.get("pepLevel").asText());
                assertFalse(first.has("country"), "only id, name, aliases, type, pepLevel travel");
                for (String line : List.of(entries[0], entries[1])) {
                    Set<String> errors = ContractFixtures.validate("sanctions-list-entry.schema.json", mapper.readTree(line));
                    assertTrue(errors.isEmpty(), line + " " + errors);
                }
            }
        }
        assertEquals(EdgeSanctionsSnapshotService.listHash(lines), snapshot.listHash());
        verify(events, times(2)).publishEvent(any(PspDataSettingsChangedEvent.class));
    }

    @Test
    void anUnchangedReDownloadKeepsTheExistingSnapshot() {
        EdgeSanctionsSnapshotService.Builder first = service.begin();
        first.add(entity("NK-1", "Ivan Petrovich Sidorov", List.of("Ivan Sidorov"), null));
        EdgeSanctionsSnapshotStore.Snapshot one = first.finish(source()).orElseThrow();

        EdgeSanctionsSnapshotService.Builder second = service.begin();
        second.add(entity("NK-1", "Ivan Petrovich Sidorov", List.of("Ivan Sidorov"), null));
        EdgeSanctionsSnapshotStore.Snapshot two = second.finish(source()).orElseThrow();

        assertEquals(one.id(), two.id(), "same content, same snapshot");
        assertEquals(1, store.rows.size(), "the duplicate BUILDING row was deleted");
        verify(events, times(2)).publishEvent(any(PspDataSettingsChangedEvent.class));
    }

    @Test
    void aChunkStorageFailureAbandonsTheWholeBuild() {
        store.failOnChunk = 2;
        EdgeSanctionsSnapshotService.Builder builder = service.begin();
        for (int i = 0; i < 11_000; i++) {
            builder.add(entity("NK-" + i, "Person " + i, List.of(), null));
        }
        assertTrue(builder.failed());
        assertTrue(builder.finish(source()).isEmpty());
        assertTrue(store.rows.isEmpty(), "no partial snapshot is left behind");
        assertTrue(store.latestReady().isEmpty());
        verify(events, never()).publishEvent(any(PspDataSettingsChangedEvent.class));
    }

    @Test
    void entriesMirrorTheAmlMicroserviceAliasStorage() {
        // aml-microservice stores aliases joined by '|' and splits them again: the Edge must see the same strings.
        JsonNode entry = service.toEntry(entity("Q1", "Name", List.of("A|B", " ", "C"), "RCA"));
        assertEquals(List.of("A", "B", "C"), toList(entry.get("aliases")));
        assertEquals("RCA", entry.get("pepLevel").asText());
        assertNull(service.toEntry(entity("", "No Id", List.of(), null)), "no entity id, no entry");
        assertNull(service.toEntry(entity("Q2", " ", List.of(), null)), "nothing to match, no entry");
        EdgeSanctionsSnapshotService noPep = new EdgeSanctionsSnapshotService(store, events, false,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertNull(noPep.toEntry(entity("Q3", "Pep", List.of(), "PEP")), "PEPs left out when configured");
        assertEquals("opensanctions:Q-1", EdgeSanctionsSnapshotService.entryId(null, "Q 1"));
        assertEquals("eu-fsf:EU.55.12", EdgeSanctionsSnapshotService.entryId("EU_FSF", "EU.55.12"));
        assertNull(EdgeSanctionsSnapshotService.entryId("x", "--"));
    }

    @Test
    void anEmptyDownloadPublishesNothing() {
        assertTrue(service.begin().finish(source()).isEmpty());
        assertTrue(store.rows.isEmpty());
    }

    /** Array of strings. */
    private static List<String> toList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** gunzip. */
    static byte[] gunzip(byte[] gz) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return in.readAllBytes();
        }
    }
}
