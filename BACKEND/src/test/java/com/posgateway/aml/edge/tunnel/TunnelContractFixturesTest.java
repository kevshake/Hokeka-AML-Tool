package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every shared fixture under {@code docs/contracts/tunnel/examples} is validated by the runtime
 * validator ({@link TunnelSchemas}, incl. format assertion and {@code x-hokeka-luhnReject}):
 * valid fixtures produce no violations and every {@code *.invalid.json} fixture is refused, as
 * {@code validate.py} checks for the Python reference implementation (§13).
 */
class TunnelContractFixturesTest {

    private static final TunnelSchemas SCHEMAS = new TunnelSchemas();

    /** Fixture names for the parameterised tests. */
    static Stream<String> fixtures() {
        return ContractFixtures.fixtureNames().stream();
    }

    /** Verifies: fixture validates as declared. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixtureValidatesAsDeclared(String name) {
        JsonNode instance = ContractFixtures.fixture(name);
        List<TunnelSchemas.Violation> violations =
                SCHEMAS.validate(ContractFixtures.schemaFor(name), instance);
        if (name.endsWith(".invalid.json")) {
            assertThat(violations).as("invalid fixture %s must be refused", name).isNotEmpty();
        } else {
            assertThat(violations).as("valid fixture %s", name).isEmpty();
        }
    }

    /** Verifies: every manifest entry names an invalid fixture. */
    @Test
    void everyManifestEntryNamesAnInvalidFixture() throws IOException {
        JsonNode manifest = ContractFixtures.fixture("expected-failures.json");
        manifest.fieldNames().forEachRemaining(n ->
                assertThat(Files.exists(ContractFixtures.examplesDir().resolve(n))).as(n).isTrue());
        try (Stream<Path> s = Files.list(ContractFixtures.examplesDir())) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".invalid.json"))
                    .forEach(n -> assertThat(manifest.has(n)).as("manifest lists %s", n).isTrue());
        }
    }

    /** Verifies: pan like fields are rejected for the luhn reason. */
    @Test
    void panLikeFieldsAreRejectedForTheLuhnReason() {
        List<TunnelSchemas.Violation> v = SCHEMAS.validate("tunnel/error.schema.json",
                ContractFixtures.fixture("error--pan-in-message.invalid.json"));
        assertThat(v).anyMatch(x -> x.keyword().equals(TunnelSchemas.LUHN_KEYWORD)
                && x.pointer().equals("/message"));

        List<TunnelSchemas.Violation> f = SCHEMAS.validate("tunnel/ops/txn.get.item.schema.json",
                ContractFixtures.fixture("txn.get.item--raw-pan-in-feature-value.invalid.json"));
        assertThat(f).anyMatch(x -> x.keyword().equals(TunnelSchemas.LUHN_KEYWORD)
                && x.pointer().equals("/features/memo"));
    }

    /** Verifies: unknown param is reported by pointer only. */
    @Test
    void unknownParamIsReportedByPointerOnly() {
        List<TunnelSchemas.Violation> v = SCHEMAS.validate("tunnel/request.schema.json",
                ContractFixtures.fixture("request--unknown-param.invalid.json"));
        assertThat(v).anyMatch(x -> x.pointer().equals("/params/panHash"));
    }

    /** Verifies: luhn guard matches the reference rule. */
    @Test
    void luhnGuardMatchesTheReferenceRule() {
        assertThat(LuhnGuard.isPanLike("4111 1111 1111 1111")).isTrue();
        assertThat(LuhnGuard.isPanLike("4111-1111-1111-1111")).isTrue();
        assertThat(LuhnGuard.isPanLike("4111111111111112")).isFalse();
        assertThat(LuhnGuard.isPanLike("411111111111")).isFalse();
        assertThat(LuhnGuard.isPanLike("AUTH-2026-10-16-000981")).isFalse();
    }
}
