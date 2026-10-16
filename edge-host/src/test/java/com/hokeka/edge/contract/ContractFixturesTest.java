package com.hokeka.edge.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Edge's schema validator ({@link ContractSchemas}) agrees with the contract on every shared
 * fixture in {@code docs/contracts/tunnel/examples} (contract §13): valid fixtures pass, every
 * {@code *.invalid.json} fails, and the Luhn fixtures fail through the {@code x-hokeka-luhnReject}
 * keyword. Fixtures are read from the source tree, never copied. The fixture → schema mapping is the
 * one {@code docs/contracts/validate.py} uses ({@code <schema stem>--<label>.json}).
 */
class ContractFixturesTest {

    static final Path CONTRACTS = Path.of(System.getProperty("contracts.dir", "../docs/contracts"));
    private static final Path EXAMPLES = CONTRACTS.resolve("tunnel/examples");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas fromSource = ContractSchemas.fromDirectory(CONTRACTS);
    private final ContractSchemas packaged = ContractSchemas.fromClasspath();

    /** Schema path (relative to docs/contracts) for a fixture, by its file-name stem. */
    static String schemaFor(Path fixture) throws IOException {
        String name = fixture.getFileName().toString();
        String stem = name.substring(0, name.indexOf("--"));
        List<String> matches;
        try (Stream<Path> all = Files.walk(CONTRACTS)) {
            matches = all.filter(p -> p.getFileName().toString().equals(stem + ".schema.json"))
                    .map(p -> CONTRACTS.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
        assertEquals(1, matches.size(), "fixture " + name + " must map to exactly one schema");
        return matches.get(0);
    }

    /** One dynamic test per fixture, against the schemas read from the source tree. */
    @TestFactory
    Stream<DynamicTest> everySharedFixtureValidatesAsTheContractSays() throws IOException {
        List<Path> fixtures;
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            fixtures = files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("expected-failures.json"))
                    .sorted()
                    .toList();
        }
        assertTrue(fixtures.size() >= 80, "expected the full fixture set, found " + fixtures.size());
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.getFileName().toString(), () -> {
            JsonNode instance = mapper.readTree(f.toFile());
            String schema = schemaFor(f);
            List<String> errors = fromSource.validateVerbose(schema, instance);
            if (f.getFileName().toString().endsWith(".invalid.json")) {
                assertFalse(errors.isEmpty(), f.getFileName() + " must be rejected by " + schema);
            } else {
                assertTrue(errors.isEmpty(), f.getFileName() + " must pass " + schema + ": " + errors);
            }
        }));
    }

    /** The PAN fixtures are rejected for the PAN reason, i.e. by the Hokeka extension keyword. */
    @Test
    void luhnFixturesFailThroughTheExtensionKeyword() throws IOException {
        JsonNode expected = mapper.readTree(EXAMPLES.resolve("expected-failures.json").toFile());
        List<String> luhnFixtures = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : (Iterable<Map.Entry<String, JsonNode>>) expected::fields) {
            if (e.getValue().asText().contains("looks like a PAN")) {
                luhnFixtures.add(e.getKey());
            }
        }
        assertTrue(luhnFixtures.size() >= 4, "manifest lists the PAN fixtures");
        for (String name : luhnFixtures) {
            Path f = EXAMPLES.resolve(name);
            List<String> errors = fromSource.validateVerbose(schemaFor(f), mapper.readTree(f.toFile()));
            assertTrue(errors.stream().anyMatch(m -> m.contains("looks like a PAN")),
                    name + " must fail with the Luhn guard, got " + errors);
        }
    }

    /** The jar carries the same schemas as the source tree (packaged by the build, not copied by hand). */
    @Test
    void packagedSchemasMatchTheSource() throws IOException {
        for (String fixture : List.of("decision-record-v2--alert.json", "decision-record-v2--fail-closed.json",
                "decision-record-v2--sanctions-hit.json", "decision-record-batch-v2--two-records.json",
                "decision-record-ack-v2--partial.json", "decision-record-v2--pan-hash.invalid.json",
                "decision-record-v2--pan-as-txnref.invalid.json")) {
            Path f = EXAMPLES.resolve(fixture);
            JsonNode instance = mapper.readTree(f.toFile());
            String schema = schemaFor(f);
            assertEquals(fromSource.validate(schema, instance).isEmpty(), packaged.validate(schema, instance).isEmpty(),
                    fixture);
        }
    }
}
