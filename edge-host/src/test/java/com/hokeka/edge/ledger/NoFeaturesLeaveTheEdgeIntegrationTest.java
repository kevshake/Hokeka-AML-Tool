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
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.residency.EdgeConfigPoller;
import com.hokeka.edge.sanctions.SanctionsListPoller;
import com.hokeka.edge.sanctions.SanctionsListStore;
import com.hokeka.edge.sanctions.SanctionsTestLists;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    /** Transaction values that must not appear in any outbound plaintext (R10b: counterparty names too). */
    private static final List<String> TRANSACTION_VALUES = List.of("9f2c1d7a-pan-hash", "4111111111111111",
            "125000", "250000", "KES", "KE", "cust-778899", "dev-55aa", "10.20.30.40", "Wanjiru Kamau", "5732",
            "Ivan Sidorov", "Northwind Shiping", "Jean Normal");

    /** Names that must not appear anywhere in any outbound plaintext, not even inside another value. */
    private static final List<String> NAMES = List.of("Ivan Sidorov", "Sidorov", "Northwind", "Wanjiru", "Kamau",
            "Jean Normal");

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
    @Autowired
    private EdgeConfigPoller configPoller;
    @Autowired
    private SanctionsListPoller sanctionsPoller;
    @Autowired
    private SanctionsListStore sanctionsLists;

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

    /** HSE-1 seals {@code payload} for this Edge as the Control Plane does (CP signing key, edge X25519). */
    private static byte[] sealForEdge(JsonNode payload, String context) {
        return new SealedEnvelopeCodec(MAPPER, new ReplayGuard()).seal(payload, EDGE_ID,
                EdgeKeys.x25519RawPublic(EDGE_X25519.getPublic()), CONTROL_PLANE_ED25519.getPrivate(), context,
                Instant.now());
    }

    /**
     * R10b: the fake Control Plane serves a sealed Edge config naming a sanctions list and blocking
     * one merchant, plus that list's sealed manifest and chunks.
     */
    private SanctionsTestLists.Built serveConfigAndSanctionsList() throws Exception {
        SanctionsTestLists.Built list = SanctionsTestLists.build(List.of(
                SanctionsTestLists.entry("us-ofac-sdn:NK-1001", "Ivan Petrovich Sidorov", List.of("Ivan Sidorov"),
                        "PERSON", null),
                SanctionsTestLists.entry("gb-hmt:7788", "Northwind Shipping Company Limited",
                        List.of("Northwind Shipping"), "ORGANIZATION", null),
                SanctionsTestLists.entry("peps:Q44", "Wanjiru Kamau", List.of(), "PERSON", "PEP")),
                2, "20261017-0200", "2026-10-17T02:00:00Z");
        ObjectNode config = (ObjectNode) MAPPER.readTree(Files.readString(Path.of(
                System.getProperty("contracts.dir", "../docs/contracts"), "tunnel", "examples", "edge-config--default.json")));
        config.put("configVersion", 1);
        config.put("pspId", 42);
        // R10a enforces limits[] against site counters in Aerospike, which this test runs without:
        // unreadable counters would answer HOLD / LIMITS_UNAVAILABLE (fail visibly, covered by
        // ComplianceOverlayTest) and hide the rule-driven ALERT this test needs. No limits here.
        config.putArray("limits");
        ObjectNode sanctions = (ObjectNode) config.get("sanctions");
        sanctions.put("listVersion", "20261017-0200");
        sanctions.put("listHash", list.listHash);
        sanctions.put("publishedAt", "2026-10-17T02:00:00Z");
        ObjectNode block = ((ArrayNode) config.get("merchantBlocklist").get("entries")).addObject();
        block.put("merchantId", "mrc_blocked");
        block.put("reasonCode", "SANCTIONED");
        block.put("since", "2026-10-17T03:00:00Z");
        cloud.served.put(EdgeConfigPoller.CONFIG_PATH, () -> sealForEdge(config, EdgeConfigPoller.CTX_EDGE_CONFIG));
        cloud.served.put(SanctionsListPoller.LIST_PATH, () -> sealForEdge(list.manifest, SanctionsListPoller.CTX_LIST));
        for (int i = 0; i < list.chunks.size(); i++) {
            ObjectNode chunk = list.chunks.get(i);
            cloud.served.put(SanctionsListPoller.LIST_PATH + "/" + list.listHash + "/chunks/" + i,
                    () -> sealForEdge(chunk, SanctionsListPoller.CTX_CHUNK));
        }
        return list;
    }

    @Test
    void alertAndHoldSendOnlyTheSealedDecisionRecordAndMetrics() throws Exception {
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));
        SanctionsTestLists.Built list = serveConfigAndSanctionsList();
        configPoller.poll();
        SanctionsListPoller.Outcome fetched = sanctionsPoller.poll();
        assertTrue(Set.of(SanctionsListPoller.Outcome.ACTIVATED, SanctionsListPoller.Outcome.UP_TO_DATE).contains(fetched),
                fetched + " " + sanctionsPoller.lastError());
        assertEquals(list.listHash, sanctionsLists.currentHash(), "the list the config names is in force");

        JsonNode hold = evaluate(txn("R10C-HOLD-1", 125000L, true));
        JsonNode alert = evaluate(txn("R10C-ALERT-1", 250000L, false));
        assertEquals("HOLD", hold.path("action").asText(), hold.toString());
        assertEquals("ALERT", alert.path("action").asText(), alert.toString());

        // R10b: counterparty / beneficiary names are screened locally; the decision changes, the names stay.
        Map<String, Object> sanctioned = txn("R10B-HIT-1", 1000L, false);
        sanctioned.put("counterparty_name", "Ivan Sidorov");
        JsonNode hit = evaluate(sanctioned);
        assertEquals("BLOCK", hit.path("action").asText(), hit.toString());
        Map<String, Object> potential = txn("R10B-POTENTIAL-1", 1000L, false);
        potential.put("beneficiary_name", "Northwind Shiping");
        JsonNode review = evaluate(potential);
        assertEquals("HOLD", review.path("action").asText(), review.toString());
        Map<String, Object> blockedMerchant = txn("R10B-MERCHANT-1", 1000L, false);
        blockedMerchant.put("merchant_id", "mrc_blocked");
        blockedMerchant.put("counterparty_name", "Jean Normal");
        JsonNode refused = evaluate(blockedMerchant);
        assertEquals("BLOCK", refused.path("action").asText(), "a blocklisted merchant is refused: " + refused);
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
            // The sanctions list manifest and its chunks (R10b) are bodiless GETs of public data.
            if (!ALLOWED_PATHS.contains(path) && !path.startsWith(SanctionsListPoller.LIST_PATH)) {
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
        assertEquals(Set.of(hold.path("edgeAssessmentId").asText(), alert.path("edgeAssessmentId").asText(),
                hit.path("edgeAssessmentId").asText(), review.path("edgeAssessmentId").asText(),
                refused.path("edgeAssessmentId").asText()), cloud.receivedIds());
        for (JsonNode record : cloud.received) {
            assertEquals(EDGE_ID, record.path("edgeNodeId").asText());
            assertEquals(SITE_ID, record.path("siteId").asText());
            String id = record.path("edgeAssessmentId").asText();
            if (id.equals(hit.path("edgeAssessmentId").asText())) {
                assertTrue(record.path("sanctionsHit").asBoolean(), record.toString());
                assertEquals("us-ofac-sdn:NK-1001", record.path("watchlistEntryIds").get(0).asText());
                assertEquals("BLOCK", record.path("decision").asText());
            } else if (id.equals(review.path("edgeAssessmentId").asText())) {
                assertTrue(record.path("sanctionsHit").asBoolean(), record.toString());
                assertEquals("gb-hmt:7788", record.path("watchlistEntryIds").get(0).asText());
                assertEquals("HOLD", record.path("decision").asText());
            } else if (id.equals(refused.path("edgeAssessmentId").asText())) {
                assertFalse(record.path("sanctionsHit").asBoolean(), "a blocklist refusal is not a list hit");
                assertEquals("BLOCK", record.path("decision").asText());
                assertEquals("mrc_blocked", record.path("merchantId").asText());
            } else {
                // The cardholder is a PEP on the list: enhanced due diligence, never a sanctions hit.
                assertFalse(record.path("sanctionsHit").asBoolean(), record.toString());
                assertTrue(record.path("watchlistEntryIds").isMissingNode(), record.toString());
            }
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
            for (String name : NAMES) {
                assertFalse(plaintext.contains(name), "an outbound payload carries the name " + name + ": " + plaintext);
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
