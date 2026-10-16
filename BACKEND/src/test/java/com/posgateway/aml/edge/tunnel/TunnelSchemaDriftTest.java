package com.posgateway.aml.edge.tunnel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runtime copy of the schemas ({@code BACKEND/src/main/resources/contracts}, needed because
 * the BACKEND Docker build context excludes {@code docs/}) must stay byte-identical to the
 * normative {@code docs/contracts}. Any schema change is made in {@code docs/contracts} first and
 * copied; this test fails until it is.
 */
class TunnelSchemaDriftTest {

    /** Verifies: classpath schemas are byte identical to docs contracts. */
    @Test
    void classpathSchemasAreByteIdenticalToDocsContracts() throws IOException {
        Path root = ContractFixtures.contractsDir();
        List<Path> schemas;
        try (Stream<Path> s = Files.walk(root)) {
            schemas = s.filter(p -> p.getFileName().toString().endsWith(".schema.json")).toList();
        }
        assertThat(schemas).isNotEmpty();
        for (Path p : schemas) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("contracts/" + rel)) {
                assertThat(in).as("classpath copy of %s", rel).isNotNull();
                assertThat(in.readAllBytes()).as("classpath copy of %s", rel).isEqualTo(Files.readAllBytes(p));
            }
        }
    }
}
