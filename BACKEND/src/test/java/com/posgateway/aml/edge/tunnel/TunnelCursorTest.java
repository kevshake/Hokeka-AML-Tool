package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cursor stability (§7.5, §7.13) against the shared fixtures: the JCS {@code q} hash, site
 * cursor minting, and the HMAC-signed composite cursor.
 */
class TunnelCursorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CompositeCursorCodec codec =
            new CompositeCursorCodec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), MAPPER,
                    new TunnelSchemas());

    /** Verifies: query hash of the fixture params is the fixture q. */
    @Test
    void queryHashOfTheFixtureParamsIsTheFixtureQ() {
        JsonNode params = ContractFixtures.fixture("txn.search.params--merchant-window.json");
        String q = ContractFixtures.fixture("cursor--site.json").get("q").asText();
        assertThat(CompositeCursorCodec.queryHash(params)).isEqualTo(q);
        // Member order must not matter (RFC 8785 sorts members).
        JsonNode reordered = ContractFixtures.fixture("request--txn-search.json").get("params");
        assertThat(CompositeCursorCodec.queryHash(reordered)).isEqualTo(q);
    }

    /** Verifies: minted site cursor is the fixture cursor. */
    @Test
    void mintedSiteCursorIsTheFixtureCursor() throws Exception {
        JsonNode fixture = ContractFixtures.fixture("cursor--site.json");
        String minted = codec.mintSiteCursor(fixture.get("q").asText(), fixture.get("k").get(0).asText(),
                fixture.get("k").get(1).asText());
        JsonNode decoded = MAPPER.readTree(Base64.getUrlDecoder().decode(minted));
        assertThat(decoded).isEqualTo(fixture);
        assertThat(minted).isEqualTo(ContractFixtures.fixture("response--search-page.json").get("nextCursor").asText());
        assertThat(minted).isEqualTo(ContractFixtures.fixture("composite-cursor--two-sites.json")
                .get("sites").get("acme-eu").asText());
        assertThat(minted).matches("^[A-Za-z0-9_-]{1,2048}$");
    }

    /** Verifies: composite cursor round trips and validates against its schema. */
    @Test
    void compositeCursorRoundTripsAndValidatesAgainstItsSchema() throws Exception {
        JsonNode fixture = ContractFixtures.fixture("composite-cursor--two-sites.json");
        Map<String, String> sites = new TreeMap<>();
        fixture.get("sites").fields().forEachRemaining(e -> sites.put(e.getKey(), e.getValue().asText()));
        String q = fixture.get("q").asText();
        String token = codec.encode(q, sites);

        assertThat(codec.decode(token, q)).isEqualTo(sites);
        JsonNode body = MAPPER.readTree(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))));
        assertThat(new TunnelSchemas().validate("tunnel/composite-cursor.schema.json", body)).isEmpty();
        assertThat(body).isEqualTo(fixture);
    }

    /** Verifies: tampered foreign or mismatched cursors are invalid cursor. */
    @Test
    void tamperedForeignOrMismatchedCursorsAreInvalidCursor() {
        String q = ContractFixtures.fixture("cursor--site.json").get("q").asText();
        String token = codec.encode(q, Map.of("acme-eu", CompositeCursorCodec.START, "acme-dr", CompositeCursorCodec.DONE));
        String forgedJson = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"v\":1,\"q\":\"" + q + "\",\"sites\":{\"acme-eu\":\"DONE\"}}").getBytes(StandardCharsets.UTF_8));
        String forged = forgedJson + token.substring(token.indexOf('.'));
        CompositeCursorCodec otherSecret = new CompositeCursorCodec(
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8), MAPPER, new TunnelSchemas());

        for (Runnable r : new Runnable[]{
                () -> codec.decode(forged, q),
                () -> otherSecret.decode(token, q),
                () -> codec.decode(token, "0".repeat(64)),
                () -> codec.decode("not-a-cursor", q),
                () -> codec.decode(token + ".x", q)}) {
            assertThatThrownBy(r::run).isInstanceOf(EdgeDataException.class)
                    .extracting(e -> ((EdgeDataException) e).error().code()).isEqualTo(TunnelErrorCode.INVALID_CURSOR);
        }
    }

    /** Verifies: jcs follows rfc8785 for numbers strings and ordering. */
    @Test
    void jcsFollowsRfc8785ForNumbersStringsAndOrdering() throws Exception {
        JsonNode n = MAPPER.readTree("{\"b\":[1.0,1e21,0.000001,1e-7,-0,123.456],\"a\":\"\\u000f\\\"é\",\"€\":true}");
        assertThat(Jcs.canonicalize(n))
                .isEqualTo("{\"a\":\"\\u000f\\\"é\",\"b\":[1,1e+21,0.000001,1e-7,0,123.456],\"€\":true}");
    }
}
