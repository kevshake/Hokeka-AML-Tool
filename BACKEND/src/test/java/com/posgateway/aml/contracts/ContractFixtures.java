package com.posgateway.aml.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the shared data residency contract (docs/contracts) for BACKEND tests: fixtures are read
 * from the repository, never copied (contract §13), and payloads are validated against the real
 * JSON Schemas with a draft 2020-12 validator.
 */
public final class ContractFixtures {

    /** $id prefix of every contract schema. */
    public static final String BASE_URI = "https://contracts.hokeka.com/edge/";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Path contractsDir;
    private static JsonSchemaFactory factory;

    private ContractFixtures() {
    }

    /** {@code docs/contracts}, found by walking up from the working directory. */
    public static synchronized Path contractsDir() {
        if (contractsDir == null) {
            Path dir = Paths.get("").toAbsolutePath();
            while (dir != null && !Files.isDirectory(dir.resolve("docs").resolve("contracts"))) {
                dir = dir.getParent();
            }
            if (dir == null) {
                throw new IllegalStateException("docs/contracts not found above " + Paths.get("").toAbsolutePath());
            }
            contractsDir = dir.resolve("docs").resolve("contracts");
        }
        return contractsDir;
    }

    /** {@code docs/contracts/tunnel/examples}. */
    public static Path examplesDir() {
        return contractsDir().resolve("tunnel").resolve("examples");
    }

    /** Parses one fixture by file name. */
    public static JsonNode fixture(String fileName) {
        return read(examplesDir().resolve(fileName));
    }

    /** Fixture file names starting with {@code prefix}, sorted. */
    public static List<String> fixtureNames(String prefix) {
        try (Stream<Path> files = Files.list(examplesDir())) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code expected-failures.json}: invalid fixture name → expected error substring. */
    public static JsonNode expectedFailures() {
        return fixture("expected-failures.json");
    }

    /** Validation messages of {@code payload} against {@code schemaFile} (relative to docs/contracts). */
    public static Set<String> validate(String schemaFile, JsonNode payload) {
        JsonSchema schema = factory().getSchema(SchemaLocation.of(BASE_URI + schemaFile), config());
        return schema.validate(payload).stream().map(ValidationMessage::getMessage).collect(Collectors.toSet());
    }

    /** Parses a JSON file. */
    public static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A schema factory that resolves contract $ids to the files under docs/contracts. */
    private static synchronized JsonSchemaFactory factory() {
        if (factory == null) {
            String target = contractsDir().toUri().toString();
            factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                    builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(BASE_URI, target)));
        }
        return factory;
    }

    /** Assert formats (date-time, uuid) as the contract's validate.py does. */
    private static SchemaValidatorsConfig config() {
        return SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    }
}
