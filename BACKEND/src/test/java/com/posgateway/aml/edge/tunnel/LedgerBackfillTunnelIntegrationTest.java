package com.posgateway.aml.edge.tunnel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.backfill.BackfillCoverageService;
import com.posgateway.aml.service.backfill.BackfillEngine;
import com.posgateway.aml.service.backfill.BackfillProperties;
import com.posgateway.aml.service.backfill.BackfillReferenceUpdater;
import com.posgateway.aml.service.backfill.BackfillRun;
import com.posgateway.aml.service.backfill.BackfillRunStore;
import com.posgateway.aml.service.backfill.BackfillState;
import com.posgateway.aml.service.backfill.BackfillVerifier;
import com.posgateway.aml.service.backfill.EdgeLedgerSimulator;
import com.posgateway.aml.service.backfill.LedgerBackfillAccess;
import com.posgateway.aml.service.backfill.LedgerBackfillService;
import com.posgateway.aml.service.backfill.LegacyRowReader;
import com.posgateway.aml.service.backfill.TunnelBackfillTransport;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.security.PspIsolationService;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.flywaydb.core.Flyway;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * R15 end to end over the real tunnel: two Control Plane instances (embedded Tomcat, WebSocket, a
 * real Redis, a fresh PostgreSQL migrated V1..V270), a fake Edge Node connected to the <em>other</em>
 * instance (every request relayed sealed through Redis) whose ledger is {@link EdgeLedgerSimulator},
 * and the back-fill engine driving {@link TunnelBackfillTransport} through the R4 gateway.
 *
 * <p>Covers: BEGIN / BATCH / COMMIT / verification sample over sealed frames as the {@code SYSTEM}
 * actor; the site going offline mid-run and the run resuming when it reconnects; the
 * {@code edge_data_access_log} rows (one per request, params summary without row content); and the
 * §7.16 canary: a planted amount, card hash and feature value reach the Edge but never the logs,
 * the access log, Redis keys or relayed pub/sub messages.
 *
 * <p>Needs {@code -Dtunnel.it.redis.port} and {@code -Dschema.it.jdbc-url} (+ user / password).
 */
@EnabledIfSystemProperty(named = "tunnel.it.redis.port", matches = "\\d+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerBackfillTunnelIntegrationTest {

    private static final long CANARY_AMOUNT = 918_273_645L;
    private static final String CANARY_FEATURE = "canary-feature-r15-7731";
    private static final String CANARY_HASH = "canaryhashr15" + "f".repeat(40);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String adminUrl;
    private String dbUser;
    private String dbPassword;
    private String databaseName;
    private ConfigurableApplicationContext cpA;
    private ConfigurableApplicationContext cpB;
    private JdbcTemplate jdbc;
    private EdgeNodeRepository nodes;
    private String ingestPublicB64;
    private java.security.PublicKey cpSigningPublic;
    private RedisClient pubSubClient;
    private StatefulRedisPubSubConnection<String, String> pubSub;
    private final List<String> relayed = new CopyOnWriteArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<FakeEdge> edges = new CopyOnWriteArrayList<>();

    /** Control Plane with the tunnel server; JPA only for the edge entities (tables from Flyway). */
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
            EdgeTunnelAccess.class, TunnelBackfillTransport.class})
    static class BackfillItApp {
    }

    /** Fresh database, two instances, the Redis tap and the log capture. */
    @BeforeAll
    void start() throws Exception {
        adminUrl = System.getProperty("schema.it.jdbc-url");
        dbUser = System.getProperty("schema.it.user", "postgres");
        dbPassword = System.getProperty("schema.it.password", "");
        org.junit.jupiter.api.Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank(),
                "pass -Dschema.it.jdbc-url for the back-fill tunnel test");
        databaseName = "r15_tunnel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(adminUrl, dbUser, dbPassword); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + databaseName);
        }
        String url = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + databaseName;
        Flyway.configure().dataSource(url, dbUser, dbPassword).locations("classpath:db/migration")
                .placeholderReplacement(false).load().migrate();

        KeyPair sign = FakeEdge.gen("Ed25519");
        KeyPair ingest = FakeEdge.gen("X25519");
        String signPriv = Base64.getEncoder().encodeToString(sign.getPrivate().getEncoded());
        String signPub = Base64.getEncoder().encodeToString(EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) sign.getPublic()));
        String ingestPriv = Base64.getEncoder().encodeToString(ingest.getPrivate().getEncoded());
        String ingestPub = Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) ingest.getPublic()));
        String port = System.getProperty("tunnel.it.redis.port");
        cpA = boot("r15-cp-a", url, signPriv, signPub, ingestPriv, ingestPub, port);
        cpB = boot("r15-cp-b", url, signPriv, signPub, ingestPriv, ingestPub, port);

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs.start();
        root.addAppender(logs);
        ((Logger) LoggerFactory.getLogger("com.posgateway.aml")).setLevel(Level.TRACE);

        jdbc = cpA.getBean(JdbcTemplate.class);
        nodes = cpA.getBean(EdgeNodeRepository.class);
        ingestPublicB64 = cpA.getBean(EdgeControlPlaneKeys.class).ingestPublicKeyBase64();
        cpSigningPublic = cpA.getBean(EdgeControlPlaneKeys.class).signingPublicKey();
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
    }

    /** Boots one instance over the shared PostgreSQL and Redis. */
    private ConfigurableApplicationContext boot(String id, String url, String signPriv, String signPub, String ingestPriv,
                                                String ingestPub, String redisPort) {
        return new SpringApplicationBuilder(BackfillItApp.class)
                .web(WebApplicationType.SERVLET)
                .properties(Map.ofEntries(
                        Map.entry("spring.config.name", "r15-backfill-it-no-application-properties"),
                        Map.entry("server.port", "0"),
                        Map.entry("server.servlet.context-path", "/api/v1"),
                        Map.entry("spring.main.banner-mode", "off"),
                        Map.entry("spring.datasource.url", url),
                        Map.entry("spring.datasource.username", dbUser),
                        Map.entry("spring.datasource.password", dbPassword),
                        Map.entry("spring.datasource.hikari.maximum-pool-size", "4"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "none"),
                        // Only the edge entities are mapped here; the application's Envers revision
                        // entity is not, so auditing is off in this slice (the tables come from Flyway).
                        Map.entry("spring.jpa.properties.hibernate.integration.envers.enabled", "false"),
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
                        Map.entry("logging.level.com.posgateway.aml", "TRACE")))
                .run();
    }

    /** Closes the Edge, the tap, both instances, and drops the database. */
    @AfterAll
    void stop() throws Exception {
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
        if (databaseName != null) {
            try (Connection c = DriverManager.getConnection(adminUrl, dbUser, dbPassword); Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
            }
        }
    }

    @Test
    void backfillRunsOverTheSealedTunnelSurvivesAnOfflineSiteAndLeaksNothing() throws Exception {
        long psp = jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email, "
                + "support_access_enabled) VALUES ('R15T', 'R15 tunnel', 'KEN', 'it@example.com', true) RETURNING psp_id",
                Long.class);
        Set<String> ops = new java.util.HashSet<>(EdgeTunnelIntegrationTest.MutableTunnelSettings.READ_OPS);
        ops.add("ledger.backfill");
        EdgeTunnelIntegrationTest.SETTINGS.set(psp,
                new TunnelPspSettingsProvider.TunnelPspSettings(true, Set.copyOf(ops), true, 16, 3L));
        for (int i = 0; i < 5; i++) {
            long txn = jdbc.queryForObject("INSERT INTO transactions (psp_id, merchant_id, pan_hash, amount_cents, currency, "
                    + "client_reference, txn_ts, created_at, decision) VALUES (?, 'm-r15', ?, ?, 'KES', ?, ?, NOW(), 'ALERT') "
                    + "RETURNING txn_id", Long.class, psp, CANARY_HASH, CANARY_AMOUNT + i, "R15T-" + i,
                    LocalDateTime.now().minusDays(5 - i));
            jdbc.update("INSERT INTO transaction_features (txn_id, psp_id, feature_json, score) VALUES (?, ?, ?::jsonb, 0.5)",
                    txn, psp, "{\"memo\":\"" + CANARY_FEATURE + "\",\"cvv\":\"321\"}");
        }

        EdgeLedgerSimulator ledger = new EdgeLedgerSimulator(psp, "r15-site");
        FakeEdge edge = connected("r15-site-1", "r15-site", psp, ledger);

        BackfillProperties props = new BackfillProperties();
        props.setBatchRows(2);
        props.setInitialBackoffMs(10);
        props.setMaxBackoffMs(20);
        Harness h = harness(psp, props);
        h.asPlatform();
        BackfillRun run = h.service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-T tunnel run"))
                .get(0);
        h.drive(run, 2);
        assertThat(h.load(run).nextBatchSeq()).isEqualTo(1);

        // The site goes offline mid-run: retried with back-off, then resumed on reconnect.
        edge.close();
        awaitOffline(psp);
        h.engine.step(h.load(run));
        assertThat(h.load(run).lastErrorCode()).isEqualTo("EDGE_OFFLINE");
        assertThat(h.load(run).state()).isEqualTo(BackfillState.RUNNING);
        FakeEdge back = edge.twin();
        back.handler = handler(ledger);
        edges.add(back);
        back.start(URI.create("ws://127.0.0.1:" + port(cpB) + "/api/v1/edge/tunnel"), 1_000);
        h.drive(run, 30);
        BackfillRun done = h.load(run);
        assertThat(done.state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(done.sampleSize()).isEqualTo(5);
        assertThat(done.sampleMismatches()).isZero();
        assertThat(ledger.held(run.backfillId().toString())).hasSize(5);
        assertThat(h.coverage.requirePurgeAllowed(psp).allowed()).isTrue();

        // Requests went out sealed as system:backfill and carried the class-T data to the Edge only.
        List<TunnelFrameCodec.OpenedFrame> sent = new ArrayList<>(edge.requests("ledger.backfill"));
        sent.addAll(back.requests("ledger.backfill"));
        assertThat(sent).isNotEmpty();
        for (TunnelFrameCodec.OpenedFrame f : sent) {
            JsonNode actor = f.body().get("actor");
            assertThat(actor.get("kind").asText()).isEqualTo("SYSTEM");
            assertThat(actor.get("userId").asText()).isEqualTo("system:backfill");
            assertThat(actor.get("purpose").asText()).isEqualTo("LEDGER_BACKFILL");
        }
        String wire = sent.stream().map(f -> f.body().toString()).reduce("", String::concat);
        assertThat(wire).contains(String.valueOf(CANARY_AMOUNT)).contains(CANARY_FEATURE).contains(CANARY_HASH)
                .doesNotContain("\"cvv\"");

        // One access-log row per request, completed, with a summary and never the rows.
        List<Map<String, Object>> audit = jdbc.queryForList("SELECT * FROM edge_data_access_log WHERE psp_id = ? "
                + "ORDER BY requested_at", psp);
        assertThat(audit).isNotEmpty();
        assertThat(audit).allSatisfy(row -> {
            assertThat(row.get("actor_kind")).isEqualTo("SYSTEM");
            assertThat(row.get("actor_user_id")).isEqualTo("system:backfill");
            assertThat(row.get("support_access")).isEqualTo(false);
            assertThat(row.get("outcome")).isNotNull();
        });
        assertThat(audit).extracting(r -> r.get("op")).contains("ledger.backfill", "ledger.health", "txn.batchGet");
        assertThat(audit).extracting(r -> r.get("outcome")).contains("OK", "EDGE_OFFLINE");
        assertThat(audit.stream().filter(r -> "ledger.backfill".equals(r.get("op")))
                .map(r -> String.valueOf(r.get("params_summary"))).toList())
                .anySatisfy(s -> assertThat(s).contains("\"phase\":\"BATCH\"").contains("\"rowCount\":2"));

        // §7.16 canary: nowhere but on the Edge.
        for (Map<String, Object> row : audit) {
            assertThat(row.toString()).doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE)
                    .doesNotContain(CANARY_HASH);
        }
        for (String table : List.of("ledger_backfill_runs", "ledger_backfill_rows", "ledger_backfill_batches",
                "ledger_backfill_targets")) {
            assertThat(jdbc.queryForList("SELECT * FROM " + table).toString()).as(table)
                    .doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE).doesNotContain(CANARY_HASH);
        }
        assertThat(jdbc.queryForList("SELECT * FROM assessments WHERE psp_id = ?", psp).toString())
                .doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE).doesNotContain(CANARY_HASH);
        StringRedisTemplate redis = new StringRedisTemplate(
                cpA.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        Set<String> keys = redis.keys("*");
        for (String k : keys == null ? Set.<String>of() : keys) {
            String type = String.valueOf(redis.type(k));
            String value = "STRING".equalsIgnoreCase(type) ? redis.opsForValue().get(k)
                    : "HASH".equalsIgnoreCase(type) ? String.valueOf(redis.opsForHash().entries(k)) : "";
            assertThat(k + " " + value).doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE);
        }
        assertThat(relayed).isNotEmpty();
        for (String m : List.copyOf(relayed)) {
            assertThat(m).doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE)
                    .doesNotContain(CANARY_HASH);
        }
        for (ILoggingEvent e : List.copyOf(logs.list)) {
            String text = e.getFormattedMessage() + (e.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(e.getThrowableProxy()));
            assertThat(text).doesNotContain(String.valueOf(CANARY_AMOUNT)).doesNotContain(CANARY_FEATURE)
                    .doesNotContain(CANARY_HASH);
        }
    }

    // ── harness ──────────────────────────────────────────────────────────────────────────────────

    /** The back-fill components over cp-a's database and its real tunnel transport. */
    private final class Harness {
        final LedgerBackfillService service;
        final BackfillEngine engine;
        final BackfillRunStore store;
        final BackfillCoverageService coverage;
        final java.util.concurrent.atomic.AtomicReference<User> user = new java.util.concurrent.atomic.AtomicReference<>();

        /** Wires the components. */
        Harness(long psp, BackfillProperties props) {
            store = new BackfillRunStore(jdbc, MAPPER);
            LegacyRowReader reader = new LegacyRowReader(jdbc, MAPPER, props);
            coverage = new BackfillCoverageService(jdbc, props);
            PspEdgeSettings s = PspEdgeSettings.defaultsFor(psp);
            List<String> allowed = new ArrayList<>(PspEdgeSettings.DEFAULT_TUNNEL_OPS);
            allowed.add("ledger.backfill");
            s.setTunnelOpsAllowed(allowed);
            s.setTunnelBackfillAllowed(true);
            PspEdgeSettingsRepository settings = Mockito.mock(PspEdgeSettingsRepository.class);
            Mockito.when(settings.findById(psp)).thenReturn(Optional.of(s));
            PspRepository psps = Mockito.mock(PspRepository.class);
            Mockito.when(psps.findById(anyLong())).thenAnswer(i -> {
                Psp p = new Psp();
                p.setPspId(i.getArgument(0));
                p.setRetentionYears(5);
                return Optional.of(p);
            });
            Mockito.when(psps.existsById(anyLong())).thenReturn(true);
            PspIsolationService isolation = Mockito.mock(PspIsolationService.class);
            Mockito.when(isolation.getCurrentUser()).thenAnswer(i -> user.get());
            Mockito.when(isolation.isPlatformAdministrator(any())).thenAnswer(i -> ((User) i.getArgument(0)).getPsp() == null);
            AuditLogService audit = Mockito.mock(AuditLogService.class);
            LedgerBackfillAccess access = new LedgerBackfillAccess(isolation, cpA.getBean(EdgeTunnelAccess.class));
            PlatformTransactionManager tx = cpA.getBean(PlatformTransactionManager.class);
            service = new LedgerBackfillService(store, reader, coverage, access, psps, settings, nodes, audit, props, tx);
            engine = new BackfillEngine(store, reader, new BackfillReferenceUpdater(jdbc),
                    new BackfillVerifier(store, reader, props), cpA.getBean(TunnelBackfillTransport.class), settings, audit,
                    props, jdbc, tx, MAPPER);
        }

        /** Signs in as a platform operator. */
        void asPlatform() {
            Role role = new Role();
            role.setName("PLATFORM_ADMIN");
            user.set(User.builder().id(1L).username("ops").email("o@x").passwordHash("").role(role).build());
        }

        /** Steps a run until it is no longer driven (back-off ignored). */
        void drive(BackfillRun run, int max) {
            for (int i = 0; i < max; i++) {
                BackfillRun current = load(run);
                if (!BackfillState.DRIVEN.contains(current.state())) {
                    return;
                }
                engine.step(current);
            }
        }

        /** The run's current row. */
        BackfillRun load(BackfillRun run) {
            return store.find(run.backfillId()).orElseThrow();
        }
    }

    /** A harness for {@code psp}. */
    private Harness harness(long psp, BackfillProperties props) {
        return new Harness(psp, props);
    }

    /** The FakeEdge request handler answering from the simulated ledger. */
    private static FakeEdge.RequestHandler handler(EdgeLedgerSimulator ledger) {
        return (e, req) -> {
            JsonNode body = req.body();
            String op = body.get("op").asText();
            ObjectNode params = (ObjectNode) body.get("params");
            long psp = body.get("actor").get("pspId").asLong();
            try {
                switch (op) {
                    case "ledger.backfill" -> e.respond(req, List.of(ledger.backfill(psp, e.siteId, params)), null, null);
                    case "ledger.health" -> {
                        ObjectNode item = (ObjectNode) ContractFixtures.fixture("ledger.health.item--ok.json");
                        item.put("siteId", e.siteId);
                        item.put("nodeId", e.edgeId);
                        e.respond(req, List.of(item), null, null);
                    }
                    case "txn.batchGet" -> {
                        List<UUID> ids = new ArrayList<>();
                        params.get("edgeAssessmentIds").forEach(n -> ids.add(UUID.fromString(n.asText())));
                        var got = ledger.batchGet(psp, e.siteId, ids);
                        e.respond(req, got.items(), null, got.missing());
                    }
                    case "txn.get" -> {
                        var found = ledger.getByTxnRef(psp, e.siteId, params.get("txnRef").asText());
                        if (found.isPresent()) {
                            e.respond(req, List.of(found.get()), null, null);
                        } else {
                            e.error(req, TunnelError.of(TunnelErrorCode.NOT_FOUND, "absent"));
                        }
                    }
                    default -> e.error(req, TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED, "not served"));
                }
            } catch (com.posgateway.aml.edge.tunnel.EdgeDataException ex) {
                e.error(req, ex.error());
            }
        };
    }

    /** Registers an ACTIVE node pinned to a fake Edge's keys and connects it to cp-b. */
    private FakeEdge connected(String edgeId, String siteId, long psp, EdgeLedgerSimulator ledger) throws Exception {
        FakeEdge e = new FakeEdge(edgeId, siteId, psp, ingestPublicB64, cpSigningPublic);
        EdgeNode n = new EdgeNode();
        n.setPspId(psp);
        n.setEdgeId(edgeId);
        n.setSiteId(siteId);
        n.setDisplayName(edgeId);
        n.setStatus(EdgeNodeStatus.ACTIVE);
        n.setEdgeX25519PublicKey(e.x25519PublicB64());
        n.setEdgeEd25519PublicKey(e.ed25519PublicB64());
        n.setActivatedAt(Instant.parse("2026-10-01T08:00:00Z"));
        nodes.save(n);
        e.handler = handler(ledger);
        edges.add(e);
        return e.start(URI.create("ws://127.0.0.1:" + port(cpB) + "/api/v1/edge/tunnel"), 1_000);
    }

    /** Waits until the registry has no live session for the PSP. */
    private void awaitOffline(long psp) throws InterruptedException {
        StringRedisTemplate redis = new StringRedisTemplate(
                cpA.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && redis.opsForHash().size("tunnel:psp:" + psp) > 0) {
            Thread.sleep(50);
        }
        assertThat(redis.opsForHash().size("tunnel:psp:" + psp)).isZero();
    }

    /** The HTTP port of an instance. */
    private static int port(ConfigurableApplicationContext cp) {
        return ((ServletWebServerApplicationContext) cp).getWebServer().getPort();
    }
}
