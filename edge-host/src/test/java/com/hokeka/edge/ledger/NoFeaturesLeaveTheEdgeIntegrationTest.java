package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.TestTlsKeystore;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.MetricsShipper;
import com.hokeka.edge.channel.OutboxShipper;
import com.hokeka.edge.channel.RuleBundlePoller;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R10c: no transaction data leaves the Edge on {@code ALERT} or {@code HOLD}.
 *
 * <p>The whole production Spring context runs (real controller, engine, ledger, recorder, outbox
 * shipper, metrics shipper, bundle poller, activation) against a real PostgreSQL ledger. Only the
 * transport is swapped: the {@link SecureChannel} bean hands every component an HTTP client aimed at
 * {@link FakeControlPlane}, which records <b>every</b> request on <b>any</b> path. So any bean that
 * tried to call the Control Plane — such as the removed feature-bag POST to
 * {@code /api/v1/edge/decision} — would show up here.
 *
 * <p>After evaluating an ALERT and a HOLD carrying a PAN hash, amount, currency, merchant, country,
 * customer and device data, it asserts that the only outbound requests are the bundle poll, the
 * config poll, the sealed metrics report and the sealed decision-record batch; that the batch is
 * schema-valid and has no forbidden member; and that no decrypted payload contains any of the
 * transaction's values.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NoFeaturesLeaveTheEdgeIntegrationTest {

    private static final String EDGE_ID = "acme-eu-1";
    private static final String SITE_ID = "acme-eu";
    private static final String STORE_PASSWORD = "edge-test-password";

    /** Paths the Edge may call: rule bundle, Edge config, metrics, decision-record batch. */
    private static final Set<String> ALLOWED_PATHS = Set.of("/api/v1/edge/bundle", "/api/v1/edge/config",
            "/api/v1/edge/metrics", OutboxShipper.ASSESSMENTS_PATH);

    /** Members a decision record v2 must never carry (contract §2.2: classes S, E and T). */
    private static final Set<String> FORBIDDEN_MEMBERS = Set.of("pan", "pan_hash", "panHash", "cvv", "track2",
            "amount", "amount_cents", "amountMinor", "currency", "country_code", "countryCode", "mcc", "channel",
            "customer_id", "device_id", "ip_address", "features", "reasons", "cardholder_name");

    /** Transaction values that must not appear in any outbound plaintext. */
    private static final List<String> TRANSACTION_VALUES = List.of("9f2c1d7a-pan-hash", "4111111111111111",
            "125000", "250000", "KES", "KE", "cust-778899", "dev-55aa", "10.20.30.40", "Wanjiru Kamau", "5732");

    private static final String BUNDLE = """
        { "version": 41, "psp_id": 42, "rules": [
          { "id": 100, "name": "VPN hold",
            "condition": { "type": "cmp", "field": "ip_vpn_or_proxy", "op": "EQ", "value": true },
            "action": "HOLD", "score": 25, "priority": 5 },
          { "id": 101, "name": "Large amount alert",
            "condition": { "type": "cmp", "field": "amount_cents", "op": "GT", "value": 200000 },
            "action": "ALERT", "score": 10, "priority": 9 } ] }""";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final KeyPair CONTROL_PLANE_X25519 = EdgeKeys.generateX25519();
    private static final KeyPair CONTROL_PLANE_ED25519 = EdgeKeys.generateEd25519();
    private static final KeyPair EDGE_X25519 = EdgeKeys.generateX25519();
    private static final KeyPair EDGE_ED25519 = EdgeKeys.generateEd25519();

    private static FakeControlPlane cloud;
    private static String schema;
    private static LedgerProperties ledger;
    private static Path identityFile;
    private static Path keystore;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EdgeEngine engine;
    @Autowired
    private RuleBundlePoller poller;
    @Autowired
    private MetricsShipper metricsShipper;
    @Autowired
    private OutboxShipper outboxShipper;

    /**
     * Runs before the Spring context: aborts (skips) the class when no ledger database is reachable,
     * starts the fake Control Plane, writes an ACTIVE node identity and a TLS keystore.
     */
    @BeforeAll
    static void startInfrastructure() throws Exception {
        schema = LedgerTestDatabase.newSchema();
        ledger = LedgerTestDatabase.properties(schema);
        cloud = new FakeControlPlane(CONTROL_PLANE_X25519, EDGE_ED25519.getPublic(), EDGE_ID,
                ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts"))));
        identityFile = Files.createTempDirectory("edge-identity").resolve("edge-identity.json");
        EdgeIdentity identity = new EdgeIdentity(EDGE_ID, "42",
                EdgeKeys.encodePrivate(EDGE_X25519.getPrivate()), EdgeKeys.encodePublic(EDGE_X25519.getPublic()),
                EdgeKeys.encodePrivate(EDGE_ED25519.getPrivate()), EdgeKeys.encodePublic(EDGE_ED25519.getPublic()),
                Instant.now().toString(), EdgeIdentity.STATUS_ACTIVE);
        Files.write(identityFile, MAPPER.writeValueAsBytes(identity));
        keystore = TestTlsKeystore.generate(STORE_PASSWORD);
    }

    /** Stops the fake Control Plane and drops the ledger schema. */
    @AfterAll
    static void stopInfrastructure() {
        if (cloud != null) {
            cloud.close();
        }
        if (schema != null) {
            LedgerTestDatabase.dropSchema(schema);
        }
    }

    /** Production configuration, pointed at the test ledger and the fake Control Plane's keys. */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.ssl.key-store", () -> keystore.toString());
        registry.add("server.ssl.key-store-password", () -> STORE_PASSWORD);
        registry.add("hokeka.controlplane.enabled", () -> "true");
        // Must be https to pass SecureChannel; the transport itself is replaced below.
        registry.add("hokeka.controlplane.base-url", () -> "https://127.0.0.1:1");
        registry.add("hokeka.controlplane.edge-id", () -> EDGE_ID);
        registry.add("hokeka.controlplane.psp-id", () -> "42");
        registry.add("hokeka.controlplane.identity-file", () -> identityFile.toString());
        registry.add("hokeka.controlplane.encryption-public-key",
                () -> EdgeKeys.encodePublic(CONTROL_PLANE_X25519.getPublic()));
        registry.add("hokeka.controlplane.signing-public-key",
                () -> EdgeKeys.encodePublic(CONTROL_PLANE_ED25519.getPublic()));
        registry.add("hokeka.controlplane.bundle-poll-interval", () -> "1h");
        registry.add("hokeka.controlplane.metrics-interval", () -> "1h");
        registry.add("hokeka.controlplane.assessment-ship-interval", () -> "1h");
        registry.add("hokeka.ledger.url", () -> ledger.getUrl());
        registry.add("hokeka.ledger.username", () -> ledger.getUsername());
        registry.add("hokeka.ledger.password", () -> ledger.getPassword());
        registry.add("hokeka.ledger.schema", () -> schema);
        registry.add("hokeka.ledger.site-id", () -> SITE_ID);
        registry.add("hokeka.ledger.pool-size", () -> "4");
        // No Aerospike in this test: let the rules decide instead of FEATURE_STORE_UNAVAILABLE.
        registry.add("featurestore.fail-closed", () -> "false");
    }

    /** Replaces only the transport: every component's mTLS / bootstrap client talks to the fake. */
    @TestConfiguration
    static class FakeTransport {
        /** A SecureChannel whose clients are plain HTTP clients aimed at {@link FakeControlPlane}. */
        @Bean
        @Primary
        SecureChannel fakeControlPlaneChannel(ControlPlaneProperties properties) {
            return new SecureChannel(properties) {
                /** The mTLS client, aimed at the fake. */
                @Override
                public RestClient mutualTlsClient() {
                    return RestClient.builder().baseUrl(cloud.baseUrl()).build();
                }

                /** The enrollment client, aimed at the fake. */
                @Override
                public RestClient bootstrapClient() {
                    return RestClient.builder().baseUrl(cloud.baseUrl()).build();
                }
            };
        }
    }

    /** One transaction body with every kind of data the contract keeps on the Edge. */
    private static Map<String, Object> txn(String txnId, long amountCents, boolean vpn) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("txn_id", txnId);
        f.put("pan_hash", "9f2c1d7a-pan-hash");
        f.put("pan", "4111111111111111");
        f.put("amount_cents", amountCents);
        f.put("currency", "KES");
        f.put("merchant_id", "mrc_00421");
        f.put("mcc", "5732");
        f.put("country_code", "KE");
        f.put("customer_id", "cust-778899");
        f.put("device_id", "dev-55aa");
        f.put("ip_address", "10.20.30.40");
        f.put("cardholder_name", "Wanjiru Kamau");
        f.put("ip_vpn_or_proxy", vpn);
        return f;
    }

    /** POSTs one evaluation through the real MVC stack and returns the response JSON. */
    private JsonNode evaluate(Map<String, Object> body) throws Exception {
        String json = mvc.perform(post("/edge/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readTree(json);
    }

    @Test
    void alertAndHoldSendOnlyTheSealedDecisionRecordAndMetrics() throws Exception {
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));

        JsonNode hold = evaluate(txn("R10C-HOLD-1", 125000L, true));
        JsonNode alert = evaluate(txn("R10C-ALERT-1", 250000L, false));
        assertEquals("HOLD", hold.path("action").asText(), hold.toString());
        assertEquals("ALERT", alert.path("action").asText(), alert.toString());
        for (JsonNode response : List.of(hold, alert)) {
            // The Edge received no AI advisory, so its answer claims none.
            assertFalse(response.toString().contains("AI recommendation"), response.toString());
        }

        // Drive every outbound loop once (they also ran at startup, on the same fake).
        poller.poll();
        metricsShipper.ship();
        assertEquals(OutboxShipper.RunOutcome.DRAINED, outboxShipper.ship());

        List<String> paths = cloud.outboundPaths();
        List<String> unexpected = new ArrayList<>();
        for (String path : paths) {
            if (!ALLOWED_PATHS.contains(path)) {
                unexpected.add(path);
            }
        }
        assertEquals(List.of(), unexpected, "only bundle, config, metrics and the decision-record batch may "
                + "leave the Edge; saw " + paths);
        assertFalse(paths.contains("/api/v1/edge/decision"), "the feature-bag POST is gone");
        assertTrue(paths.contains(OutboxShipper.ASSESSMENTS_PATH), "the decision records were shipped: " + paths);
        assertTrue(paths.contains("/api/v1/edge/metrics"), "metrics were shipped: " + paths);
        assertEquals(List.of(), cloud.problems);

        // Only bodiless GETs or sealed bodies: nothing goes up as plain JSON.
        for (FakeControlPlane.Outbound request : cloud.outbound) {
            if (request.body().length > 0) {
                String head = new String(request.body(), StandardCharsets.ISO_8859_1).trim();
                assertFalse(head.startsWith("{") || head.startsWith("["), request.path() + " sent a plain JSON body");
                assertTrue(Set.of("/api/v1/edge/metrics", OutboxShipper.ASSESSMENTS_PATH).contains(request.path()),
                        request.path() + " sent a body");
            }
        }

        // The decision records: both evaluations, schema-valid (checked by the fake), no forbidden member.
        assertEquals(Set.of(hold.path("edgeAssessmentId").asText(), alert.path("edgeAssessmentId").asText()),
                cloud.receivedIds());
        for (JsonNode record : cloud.received) {
            assertEquals(EDGE_ID, record.path("edgeNodeId").asText());
            assertEquals(SITE_ID, record.path("siteId").asText());
        }

        // Decrypted payloads (batches and metrics): no forbidden member and no transaction value at
        // any depth. Compared structurally, so a UUID or signature that happens to contain "5732"
        // cannot cause a false alarm.
        assertTrue(cloud.openedPayloads.size() >= 2, "a batch and a metrics report were opened");
        for (String plaintext : cloud.openedPayloads) {
            List<String> names = new ArrayList<>();
            List<String> values = new ArrayList<>();
            collect(MAPPER.readTree(plaintext), names, values);
            for (String name : names) {
                assertFalse(FORBIDDEN_MEMBERS.contains(name), "an outbound payload carries " + name + ": " + plaintext);
            }
            for (String value : values) {
                // merchant_id is cloud master data and MAY travel as merchantId (contract §2.2).
                assertFalse(TRANSACTION_VALUES.contains(value), "an outbound payload carries " + value + ": " + plaintext);
            }
        }
    }

    /** Collects every member name and every scalar value (as text) of a JSON tree, at any depth. */
    private static void collect(JsonNode node, List<String> names, List<String> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                names.add(e.getKey());
                collect(e.getValue(), names, values);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names, values));
        } else if (!node.isNull()) {
            values.add(node.asText());
        }
    }
}
