package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the shared contract fixtures straight from {@code docs/contracts} (plan "Contract
 * fixtures": BACKEND and edge-host load them rather than copy them). Maven runs tests from
 * {@code BACKEND/}, so the directory is {@code ../docs/contracts}; {@code -Dhokeka.contracts.dir}
 * overrides it.
 */
final class ContractFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();

    /** Utility class. */
    private ContractFixtures() {
    }

    /** Root of the canonical contract tree. */
    static Path contractsDir() {
        String override = System.getProperty("hokeka.contracts.dir");
        Path p = override != null ? Paths.get(override) : Paths.get("..", "docs", "contracts");
        if (!Files.isDirectory(p)) {
            throw new IllegalStateException("contract directory not found: " + p.toAbsolutePath());
        }
        return p;
    }

    /** {@code docs/contracts/tunnel/examples}. */
    static Path examplesDir() {
        return contractsDir().resolve("tunnel").resolve("examples");
    }

    /** Parses one fixture by file name. */
    static JsonNode fixture(String name) {
        try {
            return MAPPER.readTree(examplesDir().resolve(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every fixture file name except the manifest. */
    static List<String> fixtureNames() {
        try (Stream<Path> s = Files.list(examplesDir())) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json") && !n.equals("expected-failures.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Map schema stem → path relative to docs/contracts (validate.py's naming rule). */
    static Map<String, String> schemaByStem() {
        Path root = contractsDir();
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.getFileName().toString().endsWith(".schema.json")).forEach(p -> {
                String file = p.getFileName().toString();
                String stem = file.substring(0, file.length() - ".schema.json".length());
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (out.put(stem, rel) != null) {
                    throw new IllegalStateException("schema stem is not unique: " + stem);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Schema path for a fixture: the part of the name before {@code --}. */
    static String schemaFor(String fixtureName) {
        int i = fixtureName.indexOf("--");
        if (i < 0) {
            throw new IllegalArgumentException("fixture name has no '--': " + fixtureName);
        }
        String stem = fixtureName.substring(0, i);
        String path = schemaByStem().get(stem);
        if (path == null) {
            throw new IllegalArgumentException("no schema for stem " + stem);
        }
        return path;
    }
}
