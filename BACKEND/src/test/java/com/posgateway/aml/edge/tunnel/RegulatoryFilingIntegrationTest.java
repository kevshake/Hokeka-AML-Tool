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
import com.posgateway.aml.controller.compliance.RegulatoryFilingController;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.filing.LegalHold;
import com.posgateway.aml.entity.filing.LegalHoldSiteAck;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.exception.EdgeDataExceptionHandler;
import com.posgateway.aml.exception.FilingExceptionHandler;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.filing.LegalHoldRepository;
import com.posgateway.aml.repository.filing.LegalHoldSiteAckRepository;
import com.posgateway.aml.repository.filing.LegalHoldWorkflowRepository;
import com.posgateway.aml.repository.filing.RegulatoryFiledDocumentRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.SarWorkflowService;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import com.posgateway.aml.service.compliance.ComplianceEntityLinkService;
import com.posgateway.aml.service.compliance.RegulatoryDeadlineService;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeTransactionProxyService;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.edgedata.LegacyTransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionSourceResolver;
import com.posgateway.aml.service.filing.FiledDocumentAccessService;
import com.posgateway.aml.service.filing.FiledDocumentStore;
import com.posgateway.aml.service.filing.FilingDataService;
import com.posgateway.aml.service.filing.GoAmlFilingRenderer;
import com.posgateway.aml.service.filing.RegulatoryFilingService;
import com.posgateway.aml.service.legalhold.LegalHoldService;
import com.posgateway.aml.service.legalhold.LegalHoldTunnelListener;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import com.posgateway.aml.service.security.PspIsolationService;
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
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * End-to-end R9 filing and legal holds on the R4/R6 harness: two real Control Plane instances
 * (embedded Tomcat, Spring MVC + WebSocket, shared H2, a real Redis) with the R9 controller and
 * services, fake Edge Nodes connected to the <em>other</em> instance (every pull and notify is
 * relayed sealed through Redis), and real HTTP calls from a Console stand-in.
 *
 * <p>Covers: SAR filing pulls {@code filing.extract} as the filer and stores the encrypted, sealed
 * document, which then downloads without an Edge pull; offline and partial Edges refuse (nothing
 * filed) and a retry succeeds; CTR filing with the Edge aggregate; filing roles and tenant
 * isolation; hold.place delivery and hold.ack tracking; re-send of unconfirmed holds after every
 * reconnect, none once confirmed; release; and the residency canary (the pulled amount appears in
 * no log line, no Redis key or relayed message, and in no database column in plaintext).
 *
 * <p>Needs a Redis: {@code docker run -d --rm --name r9-redis -p 127.0.0.1:56491:6379
 * redis:7.4-alpine} and {@code -Dtunnel.it.redis.port=56491}. Skipped without the property.
 */
@EnabledIfSystemProperty(named = "tunnel.it.redis.port", matches = "\\d+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegulatoryFilingIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Pulled amount in minor units; rendered as 9182736.45 KES in the filed document. */
    private static final long CANARY_AMOUNT = 918_273_645L;
    private static final String CANARY_MAJOR = "9182736.45";
    private static final String CANARY_FEATURE = "canary-feature-r9-5513";

    /** Users the Console stand-in signs in as ({@code X-Test-User} header). */
    static final Map<String, User> USERS = new ConcurrentHashMap<>();

    private ConfigurableApplicationContext cpA;
    private ConfigurableApplicationContext cpB;
    private EdgeNodeRepository nodes;
    private EdgeDataAccessLogRepository auditRepo;
    private JdbcTemplate jdbc;
    private StringRedisTemplate redis;
    private String ingestPublicB64;
    private java.security.PublicKey cpSigningPublic;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<FakeEdge> edges = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicLong pspSeq = new AtomicLong(900);
    private final AtomicLong idSeq = new AtomicLong(7_000);
    private final HttpClient http = HttpClient.newHttpClient();

    // ── test application ─────────────────────────────────────────────────────────────────────────

    /** Control Plane with the tunnel, the R6 proxy and the R9 filing; workflow repositories are mocks. */
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class, JacksonAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class, RedisAutoConfiguration.class})
    @EntityScan(basePackageClasses = {EdgeNode.class, LegalHold.class})
    @EnableJpaRepositories(basePackageClasses = {EdgeNodeRepository.class, LegalHoldRepository.class},
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {EdgeNodeRepository.class, EdgeDataAccessLogRepository.class, LegalHoldRepository.class,
                            LegalHoldWorkflowRepository.class, LegalHoldSiteAckRepository.class,
                            RegulatoryFiledDocumentRepository.class}))
    @Import({EdgeTunnelConfiguration.class, EdgeProperties.class, EdgeControlPlaneKeys.class,
            EdgeIdentityResolver.class, EdgeReplayGuard.class, EdgeTunnelIntegrationTest.TestPolicies.class,
            FilingBeans.class, RegulatoryFilingController.class, RegulatoryFilingService.class, FilingDataService.class,
            GoAmlFilingRenderer.class, FiledDocumentStore.class, FiledDocumentAccessService.class,
            LegalHoldService.class, LegalHoldTunnelListener.class, SarWorkflowService.class, PermissionService.class,
            EdgeDataCallerResolver.class, TransactionSourceResolver.class, EdgeTransactionProxyService.class,
            LegacyTransactionReadService.class, EdgeTunnelAccess.class, EdgeDataNoStoreFilter.class,
            EdgeDataExceptionHandler.class, FilingExceptionHandler.class, PspIsolationService.class})
    static class FilingItApp {
    }

    /** Collaborators outside this test's scope, as mocks; and the sign-in filter. */
    static class FilingBeans {

        /** SAR rows: stubbed per test. */
        @Bean
        SuspiciousActivityReportRepository suspiciousActivityReportRepository() {
            SuspiciousActivityReportRepository m = Mockito.mock(SuspiciousActivityReportRepository.class);
            Mockito.when(m.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return m;
        }

        /** CTR deadlines: stubbed per test. */
        @Bean
        ComplianceDeadlineRepository complianceDeadlineRepository() {
            ComplianceDeadlineRepository m = Mockito.mock(ComplianceDeadlineRepository.class);
            Mockito.when(m.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return m;
        }

        /** Alerts behind CTR deadlines: stubbed per test. */
        @Bean
        AlertRepository alertRepository() {
            return Mockito.mock(AlertRepository.class);
        }

        /** Decision records (site routing): stubbed per test. */
        @Bean
        AssessmentRepository assessmentRepository() {
            return Mockito.mock(AssessmentRepository.class);
        }

        /** Audit log: verified per test. */
        @Bean
        AuditLogService auditLogService() {
            return Mockito.mock(AuditLogService.class);
        }

        /** SAR deadline policy: not exercised (no drafting here). */
        @Bean
        RegulatoryDeadlineService regulatoryDeadlineService() {
            return Mockito.mock(RegulatoryDeadlineService.class);
        }

        /** Compliance calendar: verified per test. */
        @Bean
        ComplianceCalendarService complianceCalendarService() {
            return Mockito.mock(ComplianceCalendarService.class);
        }

        /** Case/SAR links: not exercised. */
        @Bean
        ComplianceEntityLinkService complianceEntityLinkService() {
            return Mockito.mock(ComplianceEntityLinkService.class);
        }

        /** Retention 5 years for every PSP. */
        @Bean
        PspDataSettingsService pspDataSettingsService() {
            PspDataSettingsService m = Mockito.mock(PspDataSettingsService.class);
            Mockito.when(m.retentionState(any())).thenReturn(new PspDataSettingsService.RetentionState(5, Instant.EPOCH, null));
            return m;
        }

        /** Legacy rows: never read for Edge PSPs. */
        @Bean
        TransactionRepository transactionRepository() {
            return Mockito.mock(TransactionRepository.class);
        }

        /** Legacy keyset paging: unused. */
        @Bean
        KeysetSliceService keysetSliceService() {
            return Mockito.mock(KeysetSliceService.class);
        }

        /** Legacy reference translation: unused. */
        @Bean
        TransactionReferenceResolver transactionReferenceResolver() {
            return Mockito.mock(TransactionReferenceResolver.class);
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
        if (System.getenv("SECURITY_ENCRYPTION_KEY") == null && System.getenv("ENCRYPTION_KEY") == null
                && System.getProperty("SECURITY_ENCRYPTION_KEY") == null) {
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            System.setProperty("SECURITY_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(key));
        }
        KeyPair sign = FakeEdge.gen("Ed25519");
        KeyPair ingest = FakeEdge.gen("X25519");
        String signPriv = Base64.getEncoder().encodeToString(sign.getPrivate().getEncoded());
        String signPub = Base64.getEncoder().encodeToString(EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) sign.getPublic()));
        String ingestPriv = Base64.getEncoder().encodeToString(ingest.getPrivate().getEncoded());
        String ingestPub = Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) ingest.getPublic()));
        String port = System.getProperty("tunnel.it.redis.port");
        cpA = boot("r9-cp-a", "create", signPriv, signPub, ingestPriv, ingestPub, port);
        cpB = boot("r9-cp-b", "none", signPriv, signPub, ingestPriv, ingestPub, port);

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs.start();
        root.addAppender(logs);
        for (String name : List.of("com.posgateway.aml", "org.springframework.web", "org.hibernate.SQL",
                "org.hibernate.orm.jdbc.bind")) {
            ((Logger) LoggerFactory.getLogger(name)).setLevel(Level.TRACE);
        }
        nodes = cpA.getBean(EdgeNodeRepository.class);
        auditRepo = cpA.getBean(EdgeDataAccessLogRepository.class);
        jdbc = cpA.getBean(JdbcTemplate.class);
        redis = new StringRedisTemplate(cpA.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        ingestPublicB64 = cpA.getBean(EdgeControlPlaneKeys.class).ingestPublicKeyBase64();
        cpSigningPublic = cpA.getBean(EdgeControlPlaneKeys.class).signingPublicKey();
        jdbc.execute("CREATE TABLE IF NOT EXISTS psps (psp_id BIGINT PRIMARY KEY, support_access_enabled BOOLEAN)");

        Role platformRole = new Role();
        platformRole.setName("PLATFORM_ADMIN");
        platformRole.setPermissions(Set.of(Permission.FILE_SAR));
        USERS.put("platform", User.builder().id(1L).username("ops").email("o@x").passwordHash("").role(platformRole)
                .build());
    }

    /** Boots one instance. */
    private ConfigurableApplicationContext boot(String id, String ddl, String signPriv, String signPub,
                                                String ingestPriv, String ingestPub, String redisPort) {
        return new SpringApplicationBuilder(FilingItApp.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.ofEntries(
                        Map.entry("spring.config.name", "r9-filing-it-no-application-properties"),
                        Map.entry("server.port", "0"),
                        Map.entry("server.servlet.context-path", "/api/v1"),
                        Map.entry("spring.main.banner-mode", "off"),
                        Map.entry("spring.datasource.url", "jdbc:h2:mem:r9filing;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
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

    /** Closes the Edges and both instances. */
    @AfterAll
    void stop() {
        edges.forEach(FakeEdge::close);
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

    /** A PSP row plus its MLRO ({@code mlro:<id>}) and a PSP operator ({@code op:<id>}). */
    private long newPsp(boolean supportAccess) {
        long psp = pspSeq.incrementAndGet();
        jdbc.update("INSERT INTO psps (psp_id, support_access_enabled) VALUES (?, ?)", psp, supportAccess);
        USERS.put("mlro:" + psp, user(2000 + psp, psp, "MLRO", Set.of(Permission.FILE_SAR)));
        USERS.put("op:" + psp, user(3000 + psp, psp, "PSP_USER", Set.of()));
        return psp;
    }

    /** A user of a PSP. */
    private static User user(long id, long psp, String roleName, Set<Permission> permissions) {
        Role role = new Role();
        role.setName(roleName);
        role.setPermissions(new HashSet<>(permissions));
        Psp p = new Psp();
        p.setPspId(psp);
        return User.builder().id(id).username(roleName.toLowerCase() + "-" + psp).email("x").passwordHash("")
                .firstName("Mary").lastName("Officer").role(role).psp(p).build();
    }

    /** Registers an ACTIVE, activated node pinned to a fake Edge's keys (not connected). */
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

    /** Connects a registered node to cp-b (the Console talks to cp-a). */
    private FakeEdge connect(FakeEdge e) throws Exception {
        int port = ((ServletWebServerApplicationContext) cpB).getWebServer().getPort();
        if (!edges.contains(e)) {
            edges.add(e);
        }
        return e.start(URI.create("ws://127.0.0.1:" + port + "/api/v1/edge/tunnel"), 1_000);
    }

    /** One HTTP exchange with cp-a as {@code user}. */
    private HttpResponse<String> call(String method, String path, String user, String body) throws Exception {
        int port = ((ServletWebServerApplicationContext) cpA).getWebServer().getPort();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .header("X-Test-User", user);
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Parses a JSON body. */
    private static JsonNode json(HttpResponse<String> r) throws Exception {
        return MAPPER.readTree(r.body());
    }

    /** Deterministic lowercase UUID. */
    private static String uuid(int group, int i) {
        return String.format("%08x-0000-4000-8000-%012x", 0x9000 + group, i);
    }

    /** A detail item carrying the canary amount and feature. */
    private static ObjectNode detail(String id, String site, String node) {
        ObjectNode d = (ObjectNode) ContractFixtures.fixture("txn.get.item--detail.json");
        d.put("edgeAssessmentId", id);
        d.put("siteId", site);
        d.put("nodeId", node);
        d.put("amountMinor", CANARY_AMOUNT);
        d.remove("txnRef");
        ((ObjectNode) d.get("features")).put("memo", CANARY_FEATURE);
        return d;
    }

    /** filing.extract answers from the ids an Edge holds; absent ids are reported missing. */
    private static FakeEdge.RequestHandler extractor(Map<String, ObjectNode> held, ObjectNode aggregate) {
        return (e, req) -> {
            if (!"filing.extract".equals(req.body().get("op").asText())) {
                e.error(req, TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED, "unexpected"));
                return;
            }
            List<ObjectNode> items = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (JsonNode id : req.body().get("params").get("edgeAssessmentIds")) {
                ObjectNode d = held.get(id.asText());
                if (d == null) {
                    missing.add(id.asText());
                } else {
                    ObjectNode item = MAPPER.createObjectNode();
                    item.put("kind", "TRANSACTION");
                    item.set("transaction", d);
                    items.add(item);
                }
            }
            if (aggregate != null && "CTR".equals(req.body().get("params").get("kind").asText())) {
                items.add(aggregate);
            }
            e.respond(req, items, null, missing);
        };
    }

    /** Edge-side hold book that acknowledges hold notifies like the real Edge (§7.10). */
    static final class HoldBook implements FakeEdge.NotifyHandler {
        final Set<String> active = new ConcurrentSkipListSet<>();
        final List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean ack = true;

        /** Applies the notify and answers hold.ack with the digest of the active set. */
        @Override
        public void onNotify(FakeEdge edge, TunnelFrameCodec.OpenedFrame n) throws Exception {
            String type = n.body().get("type").asText();
            String holdId = n.body().path("holdId").asText();
            seen.add(type + ":" + holdId);
            if (!ack || !type.startsWith("hold.")) {
                return;
            }
            String state;
            if ("hold.place".equals(type)) {
                active.add(holdId);
                state = "APPLIED";
            } else {
                state = active.remove(holdId) ? "RELEASED" : "UNKNOWN_HOLD";
            }
            ObjectNode item = MAPPER.createObjectNode();
            item.put("holdId", holdId);
            item.put("state", state);
            item.put("matchedRows", n.body().path("scope").path("edgeAssessmentIds").size());
            item.put("activeHoldDigest", Jcs.sha256Hex(String.join("\n", active).getBytes(StandardCharsets.UTF_8)));
            ObjectNode body = MAPPER.createObjectNode().put("op", "hold.ack").put("status", "OK").put("seq", 0)
                    .put("last", true);
            body.putArray("items").add(item);
            edge.sendSealed("response", n.id(), body);
        }
    }

    /** An APPROVED SAR of {@code psp} (maker-checker done) over the given references, served by cp-a's mock. */
    private SuspiciousActivityReport approvedSar(long psp, List<TransactionReference> refs) {
        long id = idSeq.incrementAndGet();
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setId(id);
        sar.setSarReference("SAR-R9-" + id);
        sar.setPspId(psp);
        sar.setStatus(SarStatus.APPROVED);
        sar.setJurisdiction("KE");
        sar.setSuspiciousActivityType("SANCTIONS");
        sar.setNarrative("Repeated cash-out to a listed counterparty");
        sar.setCreatedBy(user(10, psp, "INVESTIGATOR", Set.of()));
        sar.setApprovedBy(user(11, psp, "MLRO", Set.of()));
        sar.setTransactionReferences(refs);
        SuspiciousActivityReportRepository repo = cpA.getBean(SuspiciousActivityReportRepository.class);
        Mockito.when(repo.findById(id)).thenReturn(Optional.of(sar));
        Mockito.when(repo.lockById(id)).thenReturn(Optional.of(sar));
        return sar;
    }

    /** The PSP's decision record naming a site (cp-a routes gets by it). */
    private void decisionRecord(long psp, String id, String site) {
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setPspId(psp);
        a.setEdgeAssessmentId(UUID.fromString(id));
        a.setSiteId(site);
        AssessmentRepository repo = cpA.getBean(AssessmentRepository.class);
        Mockito.when(repo.findReferencedByPsp(eq(psp), any())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(1);
            return ids.contains(UUID.fromString(id)) ? List.of(a) : List.of();
        });
    }

    /** The PSP's access-log rows, newest first. */
    private List<EdgeDataAccessLog> auditRows(long psp) {
        return auditRepo.findAll().stream().filter(r -> r.getPspId() == psp)
                .sorted((x, y) -> y.getId().compareTo(x.getId())).toList();
    }

    /** The site-ack rows of the PSP's holds. */
    private List<LegalHoldSiteAck> siteAcks(long psp) {
        List<UUID> ids = cpA.getBean(LegalHoldRepository.class).findByPspId(psp).stream().map(LegalHold::getHoldId).toList();
        return cpA.getBean(LegalHoldSiteAckRepository.class).findByHoldIdIn(ids);
    }

    /** Polls until the condition holds or fails the test after the timeout. */
    private static void await(BooleanSupplier condition, long timeoutMs, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    // ── tests ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the SAR filing chain: filing.extract as the filer (purpose SAR_FILING, filing
     * reference, both audit rows), the document stored encrypted and sealed, FILED, a hold over the
     * transactions delivered and acknowledged, the download served from the store without an Edge
     * pull, a second filing refused — and the canary nowhere outside the document.
     */
    @Test
    void filesSarFromTheEdgeAndServesTheStoredDocument() throws Exception {
        long psp = newPsp(false);
        String id = uuid(1, 1);
        FakeEdge edge = connect(node("r9-sar-1", "r9-sar", psp));
        HoldBook book = new HoldBook();
        edge.notifyHandler = book;
        edge.handler = extractor(Map.of(id, detail(id, "r9-sar", "r9-sar-1")), null);
        decisionRecord(psp, id, "r9-sar");
        SuspiciousActivityReport sar = approvedSar(psp,
                List.of(TransactionReference.ofDecisionRecord(UUID.fromString(id), "AUTH-R9-1", "r9-sar")));

        HttpResponse<String> r = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp,
                "{\"regulatorReference\":\"FRC/2026/0091\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        JsonNode result = json(r);
        assertThat(result.get("status").asText()).isEqualTo("FILED");
        long docId = result.get("document").get("id").asLong();
        assertThat(result.get("document").get("transactionCount").asInt()).isEqualTo(1);
        assertThat(result.get("document").get("dataSource").asText()).isEqualTo("EDGE");
        assertThat(result.get("document").get("regulatorReference").asText()).isEqualTo("FRC/2026/0091");
        assertThat(sar.getStatus()).isEqualTo(SarStatus.FILED);
        assertThat(sar.getFilingReferenceNumber()).isEqualTo("FRC/2026/0091");

        TunnelFrameCodec.OpenedFrame pull = edge.requests("filing.extract").get(0);
        assertThat(pull.body().get("params").get("kind").asText()).isEqualTo("SAR");
        assertThat(pull.body().get("params").get("filingRef").asText()).isEqualTo(sar.getSarReference());
        assertThat(pull.body().get("actor").get("purpose").asText()).isEqualTo("SAR_FILING");
        assertThat(pull.body().get("actor").get("kind").asText()).isEqualTo("PSP_USER");
        EdgeDataAccessLog row = auditRows(psp).get(0);
        assertThat(row.getOp()).isEqualTo("filing.extract");
        assertThat(row.getFilingRef()).isEqualTo(sar.getSarReference());
        assertThat(row.getOutcome()).isEqualTo("OK");

        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM regulatory_filed_documents WHERE id = ?", docId);
        assertThat(String.valueOf(stored.get("content_ciphertext"))).startsWith("enc:v1:");
        assertThat(String.valueOf(stored.get("edge_audit_ids"))).isEqualTo(row.getAuditId().toString());

        // The stored record downloads without contacting the Edge; its seal matches.
        int requestsBefore = edge.frames("request").size();
        HttpResponse<String> dl = call("GET", "/filed-documents/" + docId + "/content", "mlro:" + psp, null);
        assertThat(dl.statusCode()).isEqualTo(200);
        assertThat(dl.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(dl.body()).contains("<tran_amount_original>" + CANARY_MAJOR + "</tran_amount_original>",
                "<report_code>SAR</report_code>", CANARY_FEATURE);
        assertThat(dl.headers().firstValue("X-Content-SHA256"))
                .hasValue(Jcs.sha256Hex(dl.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(edge.frames("request")).hasSize(requestsBefore);
        Mockito.verify(cpA.getBean(AuditLogService.class)).logAction(any(), eq("FILED_DOCUMENT_DOWNLOAD"),
                eq("REGULATORY_FILED_DOCUMENT"), eq(String.valueOf(docId)), any(), any(), any(), any());

        // The hold over the filed transaction reaches the Edge and is acknowledged.
        await(() -> book.active.size() == 1, 5_000, "hold.place applied at the Edge");
        TunnelFrameCodec.OpenedFrame place = edge.frames("notify").get(0);
        assertThat(place.body().get("reasonCode").asText()).isEqualTo("SAR");
        assertThat(place.body().get("reference").asText()).isEqualTo(sar.getSarReference());
        assertThat(place.body().get("scope").get("edgeAssessmentIds").get(0).asText()).isEqualTo(id);
        await(() -> siteAcks(psp).stream().anyMatch(LegalHoldSiteAck::isConfirmed), 5_000, "hold.ack recorded");
        LegalHoldSiteAck ack = siteAcks(psp).get(0);
        assertThat(ack.getSiteId()).isEqualTo("r9-sar");
        assertThat(ack.getAckState()).isEqualTo("APPLIED");
        assertThat(ack.getDigestMatches()).isTrue();
        assertThat(cpA.getBean(LegalHoldRepository.class).findByPspId(psp).get(0).getReleaseBasisAt()).isNotNull();

        // Filed once: a second filing is refused; the summary and register show it.
        HttpResponse<String> again = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp, null);
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(json(again).get("code").asText()).isEqualTo("ALREADY_FILED");
        assertThat(json(call("GET", "/sar/" + sar.getId() + "/filed-document", "mlro:" + psp, null)).get("id").asLong())
                .isEqualTo(docId);
        JsonNode register = json(call("GET", "/filed-documents", "mlro:" + psp, null));
        assertThat(register.get("totalElements").asLong()).isEqualTo(1);
        assertThat(register.get("content").get(0).get("filingRef").asText()).isEqualTo(sar.getSarReference());
        HttpResponse<String> csv = call("GET", "/filed-documents/export?format=csv", "mlro:" + psp, null);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains(sar.getSarReference()).doesNotContain(CANARY_MAJOR);

        // Residency canary: the pulled values exist only inside the encrypted document.
        List<String> canaries = List.of(String.valueOf(CANARY_AMOUNT), CANARY_MAJOR, CANARY_FEATURE);
        for (ILoggingEvent ev : List.copyOf(logs.list)) {
            String text = ev.getFormattedMessage() + (ev.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(ev.getThrowableProxy()));
            for (String c : canaries) {
                assertThat(text).as("log line from %s", ev.getLoggerName()).doesNotContain(c);
            }
        }
        for (String table : jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class)) {
            for (Map<String, Object> dbRow : jdbc.queryForList("SELECT * FROM \"" + table + "\"")) {
                for (String c : canaries) {
                    assertThat(String.valueOf(dbRow.values())).as("row of %s", table).doesNotContain(c);
                }
            }
        }
        for (String key : new HashSet<>(redis.keys("*"))) {
            String type = redis.type(key).code();
            String value = switch (type) {
                case "string" -> redis.opsForValue().get(key);
                case "hash" -> String.valueOf(redis.opsForHash().entries(key));
                default -> type;
            };
            for (String c : canaries) {
                assertThat(key + " " + value).doesNotContain(c);
            }
        }
    }

    /**
     * Verifies: an offline Edge, or a partial answer leaving a transaction missing, refuses the
     * filing with a retryable 503 and changes nothing; once every site answers the retry files.
     */
    @Test
    void offlineOrPartialEdgeRefusesAndRetrySucceeds() throws Exception {
        long psp = newPsp(false);
        String a = uuid(2, 1);
        String b = uuid(2, 2);
        FakeEdge siteA = node("r9-pa-1", "r9-pa", psp);
        FakeEdge siteB = node("r9-pb-1", "r9-pb", psp);
        SuspiciousActivityReport sar = approvedSar(psp, List.of(
                TransactionReference.ofDecisionRecord(UUID.fromString(a), null, null),
                TransactionReference.ofDecisionRecord(UUID.fromString(b), null, null)));

        HttpResponse<String> offline = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp, null);
        assertThat(offline.statusCode()).isEqualTo(503);
        JsonNode err = json(offline);
        assertThat(err.get("code").asText()).isEqualTo("EDGE_PULL_FAILED");
        assertThat(err.get("retryable").asBoolean()).isTrue();
        assertThat(err.get("edgeError").get("code").asText()).isEqualTo("EDGE_OFFLINE");
        assertThat(sar.getStatus()).isEqualTo(SarStatus.APPROVED);

        connect(siteA).handler = extractor(Map.of(a, detail(a, "r9-pa", "r9-pa-1")), null);
        HttpResponse<String> partial = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp, null);
        assertThat(partial.statusCode()).isEqualTo(503);
        JsonNode p = json(partial);
        assertThat(p.get("code").asText()).isEqualTo("FILING_INCOMPLETE");
        assertThat(p.get("retryable").asBoolean()).isTrue();
        assertThat(p.get("missingCount").asInt()).isEqualTo(1);
        assertThat(p.get("unavailableSites").get(0).get("siteId").asText()).isEqualTo("r9-pb");
        assertThat(sar.getStatus()).isEqualTo(SarStatus.APPROVED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regulatory_filed_documents WHERE psp_id = ?", Integer.class,
                psp)).isZero();

        connect(siteB).handler = extractor(Map.of(b, detail(b, "r9-pb", "r9-pb-1")), null);
        HttpResponse<String> retry = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp, null);
        assertThat(retry.statusCode()).as(retry.body()).isEqualTo(200);
        assertThat(json(retry).get("document").get("transactionCount").asInt()).isEqualTo(2);
        assertThat(sar.getStatus()).isEqualTo(SarStatus.FILED);
    }

    /** Verifies: a CTR deadline files with the Edge's cash aggregate and completes; its ids are held. */
    @Test
    void filesCtrWithTheEdgeAggregate() throws Exception {
        long psp = newPsp(false);
        String anchor = uuid(3, 1);
        String other = uuid(3, 2);
        FakeEdge edge = connect(node("r9-ctr-1", "r9-ctr", psp));
        HoldBook book = new HoldBook();
        edge.notifyHandler = book;
        ObjectNode agg = (ObjectNode) ContractFixtures.fixture("filing.extract.item--ctr-aggregate.json");
        agg.put("anchorEdgeAssessmentId", anchor);
        agg.putArray("edgeAssessmentIds").add(anchor).add(other);
        agg.put("txnCount", 2);
        edge.handler = extractor(Map.of(anchor, detail(anchor, "r9-ctr", "r9-ctr-1")), agg);
        decisionRecord(psp, anchor, "r9-ctr");
        long deadlineId = idSeq.incrementAndGet();
        ComplianceDeadline d = new ComplianceDeadline();
        d.setId(deadlineId);
        d.setPspId(psp);
        d.setDeadlineType("CTR_FILING");
        d.setSourceType("ALERT");
        d.setSourceId(deadlineId + 1);
        d.setCompleted(false);
        d.setJurisdiction("KE");
        Alert alert = new Alert();
        alert.setAlertId(deadlineId + 1);
        alert.setPspId(psp);
        alert.setEdgeAssessmentId(UUID.fromString(anchor));
        ComplianceDeadlineRepository deadlines = cpA.getBean(ComplianceDeadlineRepository.class);
        Mockito.when(deadlines.findById(deadlineId)).thenReturn(Optional.of(d));
        Mockito.when(deadlines.lockById(deadlineId)).thenReturn(Optional.of(d));
        Mockito.when(cpA.getBean(AlertRepository.class).findById(deadlineId + 1)).thenReturn(Optional.of(alert));

        HttpResponse<String> r = call("POST", "/ctr/" + deadlineId + "/file", "mlro:" + psp, "{}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(d.getCompleted()).isTrue();
        long docId = json(r).get("document").get("id").asLong();
        assertThat(json(r).get("document").get("documentKind").asText()).isEqualTo("CTR");
        assertThat(edge.requests("filing.extract").get(0).body().get("params").get("kind").asText()).isEqualTo("CTR");
        assertThat(edge.requests("filing.extract").get(0).body().get("actor").get("purpose").asText())
                .isEqualTo("CTR_FILING");
        String xml = call("GET", "/filed-documents/" + docId + "/content", "mlro:" + psp, null).body();
        assertThat(xml).contains("<cash_total>16170.00</cash_total>", "<reporting_threshold>15000.00</reporting_threshold>",
                "<reference>" + other + "</reference>", "<ctr_reference>CTR-" + deadlineId + "</ctr_reference>");
        await(() -> book.active.size() == 1, 5_000, "CTR hold applied");
        JsonNode scope = edge.frames("notify").get(0).body().get("scope").get("edgeAssessmentIds");
        assertThat(List.of(scope.get(0).asText(), scope.get(1).asText())).containsExactlyInAnyOrder(anchor, other);
        assertThat(edge.frames("notify").get(0).body().get("reasonCode").asText()).isEqualTo("CTR");
    }

    /**
     * Verifies §7.10: holds placed while the Edge is offline are sent after its hello.ack; an
     * unacknowledged hold is re-sent after every reconnect; a confirmed one is not; a release is
     * delivered and acknowledged.
     */
    @Test
    void unconfirmedHoldsAreResentAfterEveryReconnect() throws Exception {
        long psp = newPsp(false);
        FakeEdge first = node("r9-rs-1", "r9-rs", psp);
        LegalHoldService holds = cpA.getBean(LegalHoldService.class);
        UUID held = UUID.fromString(uuid(4, 1));
        long sarId = idSeq.incrementAndGet();
        LegalHoldService.Coverage c = holds.ensureCoverage(psp, LegalHoldWorkflow.Type.SAR, sarId, "SAR",
                "SAR-R9-RESEND", List.of(held), "u-77");
        assertThat(c.placedHoldIds()).hasSize(1);
        UUID holdId = c.placedHoldIds().get(0);
        assertThat(siteAcks(psp)).singleElement().satisfies(a -> {
            assertThat(a.getSiteId()).isEqualTo("r9-rs");
            assertThat(a.getSendCount()).isZero();
        });

        HoldBook silent = new HoldBook();
        silent.ack = false;
        first.notifyHandler = silent;
        connect(first);
        await(() -> silent.seen.contains("hold.place:" + holdId), 5_000, "hold.place after hello.ack");
        first.close();

        FakeEdge second = first.twin();
        HoldBook book = new HoldBook();
        second.notifyHandler = book;
        connect(second);
        await(() -> book.active.contains(holdId.toString()), 5_000, "unacknowledged hold re-sent and applied");
        await(() -> siteAcks(psp).get(0).isConfirmed(), 5_000, "ack recorded");
        assertThat(siteAcks(psp).get(0).getSendCount()).isGreaterThanOrEqualTo(2);
        second.close();

        FakeEdge third = first.twin();
        HoldBook quiet = new HoldBook();
        third.notifyHandler = quiet;
        connect(third);
        Thread.sleep(1_500);
        assertThat(quiet.seen).as("a confirmed hold is not re-sent").isEmpty();

        quiet.active.add(holdId.toString());
        assertThat(holds.releaseWorkflow(LegalHoldWorkflow.Type.SAR, sarId, LegalHold.ReleaseReason.PLACED_IN_ERROR,
                "u-77")).isEqualTo(1);
        await(() -> quiet.seen.contains("hold.release:" + holdId), 5_000, "hold.release delivered");
        await(() -> siteAcks(psp).get(0).isConfirmed() && "RELEASED".equals(siteAcks(psp).get(0).getAckState()), 5_000,
                "release acknowledged");
        assertThat(holds.activelyHeldIds(psp)).isEmpty();
    }

    /**
     * Verifies: only the PSP's own MLRO/compliance officer files (operators, other PSPs and platform
     * staff are refused before any pull); a filed document is PSP-scoped and platform staff need
     * the PSP's support access and a reason to read its content.
     */
    @Test
    void filingAndDocumentAccessArePspScoped() throws Exception {
        long psp = newPsp(false);
        long other = newPsp(false);
        String id = uuid(5, 1);
        FakeEdge edge = connect(node("r9-ac-1", "r9-ac", psp));
        edge.notifyHandler = new HoldBook();
        edge.handler = extractor(Map.of(id, detail(id, "r9-ac", "r9-ac-1")), null);
        SuspiciousActivityReport sar = approvedSar(psp,
                List.of(TransactionReference.ofDecisionRecord(UUID.fromString(id), null, null)));

        HttpResponse<String> operator = call("POST", "/sar/" + sar.getId() + "/file", "op:" + psp, null);
        assertThat(operator.statusCode()).isEqualTo(403);
        assertThat(json(operator).get("code").asText()).isEqualTo("FILING_NOT_ALLOWED");
        HttpResponse<String> foreign = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + other, null);
        assertThat(foreign.statusCode()).isEqualTo(404);
        HttpResponse<String> platform = call("POST", "/sar/" + sar.getId() + "/file", "platform", null);
        assertThat(platform.statusCode()).isEqualTo(403);
        assertThat(edge.frames("request")).isEmpty();

        HttpResponse<String> filed = call("POST", "/sar/" + sar.getId() + "/file", "mlro:" + psp, null);
        assertThat(filed.statusCode()).isEqualTo(200);
        long docId = json(filed).get("document").get("id").asLong();

        assertThat(call("GET", "/filed-documents/" + docId + "/content", "mlro:" + other, null).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/filed-documents/" + docId, "mlro:" + other, null).statusCode()).isEqualTo(404);
        assertThat(json(call("GET", "/filed-documents", "mlro:" + other, null)).get("totalElements").asLong()).isZero();
        HttpResponse<String> noSupport = call("GET", "/filed-documents/" + docId
                + "/content?supportReason=Ticket%20HK-9001%20regulator%20query", "platform", null);
        assertThat(noSupport.statusCode()).isEqualTo(403);
        assertThat(json(noSupport).get("code").asText()).isEqualTo("SUPPORT_ACCESS_DISABLED");
        jdbc.update("UPDATE psps SET support_access_enabled = TRUE WHERE psp_id = ?", psp);
        assertThat(call("GET", "/filed-documents/" + docId + "/content", "platform", null).statusCode())
                .as("a reason is still required").isEqualTo(403);
        HttpResponse<String> support = call("GET", "/filed-documents/" + docId
                + "/content?supportReason=Ticket%20HK-9001%20regulator%20query", "platform", null);
        assertThat(support.statusCode()).isEqualTo(200);
        Mockito.verify(cpA.getBean(AuditLogService.class)).logAction(any(), eq("FILED_DOCUMENT_DOWNLOAD"),
                eq("REGULATORY_FILED_DOCUMENT"), eq(String.valueOf(docId)), any(), any(), any(),
                eq("Ticket HK-9001 regulator query"));
    }
}
