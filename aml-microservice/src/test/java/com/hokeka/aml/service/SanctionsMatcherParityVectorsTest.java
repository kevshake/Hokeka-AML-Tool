package com.hokeka.aml.service;

import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.aml.model.SanctionsScreenResponse.MatchDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins this matcher to the shared parity vectors in
 * {@code docs/contracts/parity/sanctions-matcher-vectors.json}. The Edge port
 * ({@code edge-host com.hokeka.edge.sanctions.SanctionsMatcher}) asserts the same file, so both
 * implementations give the same normalisation, pair scores and screening results (R10b). If a
 * change here breaks this test, the Edge port must change in the same commit.
 */
class SanctionsMatcherParityVectorsTest {

    private static final Path VECTORS = Path.of("..", "docs", "contracts", "parity", "sanctions-matcher-vectors.json");

    /** Reads the shared vectors file. */
    private static JsonNode vectors() throws Exception {
        assertTrue(Files.isRegularFile(VECTORS), "parity vectors not found at " + VECTORS.toAbsolutePath());
        return new ObjectMapper().readTree(Files.readString(VECTORS));
    }

    /** Every recorded input normalises to the recorded form. */
    @Test
    void normalisationMatchesTheVectors() throws Exception {
        for (JsonNode v : vectors().get("normalize")) {
            assertEquals(v.get("normalized").asText(), SanctionsService.normalize(v.get("input").asText()),
                    "normalize(" + v.get("input").asText() + ")");
        }
    }

    /** Every recorded pair scores exactly the recorded similarity (no tolerance). */
    @Test
    void pairScoresMatchTheVectors() throws Exception {
        JsonNode pairs = vectors().get("pairs");
        assertTrue(pairs.size() > 100, "the vectors cover a meaningful number of pairs");
        for (JsonNode p : pairs) {
            double actual = SanctionsService.similarity(SanctionsService.normalize(p.get("a").asText()),
                    SanctionsService.normalize(p.get("b").asText()));
            assertEquals(p.get("score").asDouble(), actual, 0.0, p.get("a").asText() + " vs " + p.get("b").asText());
        }
    }

    /**
     * Screening every query against the vector entries through {@code evaluateRecord} (threshold,
     * best-of-name-and-aliases, 4-decimal rounding) and the top-5 cut gives the recorded matches.
     * Equal scores are ordered by entry id, the deterministic order both sides use.
     */
    @Test
    void screeningResultsMatchTheVectors() throws Exception {
        JsonNode root = vectors();
        SanctionsService service = new SanctionsService();
        int topN = root.get("topN").asInt();
        for (JsonNode screen : root.get("screens")) {
            String query = SanctionsService.normalize(screen.get("query").asText());
            List<MatchDto> matches = new ArrayList<>();
            if (!query.isBlank()) {
                for (JsonNode entry : root.get("entries")) {
                    Map<String, Object> bins = new HashMap<>();
                    bins.put("name", entry.get("name").asText());
                    List<String> aliases = new ArrayList<>();
                    entry.get("aliases").forEach(a -> aliases.add(a.asText()));
                    bins.put("aliases", String.join("|", aliases));
                    bins.put("type", entry.get("type").asText());
                    bins.put("entityId", entry.get("id").asText());
                    if (entry.has("pepLevel")) {
                        bins.put("pepLevel", entry.get("pepLevel").asText());
                    }
                    service.evaluateRecord(query, null, new Key("aml_cache", "sanctions", entry.get("id").asText()),
                            new Record(bins, 1, 0), matches);
                }
            }
            matches.sort(Comparator.comparingDouble(MatchDto::getSimilarityScore).reversed()
                    .thenComparing(MatchDto::getEntityId));
            List<MatchDto> top = matches.size() > topN ? matches.subList(0, topN) : matches;
            JsonNode expected = screen.get("matches");
            assertEquals(expected.size(), top.size(), "match count for " + screen.get("query").asText());
            for (int i = 0; i < top.size(); i++) {
                assertEquals(expected.get(i).get("entryId").asText(), top.get(i).getEntityId());
                assertEquals(expected.get(i).get("score").asDouble(), top.get(i).getSimilarityScore(), 0.0);
                assertEquals(expected.get(i).get("matched").asText(), top.get(i).getMatchedName());
            }
        }
    }
}
