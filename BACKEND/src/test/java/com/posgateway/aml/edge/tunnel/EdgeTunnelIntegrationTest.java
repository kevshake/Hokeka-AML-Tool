package com.posgateway.aml.edge.tunnel;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * End-to-end Edge Tunnel test (plan "BACKEND integration"): two real Control Plane instances
 * (embedded Tomcat, Spring WebSocket, shared H2 standing in for Postgres, a real Redis) and fake
 * Edge Nodes speaking the wire protocol over the JDK WebSocket client.
 *
 * <p>Covers the handshake (403/400, 4403, 4426, 4408 hello and idle, 4400, 4413, 4401, 4409), the
 * hello.ack, the registry, cross-instance routing with frames sealed on Redis, offline / timeout /
 * busy, retry on session loss, multi-site merge with the composite cursor and partial results,
 * support-access gating, credit-based export, audit rows, and the §7.16 "no payload persistence"
 * assertion over logs, the database and Redis.
 *
 * <p>Needs a Redis: {@code docker run -d --rm --name r4-tunnel-redis -p 127.0.0.1:56470:6379
 * redis:7.4-alpine} and {@code -Dtunnel.it.redis.port=56470}. Skipped without the property.
 */
@EnabledIfSystemProperty(named = "tunnel.it.redis.port", matches = "\\d+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeTunnelIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CANARY_REF = "CANARY-TXNREF-7731";
    private static final long CANARY_AMOUNT = 987_654_321L;
    private static final String CANARY_FEATURE = "canary-feature-value-5519";

    private ConfigurableApplicationContext cpA;
    private ConfigurableApplicationContext cpB;
    private EdgeDataGateway gatewayA;
    private EdgeNodeRepository nodes;
    private EdgeDataAccessLogRepository auditRepo;
    private JdbcTemplate jdbc;
    private StringRedisTemplate redis;
    private String ingestPublicB64;
    private java.security.PublicKey cpSigningPublic;
    private RedisClient pubSubClient;
    private StatefulRedisPubSubConnection<String, String> pubSub;
    private final List<String> relayed = new CopyOnWriteArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<FakeEdge> edges = new CopyOnWriteArrayList<>();
    private final AtomicLong pspSeq = new AtomicLong(100);

    // ── test application ─────────────────────────────────────────────────────────────────────────

    /**
     * Minimal Control Plane: web server + WebSocket + JPA (edge entities only) + Redis + the
     * tunnel configuration. Not a {@code @Configuration}, so no component scan ever picks it up.
     */
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class, JacksonAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class, RedisAutoConfiguration.class})
    @EntityScan(basePackageClasses = EdgeNode.class)
    @EnableJpaRepositories(basePackageClasses = EdgeNodeRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {EdgeNodeRepository.class, EdgeDataAccessLogRepository.class}))
    @Import({EdgeTunnelConfiguration.class, EdgeProperties.class, EdgeControlPlaneKeys.class,
            EdgeIdentityResolver.class, EdgeReplayGuard.class, TestPolicies.class})
    static class TunnelItApp {
    }

    /** Policy beans: real JDBC support-access policy and contract-default settings. */
    static class TestPolicies {

        /** HSE-1 primitive (EdgeBundleConfig in the full application). */
        @Bean
        HokekaSecureEnvelope hokekaSecureEnvelope() {
            return new HokekaSecureEnvelope();
        }

        /** The /edge/bundle gate on the shared node table (ACTIVE, keys pinned). */
        @Bean
        TunnelNodeAuthorizer tunnelNodeAuthorizer(EdgeNodeRepository repo) {
            return edgeId -> repo.findByEdgeId(edgeId)
                    .filter(n -> n.getStatus() == EdgeNodeStatus.ACTIVE)
                    .filter(n -> n.getEdgeX25519PublicKey() != null && n.getEdgeEd25519PublicKey() != null);
        }

        /** Production policy over {@code psps.support_access_enabled}. */
        @Bean
        SupportAccessPolicy supportAccessPolicy(JdbcTemplate jdbcTemplate) {
            return new JdbcSupportAccessPolicy(jdbcTemplate);
        }

        /** §6.1 defaults. */
        @Bean
        TunnelPspSettingsProvider tunnelPspSettingsProvider() {
            return new ContractDefaultTunnelSettings();
        }

        /** Fixed bundle version. */
        @Bean
        TunnelBundleVersionSource tunnelBundleVersionSource() {
            return pspId -> 37L;
        }

        /** Records session-open and hold.ack callbacks (the R9 extension point). */
        @Bean
        TunnelSessionListener recordingListener() {
            return RECORDER;
        }
    }

    /** Session listener test double shared by both instances. */
    static final class RecordingListener implements TunnelSessionListener {
        final List<TunnelSessionInfo> opened = new CopyOnWriteArrayList<>();
        final List<Map.Entry<String, ObjectNode>> acks = new CopyOnWriteArrayList<>();

        /** Records a session-open callback. */
        @Override
        public void onSessionOpened(TunnelSessionInfo session) {
            opened.add(session);
        }

        /** Records a routed hold.ack. */
        @Override
        public void onHoldAck(TunnelSessionInfo session, String notifyId, ObjectNode item) {
            acks.add(Map.entry(notifyId, item));
        }
    }

    static final RecordingListener RECORDER = new RecordingListener();

    // ── lifecycle ────────────────────────────────────────────────────────────────────────────────

    /** Boots instances cp-a and cp-b on one H2 database and one Redis, and taps Redis pub/sub. */
    @BeforeAll
    void startTwoControlPlanes() throws Exception {

        KeyPair sign = FakeEdge.gen("Ed25519");
        KeyPair ingest = FakeEdge.gen("X25519");
        String signPriv = Base64.getEncoder().encodeToString(sign.getPrivate().getEncoded());
        String signPub = Base64.getEncoder().encodeToString(EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) sign.getPublic()));
        String ingestPriv = Base64.getEncoder().encodeToString(ingest.getPrivate().getEncoded());
        String ingestPub = Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) ingest.getPublic()));
        String port = System.getProperty("tunnel.it.redis.port");

        cpA = start("cp-a", "create", signPriv, signPub, ingestPriv, ingestPub, port);
        cpB = start("cp-b", "none", signPriv, signPub, ingestPriv, ingestPub, port);
        // Attach after both contexts started: Spring Boot re-initialises logback on startup.
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs.start();
        root.addAppender(logs);

        gatewayA = cpA.getBean(EdgeDataGateway.class);
        nodes = cpA.getBean(EdgeNodeRepository.class);
        auditRepo = cpA.getBean(EdgeDataAccessLogRepository.class);
        jdbc = cpA.getBean(JdbcTemplate.class);
        redis = cpA.getBean("tunnelRedisTemplate", StringRedisTemplate.class);
        ingestPublicB64 = cpA.getBean(EdgeControlPlaneKeys.class).ingestPublicKeyBase64();
        cpSigningPublic = cpA.getBean(EdgeControlPlaneKeys.class).signingPublicKey();
        jdbc.execute("CREATE TABLE IF NOT EXISTS psps (psp_id BIGINT PRIMARY KEY, support_access_enabled BOOLEAN)");

        pubSubClient = RedisClient.create("redis://127.0.0.1:" + port);
        pubSub = pubSubClient.connectPubSub();
        pubSub.addListener(new RedisPubSubAdapter<>() {
            /** Captures every relayed pub/sub message for the no-plaintext assertions. */
            @Override
            public void message(String pattern, String channel, String message) {
                relayed.add(channel + " " + message);
            }
        });
        pubSub.sync().psubscribe("tunnel:*");
    }

    /** Boots one Control Plane instance on a random port. */
    private ConfigurableApplicationContext start(String id, String ddl, String signPriv, String signPub,
                                                 String ingestPriv, String ingestPub, String redisPort) {
        return new SpringApplicationBuilder(TunnelItApp.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.ofEntries(
                        Map.entry("spring.config.name", "r4-tunnel-it-no-application-properties"),
                        Map.entry("server.port", "0"),
                        Map.entry("server.servlet.context-path", "/api/v1"),
                        Map.entry("spring.main.banner-mode", "off"),
                        Map.entry("spring.datasource.url",
                                "jdbc:h2:mem:r4tunnel;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
                        Map.entry("spring.datasource.username", "sa"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", ddl),
                        Map.entry("spring.jpa.open-in-view", "false"),
                        Map.entry("spring.data.redis.host", "127.0.0.1"),
                        Map.entry("spring.data.redis.port", redisPort),
                        Map.entry("edge.bundle.signing-key", signPriv),
                        Map.entry("edge.bundle.signing-public-key", signPub),
                        Map.entry("edge.metrics.ingest-key", ingestPriv),
                        Map.entry("edge.metrics.ingest-public-key", ingestPub),
                        Map.entry("edge.tunnel.instance-id", id),
                        Map.entry("edge.tunnel.housekeeping-interval-ms", "200"),
                        Map.entry("edge.tunnel.hello-timeout-ms", "1500"),
                        Map.entry("edge.tunnel.idle-timeout-ms", "15000"),
                        Map.entry("edge.tunnel.heartbeat-interval-ms", "5000"),
                        Map.entry("logging.level.com.posgateway.aml.edge.tunnel", "TRACE")))
                .run();
    }

    /** Closes the fake Edges, the pub/sub tap and both Control Plane instances. */
    @AfterAll
    void stop() {
        edges.forEach(FakeEdge::close);
        if (pubSub != null) {
            pubSub.close();
            pubSubClient.shutdown();
        }
        if (cpB != null) {
            cpB.close();
        }
        if (cpA != null) {
            cpA.close();
        }
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** WebSocket URI of the tunnel endpoint of one instance. */
    private URI uri(ConfigurableApplicationContext cp) {
        int port = ((ServletWebServerApplicationContext) cp).getWebServer().getPort();
        return URI.create("ws://127.0.0.1:" + port + "/api/v1/edge/tunnel");
    }

    /** Creates a PSP row with the given support-access flag and returns its id. */
    private long newPsp(boolean supportAccess) {
        long psp = pspSeq.incrementAndGet();
        jdbc.update("INSERT INTO psps (psp_id, support_access_enabled) VALUES (?, ?)", psp, supportAccess);
        return psp;
    }

    /** Registers an ACTIVE node pinned to the fake Edge's keys. */
    private FakeEdge node(String edgeId, String siteId, long psp) {
        FakeEdge e = new FakeEdge(edgeId, siteId, psp, ingestPublicB64, cpSigningPublic);
        EdgeNode n = new EdgeNode();
        n.setPspId(psp);
        n.setEdgeId(edgeId);
        n.setSiteId(siteId.equals(edgeId) ? null : siteId);
        n.setDisplayName(edgeId);
        n.setStatus(EdgeNodeStatus.ACTIVE);
        n.setEdgeX25519PublicKey(e.x25519PublicB64());
        n.setEdgeEd25519PublicKey(e.ed25519PublicB64());
        n.setActivatedAt(Instant.parse("2026-10-01T08:00:00Z"));
        nodes.save(n);
        edges.add(e);
        return e;
    }

    /** Registers a node and connects it to the given instance (hello, hello.ack, pings). */
    private FakeEdge connected(String edgeId, String siteId, long psp, ConfigurableApplicationContext cp) throws Exception {
        return node(edgeId, siteId, psp).start(uri(cp), 1_000);
    }

    /** A PSP compliance officer investigating a case. */
    private static TunnelActor analyst(long psp) {
        return TunnelActor.pspUser("u-4512", "COMPLIANCE_OFFICER", psp, TunnelActor.Purpose.CASE_INVESTIGATION,
                "CASE-2026-0412");
    }

    /** Parses JSON params. */
    private static ObjectNode params(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A txn.search summary item carrying the canary txnRef and amount. */
    private static ObjectNode summary(String id, Instant at, String site, String node) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("edgeAssessmentId", id);
        s.put("txnRef", CANARY_REF);
        s.put("siteId", site);
        s.put("nodeId", node);
        s.put("evaluatedAt", TunnelTime.formatMillis(at));
        s.put("decision", "ALERT");
        s.put("score", 45);
        s.put("merchantId", "mrc_00421");
        s.put("amountMinor", CANARY_AMOUNT);
        s.put("currency", "KES");
        s.putArray("triggeredRuleIds").add(1204);
        s.put("ctrRequired", false);
        s.put("sanctionsHit", false);
        s.put("origin", "LIVE");
        return s;
    }

    /** A txn.get detail item (from the fixture) carrying the canary values. */
    private static ObjectNode detail(String id, String site, String node) {
        ObjectNode d = (ObjectNode) ContractFixtures.fixture("txn.get.item--detail.json");
        d.put("edgeAssessmentId", id);
        d.put("siteId", site);
        d.put("nodeId", node);
        d.put("txnRef", CANARY_REF);
        d.put("amountMinor", CANARY_AMOUNT);
        ((ObjectNode) d.get("features")).put("memo", CANARY_FEATURE);
        return d;
    }

    /** Deterministic lowercase UUID for a site and index. */
    private static String uuid(int site, int i) {
        return String.format("%08x-0000-4000-8000-%012x", site, i);
    }

    /** Fake Edge keyset search over a dataset sorted (evaluatedAt desc, id desc), per §7.5. */
    private static FakeEdge.RequestHandler searchOver(List<ObjectNode> data) {
        return (edge, req) -> {
            JsonNode page = req.body().get("page");
            int limit = page.get("limit").asInt();
            String q = Jcs.sha256Hex(req.body().get("params"));
            int start = 0;
            if (page.hasNonNull("cursor")) {
                JsonNode c = MAPPER.readTree(Base64.getUrlDecoder().decode(page.get("cursor").asText()));
                if (!q.equals(c.get("q").asText())) {
                    edge.error(req, TunnelError.of(TunnelErrorCode.INVALID_CURSOR, "q mismatch"));
                    return;
                }
                EdgeDataGateway.SortKey k = new EdgeDataGateway.SortKey(Instant.parse(c.get("k").get(0).asText()),
                        c.get("k").get(0).asText(), c.get("k").get(1).asText());
                while (start < data.size() && EdgeDataGateway.SortKey.of(TunnelOp.TXN_SEARCH, data.get(start)).compareTo(k) >= 0) {
                    start++;
                }
            }
            int end = Math.min(data.size(), start + limit);
            List<ObjectNode> pageItems = data.subList(start, end);
            String next = null;
            if (end < data.size()) {
                ObjectNode last = pageItems.get(pageItems.size() - 1);
                next = Base64.getUrlEncoder().withoutPadding().encodeToString(MAPPER.writeValueAsBytes(
                        MAPPER.createObjectNode().put("v", 1).<ObjectNode>set("k", MAPPER.createArrayNode()
                                .add(last.get("evaluatedAt").asText()).add(last.get("edgeAssessmentId").asText()))
                                .put("q", q)));
            }
            edge.respond(req, pageItems, next, null);
        };
    }

    /** Polls until the condition holds or fails the test after the timeout. */
    private static void await(BooleanSupplier condition, long timeoutMs, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                fail("timed out waiting for " + what);
            }
            Thread.sleep(25);
        }
    }

    /** The tunnel error code of a failed pull (unwrapping ExecutionException). */
    private static TunnelErrorCode codeOf(Throwable t) {
        Throwable c = t instanceof ExecutionException ? t.getCause() : t;
        return ((EdgeDataException) c).error().code();
    }

    private static final String SEARCH_PARAMS = "{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-10-16T23:59:59Z\"}";

    // ── handshake ────────────────────────────────────────────────────────────────────────────────

    /** Verifies: upgrade is refused without identity for unknown or suspended nodes and without subprotocol. */
    @Test
    void upgradeIsRefusedWithoutIdentityForUnknownOrSuspendedNodesAndWithoutSubprotocol() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = node("hs-node-1", "hs-node-1", psp);
        assertThat(handshakeStatus(e, true, Map.of())).isEqualTo(403);
        FakeEdge unknown = new FakeEdge("hs-unknown", "hs-unknown", psp, ingestPublicB64, cpSigningPublic);
        assertThat(handshakeStatus(unknown, true, unknown.identityHeaders())).isEqualTo(403);
        assertThat(handshakeStatus(e, true, Map.of("X-SSL-Client-Verify", "SUCCESS",
                "X-SSL-Client-S-DN", "CN=hs-node-1", "X-Hokeka-Edge-Id", "someone-else"))).isEqualTo(403);
        assertThat(handshakeStatus(e, false, e.identityHeaders())).isEqualTo(400);

        EdgeNode n = nodes.findByEdgeId("hs-node-1").orElseThrow();
        n.setStatus(EdgeNodeStatus.SUSPENDED);
        nodes.save(n);
        assertThat(handshakeStatus(e, true, e.identityHeaders())).isEqualTo(403);
    }

    /** HTTP status of the upgrade attempt (101 when it succeeded). */
    private int handshakeStatus(FakeEdge e, boolean subprotocol, Map<String, String> headers) {
        try {
            e.connect(uri(cpA), subprotocol, headers).get(10, TimeUnit.SECONDS);
            return 101;
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof WebSocketHandshakeException h) {
                return h.getResponse().statusCode();
            }
            throw new AssertionError(ex.getCause());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    /** Verifies: hello ack negotiates session and registers in redis. */
    @Test
    void helloAckNegotiatesSessionAndRegistersInRedis() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("ack-node", "ack-site", psp, cpB);
        ObjectNode ack = e.helloAck;
        assertThat(ack.get("cpInstance").asText()).isEqualTo("cp-b");
        assertThat(ack.get("protocolVersion").asInt()).isEqualTo(1);
        assertThat(ack.get("maxInflight").asInt()).isEqualTo(16);
        assertThat(ack.get("currentBundleVersion").asLong()).isEqualTo(37);
        List<String> ops = new ArrayList<>();
        ack.get("opsEffective").forEach(o -> ops.add(o.asText()));
        assertThat(ops).contains("txn.search", "export.stream", "ledger.health").doesNotContain("ledger.backfill");
        assertThat(e.ws.getSubprotocol()).isEqualTo(TunnelFrameCodec.SUBPROTOCOL);

        Map<Object, Object> registry = redis.opsForHash().entries("tunnel:psp:" + psp);
        assertThat(registry).containsKey(e.sessionId);
        JsonNode entry = MAPPER.readTree(registry.get(e.sessionId).toString());
        assertThat(entry.get("edgeId").asText()).isEqualTo("ack-node");
        assertThat(entry.get("siteId").asText()).isEqualTo("ack-site");
        assertThat(entry.get("cpInstance").asText()).isEqualTo("cp-b");
        assertThat(entry.get("inflight").asInt()).isZero();
        assertThat(redis.opsForValue().get("edge:lastseen:ack-node")).isNotNull();

        e.close();
        await(() -> !redis.opsForHash().hasKey("tunnel:psp:" + psp, e.sessionId), 5_000, "registry removal on close");
    }

    /** Verifies: hello with wrong site is closed4403 and unsupported version4426. */
    @Test
    void helloWithWrongSiteIsClosed4403AndUnsupportedVersion4426() throws Exception {
        long psp = newPsp(false);
        FakeEdge pinned = node("site-mismatch", "registered-site", psp);
        FakeEdge twin = pinned.twin();
        twin.connect(uri(cpA), true, twin.identityHeaders()).get(10, TimeUnit.SECONDS);
        twin.sendHello(List.of(1));
        assertThat(twin.ackFuture.get(10, TimeUnit.SECONDS)).isNotNull();
        twin.close();

        FakeEdge v2 = pinned.twin();
        v2.connect(uri(cpA), true, v2.identityHeaders()).get(10, TimeUnit.SECONDS);
        v2.sendHello(List.of(2, 3));
        assertThat(v2.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4426);

        EdgeNode n = nodes.findByEdgeId("site-mismatch").orElseThrow();
        n.setSiteId("moved-site");
        nodes.save(n);
        FakeEdge moved = pinned.twin();
        moved.connect(uri(cpA), true, moved.identityHeaders()).get(10, TimeUnit.SECONDS);
        moved.sendHello(List.of(1));
        assertThat(moved.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4403);
    }

    /** Verifies: hello timeout binary oversize and seal failures close with contract codes. */
    @Test
    void helloTimeoutBinaryOversizeAndSealFailuresCloseWithContractCodes() throws Exception {
        long psp = newPsp(false);
        FakeEdge base = node("close-node", "close-node", psp);

        FakeEdge silent = base.twin();
        silent.connect(uri(cpA), true, silent.identityHeaders()).get(10, TimeUnit.SECONDS);
        assertThat(silent.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4408);

        FakeEdge binary = base.twin().start(uri(cpA), 1_000);
        binary.ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true).join();
        assertThat(binary.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4400);

        FakeEdge big = base.twin().start(uri(cpA), 1_000);
        big.send("{\"v\":1,\"type\":\"ping\",\"id\":\"" + "x".repeat(1_100_000) + "\"}");
        assertThat(big.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4413);

        FakeEdge garbage = base.twin().start(uri(cpA), 1_000);
        String junk = Base64.getEncoder().encodeToString(new byte[300]);
        for (int i = 0; i < 3; i++) {
            garbage.send("{\"v\":1,\"type\":\"error\",\"id\":\"" + TunnelFrameCodec.newId() + "\",\"ts\":"
                    + System.currentTimeMillis() + ",\"nonce\":\"AAAAAAAAAAAAAAAAAAAAA" + i + "\",\"sealed\":\"" + junk + "\"}");
        }
        assertThat(garbage.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4401);

        FakeEdge malformed = base.twin().start(uri(cpA), 1_000);
        malformed.send("{not json");
        assertThat(malformed.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4400);
    }

    /** Verifies: unknown frame type is answered with unsupported frame. */
    @Test
    void unknownFrameTypeIsAnsweredWithUnsupportedFrame() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("unknown-type", "unknown-type", psp, cpA);
        e.send("{\"v\":1,\"type\":\"telemetry\",\"id\":\"" + TunnelFrameCodec.newId() + "\",\"ts\":1}");
        await(() -> !e.frames("error").isEmpty(), 5_000, "UNSUPPORTED_FRAME error");
        assertThat(e.frames("error").get(0).body().get("code").asText()).isEqualTo("UNSUPPORTED_FRAME");
        assertThat(e.closeCode).isNotDone();
    }

    /** Verifies: newest session of anode wins and the older is closed4409 across instances. */
    @Test
    void newestSessionOfANodeWinsAndTheOlderIsClosed4409AcrossInstances() throws Exception {
        long psp = newPsp(false);
        FakeEdge first = connected("dup-node", "dup-node", psp, cpA);
        FakeEdge second = first.twin().start(uri(cpB), 1_000);
        assertThat(first.closeCode.get(10, TimeUnit.SECONDS)).isEqualTo(4409);
        await(() -> redis.opsForHash().entries("tunnel:psp:" + psp).size() == 1, 5_000, "single registry entry");
        assertThat(redis.opsForHash().hasKey("tunnel:psp:" + psp, second.sessionId)).isTrue();
        assertThat(second.closeCode).isNotDone();
    }

    /** Verifies: idle session is closed4408. */
    @Test
    void idleSessionIsClosed4408() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("idle-node", "idle-node", psp, cpA);
        e.stopPinging();
        assertThat(e.closeCode.get(25, TimeUnit.SECONDS)).isEqualTo(4408);
    }

    // ── requests ─────────────────────────────────────────────────────────────────────────────────

    /** Verifies: cross instance get is relayed sealed and audited. */
    @Test
    void crossInstanceGetIsRelayedSealedAndAudited() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("relay-node", "relay-site", psp, cpB);
        String id = uuid(7, 1);
        e.handler = (edge, req) -> edge.respond(req, List.of(detail(id, "relay-site", "relay-node")), null, null);

        relayed.clear();
        EdgeDataResult r = gatewayA.request(psp, TunnelOp.TXN_GET, params("{\"edgeAssessmentId\":\"" + id + "\"}"),
                analyst(psp), TunnelActor.Purpose.ALERT_REVIEW);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).get("features").get("memo").asText()).isEqualTo(CANARY_FEATURE);
        assertThat(r.sitesAnswered()).containsExactly("relay-site");

        TunnelFrameCodec.OpenedFrame req = e.requests("txn.get").get(0);
        assertThat(req.body().get("actor").get("purpose").asText()).isEqualTo("ALERT_REVIEW");
        assertThat(req.body().get("auditId").asText()).isEqualTo(r.auditId().toString());
        assertThat(req.body().get("deadlineMs").asLong()).isBetween(4_000L, 5_000L);

        await(() -> relayed.stream().anyMatch(m -> m.startsWith("tunnel:reply:cp-a")), 5_000, "relayed reply");
        assertThat(relayed).anyMatch(m -> m.startsWith("tunnel:cp:cp-b") && m.contains("\"kind\":\"REQUEST\""));
        assertThat(relayed).anyMatch(m -> m.startsWith("tunnel:reply:cp-a") && m.contains("\"kind\":\"FRAME\""));
        assertThat(relayed).noneMatch(m -> m.contains(CANARY_REF) || m.contains(CANARY_FEATURE)
                || m.contains(String.valueOf(CANARY_AMOUNT)) || m.contains("mrc_00421"));

        EdgeDataAccessLog row = auditRepo.findByAuditId(r.auditId()).orElseThrow();
        assertThat(row.getOutcome()).isEqualTo("OK");
        assertThat(row.getOp()).isEqualTo("txn.get");
        assertThat(row.getPspId()).isEqualTo(psp);
        assertThat(row.getPurpose()).isEqualTo("ALERT_REVIEW");
        assertThat(row.getCaseId()).isEqualTo("CASE-2026-0412");
        assertThat(row.getSitesContacted()).isEqualTo("relay-site");
        assertThat(row.getSitesAnswered()).isEqualTo("relay-site");
        assertThat(row.getItemCount()).isEqualTo(1);
        assertThat(row.getBytes()).isPositive();
        assertThat(row.getParamsDigest()).isEqualTo(Jcs.sha256Hex(params("{\"edgeAssessmentId\":\"" + id + "\"}")));
        assertThat(row.getParamsSummary()).isEqualTo("{\"idCount\":1}");
        assertThat(row.isSupportAccess()).isFalse();
        assertThat(row.getCompletedAt()).isNotNull();

        await(() -> {
            String v = (String) redis.opsForHash().get("tunnel:psp:" + psp, e.sessionId);
            return v != null && v.contains("\"inflight\":0");
        }, 5_000, "inflight released");
    }

    /** Verifies: offline site answers edge offline with last seen at. */
    @Test
    void offlineSiteAnswersEdgeOfflineWithLastSeenAt() {
        long psp = newPsp(false);
        node("offline-node", "offline-site", psp);
        assertThatThrownBy(() -> gatewayA.request(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS), analyst(psp),
                TunnelActor.Purpose.TRANSACTION_MONITORING))
                .isInstanceOfSatisfying(EdgeDataException.class, ex -> {
                    assertThat(ex.error().code()).isEqualTo(TunnelErrorCode.EDGE_OFFLINE);
                    assertThat(ex.error().httpStatus()).isEqualTo(503);
                    assertThat(ex.error().lastSeenAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
                    assertThat(ex.error().siteId()).isEqualTo("offline-site");
                    assertThat(new TunnelSchemas().validate("tunnel/error.schema.json", ex.error().toJson())).isEmpty();
                });
        assertThat(auditRepo.findByPspIdOrderByRequestedAtDesc(psp, org.springframework.data.domain.Pageable.unpaged())
                .getContent()).singleElement().satisfies(row -> {
                    assertThat(row.getOutcome()).isEqualTo("EDGE_OFFLINE");
                    assertThat(row.getSitesAnswered()).isNull();
                    assertThat(row.getParamsSummary()).contains("\"from\":\"2026-10-01T00:00:00Z\"");
                });
    }

    /** Verifies: silent edge times out and receives cancel deadline. */
    @Test
    void silentEdgeTimesOutAndReceivesCancelDeadline() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("slow-node", "slow-site", psp, cpB);
        long t0 = System.currentTimeMillis();
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET,
                params("{\"txnRef\":\"AUTH-1\"}"), analyst(psp)).withDeadline(500)))
                .isInstanceOfSatisfying(EdgeDataException.class, ex -> {
                    assertThat(ex.error().code()).isEqualTo(TunnelErrorCode.EDGE_TIMEOUT);
                    assertThat(ex.error().httpStatus()).isEqualTo(504);
                });
        assertThat(System.currentTimeMillis() - t0).isBetween(1_400L, 6_000L);
        await(() -> !e.frames("cancel").isEmpty(), 5_000, "cancel frame");
        TunnelFrameCodec.OpenedFrame cancel = e.frames("cancel").get(0);
        assertThat(cancel.body().get("reason").asText()).isEqualTo("DEADLINE");
        assertThat(cancel.corrId()).isEqualTo(e.requests("txn.get").get(0).id());
    }

    /** Verifies: seventeenth concurrent request is edge busy. */
    @Test
    void seventeenthConcurrentRequestIsEdgeBusy() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("busy-node", "busy-site", psp, cpB);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> held = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                held.add(pool.submit(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET,
                        params("{\"txnRef\":\"AUTH-2\"}"), analyst(psp)).withDeadline(4_000))));
            }
            await(() -> e.requests("txn.get").size() == 16, 10_000, "16 requests in flight");
            long t0 = System.currentTimeMillis();
            assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET,
                    params("{\"txnRef\":\"AUTH-3\"}"), analyst(psp)).withDeadline(4_000)))
                    .isInstanceOfSatisfying(EdgeDataException.class, ex -> {
                        assertThat(ex.error().code()).isEqualTo(TunnelErrorCode.EDGE_BUSY);
                        assertThat(ex.error().httpStatus()).isEqualTo(429);
                        assertThat(ex.error().retryAfterMs()).isPositive();
                    });
            assertThat(System.currentTimeMillis() - t0).isLessThan(2_000);
            assertThat(e.requests("txn.get")).hasSize(16);
            for (Future<?> f : held) {
                assertThatThrownBy(f::get).satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.EDGE_TIMEOUT));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Verifies: read op is retried once on another node when the session drops. */
    @Test
    void readOpIsRetriedOnceOnAnotherNodeWhenTheSessionDrops() throws Exception {
        long psp = newPsp(false);
        AtomicBoolean dropped = new AtomicBoolean();
        String id = uuid(9, 9);
        FakeEdge.RequestHandler h = (edge, req) -> {
            if (dropped.compareAndSet(false, true)) {
                edge.ws.abort();
            } else {
                edge.respond(req, List.of(detail(id, "retry-site", edge.edgeId)), null, null);
            }
        };
        FakeEdge n1 = connected("retry-1", "retry-site", psp, cpB);
        FakeEdge n2 = connected("retry-2", "retry-site", psp, cpB);
        n1.handler = h;
        n2.handler = h;
        EdgeDataResult r = gatewayA.request(psp, TunnelOp.TXN_GET, params("{\"edgeAssessmentId\":\"" + id + "\"}"),
                analyst(psp), TunnelActor.Purpose.CASE_INVESTIGATION);
        assertThat(r.items()).hasSize(1);
        assertThat(dropped).isTrue();
        assertThat(n1.requests("txn.get").size() + n2.requests("txn.get").size()).isEqualTo(2);
    }

    /** Verifies: multi site search merges in order and pages with acomposite cursor. */
    @Test
    void multiSiteSearchMergesInOrderAndPagesWithACompositeCursor() throws Exception {
        long psp = newPsp(false);
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        List<ObjectNode> eu = new ArrayList<>();
        List<ObjectNode> dr = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            eu.add(summary(uuid(1, k), t.minusSeconds(2L * k), "eu", "eu-1"));
            dr.add(summary(uuid(2, k), t.minusSeconds(2L * k + 1), "dr", "dr-1"));
        }
        dr.add(summary(uuid(2, 99), t.minusSeconds(4), "dr", "dr-1"));
        Comparator<ObjectNode> order = (a, b) -> EdgeDataGateway.SortKey.of(TunnelOp.TXN_SEARCH, b)
                .compareTo(EdgeDataGateway.SortKey.of(TunnelOp.TXN_SEARCH, a));
        eu.sort(order);
        dr.sort(order);
        FakeEdge euNode = connected("eu-1", "eu", psp, cpA);
        FakeEdge drNode = connected("dr-1", "dr", psp, cpB);
        euNode.handler = searchOver(eu);
        drNode.handler = searchOver(dr);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            EdgeDataResult r = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                    analyst(psp)).page(3, cursor));
            assertThat(r.incomplete()).isFalse();
            assertThat(r.items().size()).isLessThanOrEqualTo(3);
            r.items().forEach(i -> seen.add(i.get("edgeAssessmentId").asText()));
            cursor = r.nextCursor();
            pages++;
        } while (cursor != null && pages < 10);

        List<ObjectNode> all = new ArrayList<>(eu);
        all.addAll(dr);
        all.sort(order);
        assertThat(seen).containsExactlyElementsOf(all.stream().map(i -> i.get("edgeAssessmentId").asText()).toList());
        assertThat(pages).isEqualTo(4);

        EdgeDataResult first = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                analyst(psp)).page(3, null));
        String tampered = first.nextCursor().substring(0, first.nextCursor().length() - 2) + "AA";
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                analyst(psp)).page(3, tampered)))
                .satisfies(t2 -> assertThat(codeOf(t2)).isEqualTo(TunnelErrorCode.INVALID_CURSOR));
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH,
                params("{\"from\":\"2026-10-02T00:00:00Z\",\"to\":\"2026-10-16T23:59:59Z\"}"), analyst(psp))
                .page(3, first.nextCursor())))
                .satisfies(t2 -> assertThat(codeOf(t2)).isEqualTo(TunnelErrorCode.INVALID_CURSOR));
    }

    /** Verifies: unavailable site gives partial result and keeps its cursor position. */
    @Test
    void unavailableSiteGivesPartialResultAndKeepsItsCursorPosition() throws Exception {
        long psp = newPsp(false);
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        List<ObjectNode> up = List.of(summary(uuid(3, 1), t, "up", "up-1"), summary(uuid(3, 0), t.minusSeconds(5), "up", "up-1"));
        FakeEdge upNode = connected("up-1", "up", psp, cpA);
        upNode.handler = searchOver(up);
        FakeEdge downNode = connected("down-1", "down", psp, cpB);
        downNode.close();
        await(() -> redis.opsForHash().entries("tunnel:psp:" + psp).size() == 1, 5_000, "down site deregistered");

        EdgeDataResult r = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                analyst(psp)).page(50, null));
        assertThat(r.items()).hasSize(2);
        assertThat(r.incomplete()).isTrue();
        assertThat(r.unavailableSites()).singleElement().satisfies(u -> {
            assertThat(u.siteId()).isEqualTo("down");
            assertThat(u.code()).isEqualTo(TunnelErrorCode.EDGE_OFFLINE);
            assertThat(u.lastSeenAt()).isNotNull();
        });
        assertThat(r.nextCursor()).isNotNull();
        JsonNode composite = MAPPER.readTree(Base64.getUrlDecoder().decode(r.nextCursor().split("\\.")[0]));
        assertThat(composite.get("sites").get("down").asText()).isEqualTo("START");
        assertThat(composite.get("sites").get("up").asText()).isEqualTo("DONE");
        ObjectNode envelope = r.toConsoleEnvelope();
        assertThat(envelope.get("items").get(0).get("source").asText()).isEqualTo("EDGE");
        assertThat(envelope.get("unavailableSites").get(0).get("code").asText()).isEqualTo("EDGE_OFFLINE");
        assertThat(auditRepo.findByAuditId(r.auditId()).orElseThrow().getOutcome()).isEqualTo("PARTIAL");
    }

    /** Verifies: batch get across sites is aunion in request order with missing. */
    @Test
    void batchGetAcrossSitesIsAUnionInRequestOrderWithMissing() throws Exception {
        long psp = newPsp(false);
        String a = uuid(4, 1);
        String b = uuid(5, 1);
        String gone = uuid(6, 1);
        FakeEdge s1 = connected("bg-1", "bg-a", psp, cpA);
        FakeEdge s2 = connected("bg-2", "bg-b", psp, cpB);
        s1.handler = (edge, req) -> edge.respond(req, List.of(detail(a, "bg-a", "bg-1")), null, List.of(b, gone));
        s2.handler = (edge, req) -> edge.respond(req, List.of(detail(b, "bg-b", "bg-2")), null, List.of(a, gone));
        EdgeDataResult r = gatewayA.request(psp, TunnelOp.TXN_BATCH_GET,
                params("{\"edgeAssessmentIds\":[\"" + b + "\",\"" + gone + "\",\"" + a + "\"]}"), analyst(psp),
                TunnelActor.Purpose.CASE_INVESTIGATION);
        assertThat(r.items().stream().map(i -> i.get("edgeAssessmentId").asText()).toList()).containsExactly(b, a);
        assertThat(r.missing()).containsExactly(gone);
        assertThat(auditRepo.findByAuditId(r.auditId()).orElseThrow().getParamsSummary()).isEqualTo("{\"idCount\":3}");
    }

    /** Verifies: items failing their schema are discarded not forwarded. */
    @Test
    void itemsFailingTheirSchemaAreDiscardedNotForwarded() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("leaky-node", "leaky", psp, cpB);
        String good = uuid(8, 1);
        e.handler = (edge, req) -> {
            ObjectNode bad = summary(uuid(8, 2), Instant.parse("2026-10-16T09:00:00Z"), "leaky", "leaky-node");
            bad.put("panHash", "sha256:4a7d");
            edge.respond(req, List.of(summary(good, Instant.parse("2026-10-16T08:00:00Z"), "leaky", "leaky-node"), bad),
                    null, null);
        };
        EdgeDataResult r = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                analyst(psp)).page(10, null));
        assertThat(r.items()).singleElement().satisfies(i -> assertThat(i.get("edgeAssessmentId").asText()).isEqualTo(good));
        assertThat(logs.list).anyMatch(ev -> ev.getFormattedMessage().contains("discarded 1 item(s)")
                && ev.getFormattedMessage().contains("/items/1/panHash"));
    }

    /** Verifies: cloud refuses invalid requests without contacting the edge. */
    @Test
    void cloudRefusesInvalidRequestsWithoutContactingTheEdge() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("strict-node", "strict", psp, cpA);
        assertThatThrownBy(() -> gatewayA.request(psp, TunnelOp.TXN_SEARCH,
                params("{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-10-16T23:59:59Z\",\"panHash\":\"x\"}"),
                analyst(psp), TunnelActor.Purpose.TRANSACTION_MONITORING))
                .isInstanceOfSatisfying(EdgeDataException.class, ex -> {
                    assertThat(ex.error().code()).isEqualTo(TunnelErrorCode.INVALID_PARAMS);
                    assertThat(ex.error().param()).isEqualTo("/panHash");
                });
        assertThatThrownBy(() -> gatewayA.request(psp, TunnelOp.TXN_SEARCH,
                params("{\"from\":\"2026-01-01T00:00:00Z\",\"to\":\"2026-10-16T23:59:59Z\"}"),
                analyst(psp), TunnelActor.Purpose.TRANSACTION_MONITORING))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.INVALID_PARAMS));
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH, params(SEARCH_PARAMS),
                analyst(psp)).page(201, null)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.PAGE_LIMIT_EXCEEDED));
        assertThatThrownBy(() -> gatewayA.request(psp, TunnelOp.TXN_GET, params("{\"edgeAssessmentId\":\"x\"}"),
                TunnelActor.pspUser("u-1", "ANALYST", psp + 1, TunnelActor.Purpose.ALERT_REVIEW, null),
                TunnelActor.Purpose.ALERT_REVIEW))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.TENANT_MISMATCH));
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.LEDGER_BACKFILL,
                (ObjectNode) ContractFixtures.fixture("ledger.backfill.params--begin.json"),
                TunnelActor.system("backfill", psp, TunnelActor.Purpose.LEDGER_BACKFILL)).onSites(Set.of("strict"))))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.OP_NOT_ALLOWED));
        Thread.sleep(300);
        assertThat(e.requests("txn.search")).isEmpty();
        assertThat(e.requests("ledger.backfill")).isEmpty();
    }

    /** Verifies: platform pull needs support access and is audited with its reason. */
    @Test
    void platformPullNeedsSupportAccessAndIsAuditedWithItsReason() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("support-node", "support", psp, cpB);
        String id = uuid(10, 1);
        e.handler = (edge, req) -> edge.respond(req, List.of(detail(id, "support", "support-node")), null, null);
        TunnelActor platform = TunnelActor.platformUser("u-1", "PLATFORM_ADMIN", psp,
                "Ticket HK-2291: PSP reports a missing ALERT on this evaluation");
        ObjectNode p = params("{\"edgeAssessmentId\":\"" + id + "\"}");

        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET, p, platform)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.SUPPORT_ACCESS_DISABLED));
        assertThat(e.requests("txn.get")).isEmpty();

        jdbc.update("UPDATE psps SET support_access_enabled = TRUE WHERE psp_id = ?", psp);
        TunnelActor noReason = new TunnelActor(TunnelActor.Kind.PLATFORM_USER, "u-1", "PLATFORM_ADMIN", psp,
                TunnelActor.Purpose.SUPPORT_ACCESS, null, null, null, "short");
        assertThatThrownBy(() -> gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET, p, noReason)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.SUPPORT_ACCESS_DISABLED));

        EdgeDataResult r = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_GET, p, platform));
        assertThat(r.items()).hasSize(1);
        assertThat(e.requests("txn.get")).singleElement().satisfies(req -> {
            assertThat(req.body().get("actor").get("kind").asText()).isEqualTo("PLATFORM_USER");
            assertThat(req.body().get("actor").get("supportReason").asText()).startsWith("Ticket HK-2291");
        });
        EdgeDataAccessLog row = auditRepo.findByAuditId(r.auditId()).orElseThrow();
        assertThat(row.isSupportAccess()).isTrue();
        assertThat(row.getSupportReason()).startsWith("Ticket HK-2291");
        assertThat(row.getActorKind()).isEqualTo("PLATFORM_USER");
    }

    /** Verifies: revoking support access cancels in flight platform pulls. */
    @Test
    void revokingSupportAccessCancelsInFlightPlatformPulls() throws Exception {
        long psp = newPsp(true);
        FakeEdge e = connected("revoke-node", "revoke", psp, cpB);
        TunnelActor platform = TunnelActor.platformUser("u-1", "SUPER_ADMIN", psp, "Ticket HK-3000: reconcile counts");
        CompletableFuture<EdgeDataResult> pull = CompletableFuture.supplyAsync(() -> gatewayA.request(
                EdgeDataRequest.of(psp, TunnelOp.TXN_GET, params("{\"txnRef\":\"AUTH-9\"}"), platform).withDeadline(6_000)));
        await(() -> !e.requests("txn.get").isEmpty(), 5_000, "platform request at the Edge");
        cpB.getBean(EdgeDataGateway.class).revokeSupportAccess(psp);
        assertThatThrownBy(() -> pull.get(5, TimeUnit.SECONDS))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(TunnelErrorCode.SUPPORT_ACCESS_DISABLED));
        await(() -> !e.frames("cancel").isEmpty(), 5_000, "cancel at the Edge");
        assertThat(e.frames("cancel").get(0).body().get("reason").asText()).isEqualTo("SUPPORT_ACCESS_REVOKED");
    }

    /** Verifies: export streams across sites under credit in merged order. */
    @Test
    void exportStreamsAcrossSitesUnderCreditInMergedOrder() throws Exception {
        long psp = newPsp(false);
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        List<ObjectNode> a = new ArrayList<>();
        List<ObjectNode> b = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            a.add(summary(uuid(11, k), t.minusSeconds(2L * k), "xa", "xa-1"));
            b.add(summary(uuid(12, k), t.minusSeconds(2L * k + 1), "xb", "xb-1"));
        }
        FakeEdge na = connected("xa-1", "xa", psp, cpA);
        FakeEdge nb = connected("xb-1", "xb", psp, cpB);
        na.handler = streamer(a, 5);
        nb.handler = streamer(b, 5);

        ObjectNode p = params("{\"filter\":" + SEARCH_PARAMS + ",\"initialCredit\":2}");
        List<String> ids = new ArrayList<>();
        java.util.UUID[] auditId = new java.util.UUID[1];
        try (EdgeDataStream s = gatewayA.openExport(EdgeDataRequest.of(psp, TunnelOp.EXPORT_STREAM, p,
                TunnelActor.pspUser("u-9", "COMPLIANCE_OFFICER", psp, TunnelActor.Purpose.DATA_EXPORT, null)))) {
            auditId[0] = s.auditId();
            while (s.hasNext()) {
                ids.add(s.next().get("edgeAssessmentId").asText());
                Thread.sleep(5);
            }
            assertThat(s.incomplete()).isFalse();
            assertThat(s.totalRows()).isEqualTo(24);
        }
        List<ObjectNode> all = new ArrayList<>(a);
        all.addAll(b);
        all.sort((x, y) -> EdgeDataGateway.SortKey.of(TunnelOp.EXPORT_STREAM, y)
                .compareTo(EdgeDataGateway.SortKey.of(TunnelOp.EXPORT_STREAM, x)));
        assertThat(ids).containsExactlyElementsOf(all.stream().map(i -> i.get("edgeAssessmentId").asText()).toList());
        assertThat(na.creditViolations.get() + nb.creditViolations.get()).isZero();
        assertThat(na.frames("credit")).isNotEmpty();
        EdgeDataAccessLog row = auditRepo.findByAuditId(auditId[0]).orElseThrow();
        assertThat(row.getOutcome()).isEqualTo("OK");
        assertThat(row.getItemCount()).isEqualTo(24);
        assertThat(row.getSitesAnswered()).isEqualTo("xa,xb");
    }


    /** Fake Edge export: chunks of {@code size}, each sent only within credit (on its own thread). */
    private static FakeEdge.RequestHandler streamer(List<ObjectNode> rows, int size) {
        return (edge, req) -> new Thread(() -> {
            try {
                int seq = 0;
                for (int i = 0; i < rows.size(); i += size) {
                    if (!edge.awaitCredit(req.id(), 10_000)) {
                        return;
                    }
                    boolean last = i + size >= rows.size();
                    edge.chunk(req, seq++, rows.subList(i, Math.min(rows.size(), i + size)), last,
                            last ? (long) rows.size() : null);
                }
            } catch (Exception ex) {
                edge.openFailures.add("streamer:" + ex);
            }
        }, "fake-edge-export").start();
    }

    /** Verifies: notify reaches every session and hold ack is routed to listeners. */
    @Test
    void notifyReachesEverySessionAndHoldAckIsRoutedToListeners() throws Exception {
        long psp = newPsp(false);
        FakeEdge n1 = connected("notify-1", "notify-a", psp, cpA);
        FakeEdge n2 = connected("notify-2", "notify-b", psp, cpB);
        ObjectNode body = (ObjectNode) ContractFixtures.fixture("notify--config-updated.json");
        List<TunnelNotifier.Delivery> sent = cpA.getBean(TunnelNotifier.class).notifyPsp(psp, body);
        assertThat(sent).hasSize(2);
        await(() -> !n1.frames("notify").isEmpty() && !n2.frames("notify").isEmpty(), 5_000, "notify at both nodes");
        assertThat(n1.frames("notify").get(0).body()).isEqualTo(body);
        assertThat(n1.openFailures).isEmpty();
        assertThat(RECORDER.opened).anyMatch(s -> s.sessionId().equals(n2.sessionId) && s.cpInstance().equals("cp-b"));

        // hold.place must be acknowledged; the ack is routed to the listeners of the holder.
        ObjectNode hold = (ObjectNode) ContractFixtures.fixture("notify--hold-place.json");
        TunnelNotifier.Delivery d = cpA.getBean(TunnelNotifier.class).notifySession(psp, n2.sessionId, hold).orElseThrow();
        await(() -> n2.frames("notify").size() == 2, 5_000, "hold.place at node 2");
        TunnelFrameCodec.OpenedFrame placed = n2.frames("notify").get(1);
        assertThat(placed.id()).isEqualTo(d.notifyId());
        ObjectNode ack = MAPPER.createObjectNode().put("op", "hold.ack").put("status", "OK").put("seq", 0).put("last", true);
        ack.putArray("items").add(ContractFixtures.fixture("hold.ack.item--applied.json"));
        n2.sendSealed("response", placed.id(), ack);
        await(() -> RECORDER.acks.stream().anyMatch(a -> a.getKey().equals(d.notifyId())), 5_000, "hold.ack routed");
        assertThat(RECORDER.acks.stream().filter(a -> a.getKey().equals(d.notifyId())).findFirst().orElseThrow()
                .getValue()).isEqualTo(ContractFixtures.fixture("hold.ack.item--applied.json"));
        assertThatThrownBy(() -> cpA.getBean(TunnelNotifier.class).notifyPsp(psp, MAPPER.createObjectNode().put("type", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── §7.16: no payload persistence ────────────────────────────────────────────────────────────

    /** Verifies: no payload reaches logs database or redis. */
    @Test
    void noPayloadReachesLogsDatabaseOrRedis() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("canary-node", "canary", psp, cpB);
        String id = uuid(13, 1);
        e.handler = (edge, req) -> {
            switch (req.body().get("op").asText()) {
                case "txn.search" -> edge.respond(req, List.of(summary(id, Instant.parse("2026-10-16T09:00:00Z"),
                        "canary", "canary-node")), null, null);
                default -> edge.respond(req, List.of(detail(id, "canary", "canary-node")), null, null);
            }
        };
        EdgeDataResult search = gatewayA.request(EdgeDataRequest.of(psp, TunnelOp.TXN_SEARCH,
                params("{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-10-16T23:59:59Z\",\"txnRef\":\"" + CANARY_REF + "\"}"),
                analyst(psp)).page(10, null));
        EdgeDataResult get = gatewayA.request(psp, TunnelOp.TXN_GET, params("{\"txnRef\":\"" + CANARY_REF + "\"}"),
                analyst(psp), TunnelActor.Purpose.ALERT_REVIEW);
        assertThat(search.items().get(0).get("txnRef").asText()).isEqualTo(CANARY_REF);
        assertThat(get.items().get(0).get("amountMinor").asLong()).isEqualTo(CANARY_AMOUNT);
        assertThat(get.items().get(0).get("features").get("memo").asText()).isEqualTo(CANARY_FEATURE);
        assertThat(auditRepo.findByAuditId(search.auditId()).orElseThrow().getParamsSummary()).contains("\"byTxnRef\":true");

        List<String> canaries = List.of(CANARY_REF, String.valueOf(CANARY_AMOUNT), CANARY_FEATURE);
        // Logs (TRACE for the tunnel package), including stack traces. The capture must be live.
        assertThat(logs.list).anyMatch(ev -> ev.getLoggerName().startsWith("com.posgateway.aml.edge.tunnel"));
        for (ILoggingEvent ev : List.copyOf(logs.list)) {
            String text = ev.getFormattedMessage() + (ev.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(ev.getThrowableProxy()));
            for (String c : canaries) {
                assertThat(text).as("log line from %s", ev.getLoggerName()).doesNotContain(c);
            }
        }
        // Every column of every table of the shared database.
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("edge_data_access_log", "edge_nodes");
        for (String table : tables) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM \"" + table + "\"")) {
                String text = String.valueOf(row.values());
                for (String c : canaries) {
                    assertThat(text).as("row of %s", table).doesNotContain(c);
                }
            }
        }
        // Every Redis key and value, and every relayed pub/sub message.
        Set<String> keys = new HashSet<>(redis.keys("*"));
        for (String key : keys) {
            String type = redis.type(key).code();
            String value = switch (type) {
                case "string" -> redis.opsForValue().get(key);
                case "hash" -> String.valueOf(redis.opsForHash().entries(key));
                default -> type;
            };
            for (String c : canaries) {
                assertThat(key + " " + value).as("redis key %s", key).doesNotContain(c);
            }
        }
        for (String m : List.copyOf(relayed)) {
            for (String c : canaries) {
                assertThat(m).doesNotContain(c);
            }
        }
        assertThat(relayed).isNotEmpty();
    }
}
