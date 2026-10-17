package com.posgateway.aml.edge.tunnel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.EdgeDataNoStoreFilter;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.controller.edgedata.EdgeDataController;
import com.posgateway.aml.controller.transaction.EdgeTransactionController;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.exception.EdgeDataExceptionHandler;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import com.posgateway.aml.service.edgedata.EdgeDataAccessLogService;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeDataStatusService;
import com.posgateway.aml.service.edgedata.EdgeTransactionProxyService;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.edgedata.LegacyTransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionExportService;
import com.posgateway.aml.service.edgedata.TransactionLinksService;
import com.posgateway.aml.service.edgedata.TransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionSourceResolver;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import com.posgateway.aml.service.security.PspIsolationService;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * End-to-end R6 data proxy test on the R4 harness: two real Control Plane instances (embedded
 * Tomcat, Spring MVC + WebSocket, shared H2, a real Redis) with the R6 controllers and services,
 * fake Edge Nodes connected to the <em>other</em> instance (so every pull is relayed sealed through
 * Redis), and real HTTP calls from a Console stand-in.
 *
 * <p>Covers: list / get / batchGet / related / export / status / access log over the tunnel, the
 * envelope and {@code Cache-Control: no-store}; tenant isolation (a PSP user can never pull another
 * PSP's data, and the other PSP's Edge receives nothing); the support-access gate for platform
 * staff, audited either way; offline; and the §7.16 canary across the new endpoints (logs at TRACE
 * including Spring MVC, every database column, every Redis key and relayed message).
 *
 * <p>Needs a Redis: {@code docker run -d --rm --name r6-proxy-redis -p 127.0.0.1:56471:6379
 * redis:7.4-alpine} and {@code -Dtunnel.it.redis.port=56471}. Skipped without the property.
 */
@EnabledIfSystemProperty(named = "tunnel.it.redis.port", matches = "\\d+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeDataProxyIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CANARY_AMOUNT = 918_273_645L;
    private static final String CANARY_FEATURE = "canary-feature-r6-8841";
    private static final String CANARY_REASON = "canary-reason-r6-2290";
    private static final String FROM = "2026-10-01T00:00:00Z";
    private static final String TO = "2026-10-17T00:00:00Z";

    /** Users the Console stand-in signs in as ({@code X-Test-User} header). */
    static final Map<String, User> USERS = new ConcurrentHashMap<>();

    private ConfigurableApplicationContext cpA;
    private ConfigurableApplicationContext cpB;
    private EdgeNodeRepository nodes;
    private EdgeDataAccessLogRepository auditRepo;
    private AssessmentRepository assessmentsA;
    private JdbcTemplate jdbc;
    private StringRedisTemplate redis;
    private String ingestPublicB64;
    private java.security.PublicKey cpSigningPublic;
    private RedisClient pubSubClient;
    private StatefulRedisPubSubConnection<String, String> pubSub;
    private final List<String> relayed = new CopyOnWriteArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<FakeEdge> edges = new CopyOnWriteArrayList<>();
    private final AtomicLong pspSeq = new AtomicLong(600);
    private final HttpClient http = HttpClient.newHttpClient();

    // ── test application ─────────────────────────────────────────────────────────────────────────

    /** Control Plane with the tunnel and the R6 proxy; repositories outside the edge package are mocks. */
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class, JacksonAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class, RedisAutoConfiguration.class})
    @EntityScan(basePackageClasses = EdgeNode.class)
    @EnableJpaRepositories(basePackageClasses = EdgeNodeRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {EdgeNodeRepository.class, EdgeDataAccessLogRepository.class}))
    @Import({EdgeTunnelConfiguration.class, EdgeProperties.class, EdgeControlPlaneKeys.class,
            EdgeIdentityResolver.class, EdgeReplayGuard.class, EdgeTunnelIntegrationTest.TestPolicies.class,
            ProxyBeans.class, EdgeTransactionController.class, EdgeDataController.class, TransactionReadService.class,
            EdgeDataCallerResolver.class, TransactionSourceResolver.class, EdgeTransactionProxyService.class,
            LegacyTransactionReadService.class, TransactionExportService.class, EdgeDataStatusService.class,
            EdgeDataAccessLogService.class, EdgeTunnelAccess.class, EdgeDataNoStoreFilter.class,
            EdgeDataExceptionHandler.class, PspIsolationService.class})
    static class ProxyItApp {
    }

    /** Collaborators outside this test's scope, as mocks; and the sign-in filter. */
    static class ProxyBeans {

        /** Decision records: stubbed per test. */
        @Bean
        AssessmentRepository assessmentRepository() {
            return Mockito.mock(AssessmentRepository.class);
        }

        /** Legacy rows: never read for Edge PSPs. */
        @Bean
        TransactionRepository transactionRepository() {
            return Mockito.mock(TransactionRepository.class);
        }

        /** Legacy keyset paging: never used for Edge PSPs. */
        @Bean
        KeysetSliceService keysetSliceService() {
            return Mockito.mock(KeysetSliceService.class);
        }

        /** Legacy reference translation: not exercised here. */
        @Bean
        TransactionReferenceResolver transactionReferenceResolver() {
            return Mockito.mock(TransactionReferenceResolver.class);
        }

        /** Cloud-held links (alerts, cases): covered by the WebMvc test. */
        @Bean
        TransactionLinksService transactionLinksService() {
            return Mockito.mock(TransactionLinksService.class);
        }

        /** PspIsolationService's lookup by name (the principal is a User, so it is never called). */
        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        /** Signs the request in as the {@code X-Test-User} user, for the request's duration only. */
        @Bean
        Filter testSignIn() {
            return (req, res, chain) -> {
                User u = USERS.get(String.valueOf(((HttpServletRequest) req).getHeader("X-Test-User")));
                if (u != null) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,
                            null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().getName()))));
                }
                try {
                    chain.doFilter(req, res);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────────────────────────────

    /** Boots cp-a (serves the Console) and cp-b (holds the Edge sessions) on one H2 and one Redis. */
    @BeforeAll
    void start() throws Exception {
        KeyPair sign = FakeEdge.gen("Ed25519");
        KeyPair ingest = FakeEdge.gen("X25519");
        String signPriv = Base64.getEncoder().encodeToString(sign.getPrivate().getEncoded());
        String signPub = Base64.getEncoder().encodeToString(EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) sign.getPublic()));
        String ingestPriv = Base64.getEncoder().encodeToString(ingest.getPrivate().getEncoded());
        String ingestPub = Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) ingest.getPublic()));
        String port = System.getProperty("tunnel.it.redis.port");
        cpA = boot("r6-cp-a", "create", signPriv, signPub, ingestPriv, ingestPub, port);
        cpB = boot("r6-cp-b", "none", signPriv, signPub, ingestPriv, ingestPub, port);

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs.start();
        root.addAppender(logs);
        for (String name : List.of("com.posgateway.aml", "org.springframework.web", "org.hibernate.SQL",
                "org.hibernate.orm.jdbc.bind")) {
            ((Logger) LoggerFactory.getLogger(name)).setLevel(Level.TRACE);
        }

        nodes = cpA.getBean(EdgeNodeRepository.class);
        auditRepo = cpA.getBean(EdgeDataAccessLogRepository.class);
        assessmentsA = cpA.getBean(AssessmentRepository.class);
        jdbc = cpA.getBean(JdbcTemplate.class);
        redis = new StringRedisTemplate(cpA.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        ingestPublicB64 = cpA.getBean(EdgeControlPlaneKeys.class).ingestPublicKeyBase64();
        cpSigningPublic = cpA.getBean(EdgeControlPlaneKeys.class).signingPublicKey();
        jdbc.execute("CREATE TABLE IF NOT EXISTS psps (psp_id BIGINT PRIMARY KEY, support_access_enabled BOOLEAN)");

        pubSubClient = RedisClient.create("redis://127.0.0.1:" + port);
        pubSub = pubSubClient.connectPubSub();
        pubSub.addListener(new RedisPubSubAdapter<>() {
            /** Captures every relayed message for the canary scan. */
            @Override
            public void message(String pattern, String channel, String message) {
                relayed.add(channel + " " + message);
            }
        });
        pubSub.sync().psubscribe("tunnel:*");

        Role platformRole = new Role();
        platformRole.setName("PLATFORM_ADMIN");
        USERS.put("platform", User.builder().id(1L).username("ops").email("o@x").passwordHash("").role(platformRole)
                .build());
    }

    /** Boots one instance. */
    private ConfigurableApplicationContext boot(String id, String ddl, String signPriv, String signPub,
                                                String ingestPriv, String ingestPub, String redisPort) {
        return new SpringApplicationBuilder(ProxyItApp.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.ofEntries(
                        Map.entry("spring.config.name", "r6-proxy-it-no-application-properties"),
                        Map.entry("server.port", "0"),
                        Map.entry("server.servlet.context-path", "/api/v1"),
                        Map.entry("spring.main.banner-mode", "off"),
                        Map.entry("spring.datasource.url", "jdbc:h2:mem:r6proxy;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
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
                        Map.entry("logging.level.com.posgateway.aml", "TRACE"),
                        Map.entry("logging.level.org.springframework.web", "TRACE")))
                .run();
    }

    /** Closes the Edges, the tap and both instances. */
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
        USERS.clear();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** A PSP row and a COMPLIANCE_OFFICER of it signed in as {@code psp:<id>}. */
    private long newPsp(boolean supportAccess) {
        long psp = pspSeq.incrementAndGet();
        jdbc.update("INSERT INTO psps (psp_id, support_access_enabled) VALUES (?, ?)", psp, supportAccess);
        Role role = new Role();
        role.setName("COMPLIANCE_OFFICER");
        Psp p = new Psp();
        p.setPspId(psp);
        USERS.put("psp:" + psp, User.builder().id(1000 + psp).username("officer-" + psp).email("x").passwordHash("")
                .role(role).psp(p).build());
        return psp;
    }

    /** Registers an ACTIVE, activated node pinned to a fake Edge's keys. */
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

    /** Registers a node and connects it to cp-b (the Console talks to cp-a). */
    private FakeEdge connected(String edgeId, String siteId, long psp) throws Exception {
        int port = ((ServletWebServerApplicationContext) cpB).getWebServer().getPort();
        return node(edgeId, siteId, psp).start(URI.create("ws://127.0.0.1:" + port + "/api/v1/edge/tunnel"), 1_000);
    }

    /** One HTTP exchange with cp-a as {@code user}. */
    private HttpResponse<String> call(String method, String path, String user, String body,
                                      Map<String, String> headers) throws Exception {
        int port = ((ServletWebServerApplicationContext) cpA).getWebServer().getPort();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .header("X-Test-User", user);
        headers.forEach(b::header);
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** GET as {@code user}. */
    private HttpResponse<String> get(String path, String user) throws Exception {
        return call("GET", path, user, null, Map.of());
    }

    /** Parses a JSON body. */
    private static JsonNode json(HttpResponse<String> r) throws Exception {
        return MAPPER.readTree(r.body());
    }

    /** URL-encodes a query value. */
    private static String q(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** Deterministic lowercase UUID. */
    private static String uuid(int site, int i) {
        return String.format("%08x-0000-4000-8000-%012x", site, i);
    }

    /** A summary item carrying the canary amount. */
    private static ObjectNode summary(String id, Instant at, String site, String node) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("edgeAssessmentId", id);
        s.put("txnRef", "R6-" + id.substring(0, 8) + "-" + id.substring(24));
        s.put("siteId", site);
        s.put("nodeId", node);
        s.put("evaluatedAt", TunnelTime.formatMillis(at));
        s.put("decision", "ALERT");
        s.put("score", 52);
        s.put("merchantId", "mrc_r6");
        s.put("amountMinor", CANARY_AMOUNT);
        s.put("currency", "KES");
        s.put("origin", "LIVE");
        return s;
    }

    /** A detail item carrying the canary amount, feature and reason. */
    private static ObjectNode detail(String id, String site, String node) {
        ObjectNode d = (ObjectNode) ContractFixtures.fixture("txn.get.item--detail.json");
        d.put("edgeAssessmentId", id);
        d.put("siteId", site);
        d.put("nodeId", node);
        d.put("amountMinor", CANARY_AMOUNT);
        d.remove("txnRef");
        ((ObjectNode) d.get("features")).put("memo", CANARY_FEATURE);
        d.putArray("reasons").add(CANARY_REASON);
        return d;
    }

    /** A decision record of {@code psp} naming a site, as the cloud holds it. */
    private void decisionRecord(long psp, String id, String site) {
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setPspId(psp);
        a.setEdgeAssessmentId(UUID.fromString(id));
        a.setSiteId(site);
        Mockito.when(assessmentsA.findFirstByPspIdAndEdgeAssessmentId(psp, UUID.fromString(id))).thenReturn(Optional.of(a));
        Mockito.when(assessmentsA.findReferencedByPsp(eq(psp), any())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(1);
            return ids.contains(UUID.fromString(id)) ? List.of(a) : List.of();
        });
    }

    /** The PSP's audit rows, newest first. */
    private List<EdgeDataAccessLog> auditRows(long psp) {
        return auditRepo.findAll().stream().filter(r -> r.getPspId() == psp)
                .sorted((x, y) -> y.getId().compareTo(x.getId())).toList();
    }

    /** Waits for the audit row of the newest pull to be completed. */
    private EdgeDataAccessLog completedAudit(long psp) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            List<EdgeDataAccessLog> rows = auditRows(psp);
            if (!rows.isEmpty() && rows.get(0).getOutcome() != null) {
                return rows.get(0);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("audit row not completed");
    }

    // ── tests ────────────────────────────────────────────────────────────────────────────────────

    /** Verifies: the list merges two sites over the relayed tunnel, with the envelope, no-store and audit. */
    @Test
    void listMergesSitesThroughTheTunnelAndIsAudited() throws Exception {
        long psp = newPsp(false);
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        FakeEdge a = connected("r6-la-1", "r6-la", psp);
        FakeEdge b = connected("r6-lb-1", "r6-lb", psp);
        a.handler = (e, req) -> e.respond(req, List.of(summary(uuid(1, 2), t, "r6-la", "r6-la-1"),
                summary(uuid(1, 1), t.minusSeconds(20), "r6-la", "r6-la-1")), null, null);
        b.handler = (e, req) -> e.respond(req, List.of(summary(uuid(2, 1), t.minusSeconds(10), "r6-lb", "r6-lb-1")),
                null, null);
        HttpResponse<String> r = get("/transactions?from=" + q(FROM) + "&to=" + q(TO) + "&size=10", "psp:" + psp);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control")).hasValue("no-store");
        JsonNode env = json(r);
        assertThat(env.get("source").asText()).isEqualTo("EDGE");
        assertThat(env.get("incomplete").asBoolean()).isFalse();
        List<String> ids = new ArrayList<>();
        env.get("items").forEach(i -> ids.add(i.get("id").asText()));
        assertThat(ids).containsExactly(uuid(1, 2), uuid(2, 1), uuid(1, 1));
        assertThat(env.get("items").get(0).get("source").asText()).isEqualTo("EDGE");
        assertThat(env.get("nextCursor").isNull()).isTrue();

        EdgeDataAccessLog row = completedAudit(psp);
        assertThat(row.getOp()).isEqualTo("txn.search");
        assertThat(row.getActorKind()).isEqualTo("PSP_USER");
        assertThat(row.getActorUserId()).isEqualTo("u-" + (1000 + psp));
        assertThat(row.getPurpose()).isEqualTo("TRANSACTION_MONITORING");
        assertThat(row.getOutcome()).isEqualTo("OK");
        assertThat(row.getSitesAnswered()).isEqualTo("r6-la,r6-lb");
        assertThat(row.getItemCount()).isEqualTo(3);
        TunnelFrameCodec.OpenedFrame sent = a.requests("txn.search").get(0);
        assertThat(sent.body().get("actor").get("pspId").asLong()).isEqualTo(psp);
        assertThat(sent.body().get("auditId").asText()).isEqualTo(row.getAuditId().toString());
    }

    /**
     * Verifies tenant isolation: a PSP user naming another PSP is refused before any pull, and a PSP
     * user asking for another PSP's transaction id only ever reaches its own Edge (404); the other
     * PSP's Edge receives no request at all.
     */
    @Test
    void pspUserCannotPullAnotherPspsData() throws Exception {
        long mine = newPsp(false);
        long theirs = newPsp(false);
        FakeEdge myEdge = connected("r6-ti-mine", "r6-ti-mine", mine);
        FakeEdge theirEdge = connected("r6-ti-theirs", "r6-ti-theirs", theirs);
        String theirId = uuid(3, 1);
        decisionRecord(theirs, theirId, "r6-ti-theirs");
        theirEdge.handler = (e, req) -> e.respond(req, List.of(detail(theirId, "r6-ti-theirs", "r6-ti-theirs")), null, null);
        myEdge.handler = (e, req) -> e.error(req, TunnelError.of(TunnelErrorCode.NOT_FOUND, "absent"));

        HttpResponse<String> named = get("/transactions?pspId=" + theirs, "psp:" + mine);
        assertThat(named.statusCode()).isEqualTo(403);
        assertThat(json(named).get("code").asText()).isEqualTo("TENANT_MISMATCH");
        HttpResponse<String> batch = call("POST", "/transactions:batchGet?pspId=" + theirs, "psp:" + mine,
                "{\"edgeAssessmentIds\":[\"" + theirId + "\"]}", Map.of());
        assertThat(batch.statusCode()).isEqualTo(403);
        HttpResponse<String> export = get("/transactions/export?pspId=" + theirs, "psp:" + mine);
        assertThat(export.statusCode()).isEqualTo(403);
        HttpResponse<String> log = get("/edge-data/access-log?pspId=" + theirs, "psp:" + mine);
        assertThat(log.statusCode()).isEqualTo(403);

        HttpResponse<String> byId = get("/transactions/" + theirId, "psp:" + mine);
        assertThat(byId.statusCode()).isEqualTo(404);
        assertThat(json(byId).get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(byId.body()).doesNotContain(CANARY_FEATURE);
        assertThat(myEdge.requests("txn.get")).hasSize(1);
        assertThat(myEdge.requests("txn.get").get(0).body().get("actor").get("pspId").asLong()).isEqualTo(mine);
        assertThat(theirEdge.frames("request")).isEmpty();
        assertThat(auditRows(theirs)).isEmpty();
    }

    /** Verifies: platform pulls need support access and a reason; refusals and pulls are both audited. */
    @Test
    void platformPullsAreGatedBySupportAccessAndAudited() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("r6-sa-1", "r6-sa", psp);
        e.handler = (edge, req) -> edge.respond(req, List.of(summary(uuid(4, 1), Instant.parse("2026-10-16T09:00:00Z"),
                "r6-sa", "r6-sa-1")), null, null);
        String reason = "Ticket HK-5512 settlement mismatch";

        HttpResponse<String> noPsp = get("/transactions", "platform");
        assertThat(noPsp.statusCode()).isEqualTo(400);
        assertThat(json(noPsp).get("param").asText()).isEqualTo("/pspId");

        HttpResponse<String> refused = call("GET", "/transactions?pspId=" + psp, "platform", null,
                Map.of("X-Support-Reason", reason));
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(json(refused).get("code").asText()).isEqualTo("SUPPORT_ACCESS_DISABLED");
        assertThat(e.frames("request")).isEmpty();
        EdgeDataAccessLog refusal = completedAudit(psp);
        assertThat(refusal.isSupportAccess()).isTrue();
        assertThat(refusal.getOutcome()).isEqualTo("SUPPORT_ACCESS_DISABLED");

        jdbc.update("UPDATE psps SET support_access_enabled = TRUE WHERE psp_id = ?", psp);
        HttpResponse<String> shortReason = call("GET", "/transactions?pspId=" + psp, "platform", null,
                Map.of("X-Support-Reason", "too short"));
        assertThat(shortReason.statusCode()).isEqualTo(403);
        HttpResponse<String> ok = call("GET", "/transactions?pspId=" + psp, "platform", null,
                Map.of("X-Support-Reason", reason));
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(json(ok).get("items")).hasSize(1);
        EdgeDataAccessLog pull = completedAudit(psp);
        assertThat(pull.isSupportAccess()).isTrue();
        assertThat(pull.getSupportReason()).isEqualTo(reason);
        assertThat(pull.getActorKind()).isEqualTo("PLATFORM_USER");
        assertThat(pull.getPurpose()).isEqualTo("SUPPORT_ACCESS");
        assertThat(pull.getOutcome()).isEqualTo("OK");
        assertThat(e.requests("txn.search").get(0).body().get("actor").get("supportReason").asText()).isEqualTo(reason);

        // The PSP sees the support pull in its own access log.
        HttpResponse<String> log = get("/edge-data/access-log?supportAccess=true", "psp:" + psp);
        assertThat(log.statusCode()).isEqualTo(200);
        JsonNode page = json(log);
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(page.get("content").get(0).get("supportReason").asText()).isEqualTo(reason);
        assertThat(page.get("content").get(0).get("supportAccess").asBoolean()).isTrue();
    }

    /** Verifies: an Edge without a session is 503 EDGE_OFFLINE with lastSeenAt, and status shows it. */
    @Test
    void offlineEdgeIsAnEnvelopeStateAndStatusShowsIt() throws Exception {
        long psp = newPsp(false);
        node("r6-off-1", "r6-off", psp);
        HttpResponse<String> r = get("/transactions", "psp:" + psp);
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(r.headers().firstValue("Cache-Control")).hasValue("no-store");
        JsonNode err = json(r);
        assertThat(err.get("code").asText()).isEqualTo("EDGE_OFFLINE");
        assertThat(err.get("lastSeenAt").asText()).isEqualTo("2026-10-01T08:00:00Z");
        assertThat(err.get("retryable").asBoolean()).isTrue();

        HttpResponse<String> s = get("/edge-data/status", "psp:" + psp);
        assertThat(s.statusCode()).isEqualTo(200);
        JsonNode site = json(s).get("sites").get(0);
        assertThat(site.get("siteId").asText()).isEqualTo("r6-off");
        assertThat(site.get("tunnel").asText()).isEqualTo("OFFLINE");
        assertThat(site.get("ledger").get("state").asText()).isEqualTo("EDGE_OFFLINE");
    }

    /** Verifies: get and batchGet go only to the decision record's site; related to the anchor's site. */
    @Test
    void getsAndRelatedAreRoutedToTheDecisionRecordSite() throws Exception {
        long psp = newPsp(false);
        FakeEdge a = connected("r6-rt-a", "r6-rt-a", psp);
        FakeEdge b = connected("r6-rt-b", "r6-rt-b", psp);
        String id = uuid(5, 1);
        String linked = uuid(5, 2);
        decisionRecord(psp, id, "r6-rt-b");
        b.handler = (e, req) -> {
            switch (req.body().get("op").asText()) {
                case "txn.get" -> e.respond(req, List.of(detail(id, "r6-rt-b", "r6-rt-b")), null, null);
                case "txn.batchGet" -> e.respond(req, List.of(detail(id, "r6-rt-b", "r6-rt-b")), null, null);
                case "txn.related" -> {
                    ObjectNode item = MAPPER.createObjectNode();
                    item.put("link", "DEVICE");
                    item.put("hop", 1);
                    item.put("viaEdgeAssessmentId", id);
                    item.set("transaction", summary(linked, Instant.parse("2026-10-15T09:00:00Z"), "r6-rt-b", "r6-rt-b"));
                    e.respond(req, List.of(item), null, null);
                }
                default -> e.error(req, TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED, "unexpected"));
            }
        };
        HttpResponse<String> one = get("/transactions/" + id, "psp:" + psp);
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(json(one).get("source").asText()).isEqualTo("EDGE");
        assertThat(json(one).get("decisionRecord").get("siteId").asText()).isEqualTo("r6-rt-b");

        HttpResponse<String> batch = call("POST", "/transactions:batchGet", "psp:" + psp,
                "{\"edgeAssessmentIds\":[\"" + id + "\"]}", Map.of());
        assertThat(batch.statusCode()).isEqualTo(200);
        assertThat(json(batch).get("items").get(0).get("edgeAssessmentId").asText()).isEqualTo(id);
        assertThat(json(batch).get("missing")).isEmpty();

        HttpResponse<String> rel = get("/transactions/" + id + "/related?linkBy=DEVICE&depth=1", "psp:" + psp);
        assertThat(rel.statusCode()).isEqualTo(200);
        JsonNode item = json(rel).get("items").get(0);
        assertThat(item.get("link").asText()).isEqualTo("DEVICE");
        assertThat(item.get("transaction").get("id").asText()).isEqualTo(linked);
        assertThat(a.frames("request")).isEmpty();
        assertThat(b.requests("txn.get")).hasSize(1);
        assertThat(b.requests("txn.batchGet")).hasSize(1);
        assertThat(b.requests("txn.related")).hasSize(1);
    }

    /** Verifies: the export streams CSV across sites under credit and is one audited pull. */
    @Test
    void exportStreamsCsvAcrossSitesUnderCredit() throws Exception {
        long psp = newPsp(false);
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        List<ObjectNode> rowsA = new ArrayList<>();
        List<ObjectNode> rowsB = new ArrayList<>();
        for (int k = 0; k < 9; k++) {
            rowsA.add(summary(uuid(6, k), t.minusSeconds(2L * k), "r6-xa", "r6-xa-1"));
            rowsB.add(summary(uuid(7, k), t.minusSeconds(2L * k + 1), "r6-xb", "r6-xb-1"));
        }
        FakeEdge a = connected("r6-xa-1", "r6-xa", psp);
        FakeEdge b = connected("r6-xb-1", "r6-xb", psp);
        a.handler = streamer(rowsA, 4);
        b.handler = streamer(rowsB, 4);
        HttpResponse<String> r = get("/transactions/export?format=csv&from=" + q(FROM) + "&to=" + q(TO)
                + "&columns=amountMinor&columns=currency", "psp:" + psp);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type")).hasValue("text/csv;charset=UTF-8");
        assertThat(r.headers().firstValue("Cache-Control")).hasValue("no-store");
        String[] lines = r.body().split("\r\n");
        assertThat(lines[0]).isEqualTo("﻿edgeAssessmentId,evaluatedAt,siteId,decision,amountMinor,currency,source");
        assertThat(lines).hasSize(19);
        assertThat(lines[1]).startsWith(uuid(6, 0) + ",");
        assertThat(lines[2]).startsWith(uuid(7, 0) + ",");
        assertThat(a.creditViolations.get() + b.creditViolations.get()).isZero();
        EdgeDataAccessLog row = completedAudit(psp);
        assertThat(row.getOp()).isEqualTo("export.stream");
        assertThat(row.getPurpose()).isEqualTo("DATA_EXPORT");
        assertThat(row.getItemCount()).isEqualTo(18);
        assertThat(row.getOutcome()).isEqualTo("OK");
    }

    /** Verifies: status shows each site's tunnel and ledger health; the access log is paged and exportable. */
    @Test
    void statusAndAccessLogArePspScoped() throws Exception {
        long psp = newPsp(false);
        long other = newPsp(false);
        FakeEdge e = connected("r6-st-1", "r6-st", psp);
        e.handler = (edge, req) -> {
            ObjectNode health = (ObjectNode) ContractFixtures.fixture("ledger.health.item--ok.json");
            health.put("siteId", "r6-st");
            health.put("nodeId", "r6-st-1");
            edge.respond(req, List.of(health), null, null);
        };
        HttpResponse<String> s = get("/edge-data/status", "psp:" + psp);
        assertThat(s.statusCode()).isEqualTo(200);
        JsonNode site = json(s).get("sites").get(0);
        assertThat(site.get("tunnel").asText()).isEqualTo("ONLINE");
        assertThat(site.get("nodes").get(0).get("connected").asBoolean()).isTrue();
        assertThat(site.get("ledger").get("status").asText()).isEqualTo("OK");
        assertThat(site.get("ledger").get("holds").get("active").asInt()).isEqualTo(1);

        for (int i = 0; i < 3; i++) {
            get("/edge-data/status", "psp:" + psp);
        }
        HttpResponse<String> page = get("/edge-data/access-log?size=2&page=1&op=ledger.health", "psp:" + psp);
        assertThat(page.statusCode()).isEqualTo(200);
        JsonNode p = json(page);
        assertThat(p.get("totalElements").asLong()).isEqualTo(4);
        assertThat(p.get("totalPages").asInt()).isEqualTo(2);
        assertThat(p.get("number").asInt()).isEqualTo(1);
        assertThat(p.get("content")).hasSize(2);
        assertThat(p.get("content").get(0).get("op").asText()).isEqualTo("ledger.health");
        assertThat(json(get("/edge-data/access-log", "psp:" + other)).get("totalElements").asLong()).isZero();

        HttpResponse<String> nd = get("/edge-data/access-log/export?format=ndjson", "psp:" + psp);
        assertThat(nd.statusCode()).isEqualTo(200);
        assertThat(nd.headers().firstValue("Cache-Control")).hasValue("no-store");
        String[] lines = nd.body().split("\n");
        assertThat(lines).hasSize(5);
        assertThat(MAPPER.readTree(lines[4]).get("@summary").get("rows").asInt()).isEqualTo(4);
        HttpResponse<String> platform = get("/edge-data/access-log?pspId=" + psp, "platform");
        assertThat(json(platform).get("totalElements").asLong()).isEqualTo(4);
        assertThat(get("/edge-data/access-log", "platform").statusCode()).isEqualTo(400);
    }

    /**
     * §7.16 canary across the new endpoints: canary amount, feature and reason pulled through list,
     * get, batchGet, related and export (and returned to the caller), then searched for in every log
     * line (TRACE for the application and Spring MVC, with stack traces), every column of every
     * table, every Redis key and value, and every relayed pub/sub message.
     */
    @Test
    void canaryNeverReachesLogsDatabaseOrRedis() throws Exception {
        long psp = newPsp(false);
        FakeEdge e = connected("r6-cn-1", "r6-cn", psp);
        String id = uuid(8, 1);
        String linked = uuid(8, 2);
        decisionRecord(psp, id, "r6-cn");
        Instant t = Instant.parse("2026-10-16T09:00:00Z");
        e.handler = (edge, req) -> {
            switch (req.body().get("op").asText()) {
                case "txn.search" -> edge.respond(req, List.of(summary(id, t, "r6-cn", "r6-cn-1")), null, null);
                case "txn.related" -> {
                    ObjectNode item = MAPPER.createObjectNode();
                    item.put("link", "CARD");
                    item.put("hop", 1);
                    item.put("viaEdgeAssessmentId", id);
                    item.set("transaction", summary(linked, t.minusSeconds(5), "r6-cn", "r6-cn-1"));
                    edge.respond(req, List.of(item), null, null);
                }
                case "export.stream" -> streamer(List.of(summary(id, t, "r6-cn", "r6-cn-1")), 10).onRequest(edge, req);
                default -> edge.respond(req, List.of(detail(id, "r6-cn", "r6-cn-1")), null, null);
            }
        };
        String user = "psp:" + psp;
        HttpResponse<String> list = get("/transactions?from=" + q(FROM) + "&to=" + q(TO), user);
        HttpResponse<String> one = get("/transactions/" + id, user);
        HttpResponse<String> batch = call("POST", "/transactions:batchGet", user,
                "{\"edgeAssessmentIds\":[\"" + id + "\"]}", Map.of());
        HttpResponse<String> rel = get("/transactions/" + id + "/related", user);
        HttpResponse<String> export = get("/transactions/export?format=ndjson&from=" + q(FROM) + "&to=" + q(TO), user);
        for (HttpResponse<String> r : List.of(list, one, batch, rel, export)) {
            assertThat(r.statusCode()).as(r.uri().toString()).isEqualTo(200);
            assertThat(r.headers().firstValue("Cache-Control")).as(r.uri().toString()).hasValue("no-store");
        }
        assertThat(list.body()).contains(String.valueOf(CANARY_AMOUNT));
        assertThat(one.body()).contains(CANARY_FEATURE, CANARY_REASON);
        assertThat(batch.body()).contains(CANARY_FEATURE);
        assertThat(rel.body()).contains(String.valueOf(CANARY_AMOUNT));
        assertThat(export.body()).contains(String.valueOf(CANARY_AMOUNT));
        completedAudit(psp);

        List<String> canaries = List.of(String.valueOf(CANARY_AMOUNT), CANARY_FEATURE, CANARY_REASON);
        assertThat(logs.list).anyMatch(ev -> ev.getLoggerName().startsWith("org.springframework.web")
                && ev.getLevel() == Level.TRACE);
        for (ILoggingEvent ev : List.copyOf(logs.list)) {
            String text = ev.getFormattedMessage() + (ev.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(ev.getThrowableProxy()));
            for (String c : canaries) {
                assertThat(text).as("log line from %s", ev.getLoggerName()).doesNotContain(c);
            }
        }
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("edge_data_access_log", "edge_nodes", "psps");
        for (String table : tables) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM \"" + table + "\"")) {
                String text = String.valueOf(row.values());
                for (String c : canaries) {
                    assertThat(text).as("row of %s", table).doesNotContain(c);
                }
            }
        }
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
        assertThat(relayed).isNotEmpty();
        for (String m : List.copyOf(relayed)) {
            for (String c : canaries) {
                assertThat(m).doesNotContain(c);
            }
        }
    }

    /** Fake Edge export: chunks of {@code size}, each within credit, on their own thread. */
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
        }, "r6-fake-edge-export").start();
    }
}
