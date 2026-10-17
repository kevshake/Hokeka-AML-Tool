package com.posgateway.aml.service.edge.typology;

import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.host.EdgeRuleInterpreter;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.edge.EdgeRuleCompiler;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every seeded SpEL typology rule, read from a database migrated with the real Flyway history,
 * either compiles to Edge IR v2 or is reported with a reason. Runs against
 * {@code -Dschema.it.jdbc-url} (the same database ProductionSchemaValidationIntegrationTest uses;
 * this test works in its own database {@code r10a_catalog} on that server) and is skipped without it.
 * Writes the inventory to {@code target/edge-typology-inventory.txt}.
 */
class EdgeTypologyCatalogIntegrationTest {

    /** Seeded rules that genuinely cannot run on the Edge (reason checked per rule). */
    private static final Set<String> NOT_ON_EDGE = Set.of(
            "R-17", "R-32", "R-128", "R-169",           // screening: R10b
            "R-45", "R-61", "R-118",                    // merchant legal-name master data
            "R-CB-1", "R-CB-2", "R-CB-3", "R-CB-4", "R-CB-5", // cloud dispute data
            "R-127", "R-173");                          // cloud merchant aggregates / scheme stage

    /** Opens the catalog database, creating it on first use. */
    private static Connection catalogDb(String url, String user, String password) throws Exception {
        try (Connection admin = DriverManager.getConnection(url, user, password);
             Statement st = admin.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'r10a_catalog'")) {
            if (!rs.next()) {
                st.execute("CREATE DATABASE r10a_catalog");
            }
        }
        return DriverManager.getConnection(catalogUrl(url), user, password);
    }

    /** The server URL with the database name replaced. */
    private static String catalogUrl(String url) {
        int q = url.indexOf('?');
        String base = q < 0 ? url : url.substring(0, q);
        String query = q < 0 ? "" : url.substring(q);
        return base.substring(0, base.lastIndexOf('/') + 1) + "r10a_catalog" + query;
    }

    /** Compiles the migrated seed catalog and checks every rule's outcome. */
    @Test
    void everySeededSpelRuleCompilesOrIsReportedWithAReason() throws Exception {
        String url = System.getProperty("schema.it.jdbc-url", "");
        Assumptions.assumeTrue(!url.isBlank(), "pass -Dschema.it.jdbc-url to run against a migrated database");
        String user = System.getProperty("schema.it.user", "postgres");
        String password = System.getProperty("schema.it.password", "");
        try (Connection ignored = catalogDb(url, user, password)) {
            Flyway.configure().dataSource(catalogUrl(url), user, password).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
        }

        List<RuleDefinition> seeds = new ArrayList<>();
        Map<Long, String> codes = new java.util.HashMap<>();
        try (Connection c = DriverManager.getConnection(catalogUrl(url), user, password);
             ResultSet rs = c.createStatement().executeQuery("""
                     SELECT id, name, rule_type, rule_expression, parameters, action_type, score_impact, priority,
                            external_code
                     FROM rule_definitions WHERE is_system_managed = TRUE ORDER BY id""")) {
            while (rs.next()) {
                RuleDefinition r = new RuleDefinition();
                r.setId(rs.getLong("id"));
                r.setName(rs.getString("name"));
                r.setRuleType(rs.getString("rule_type"));
                r.setRuleExpression(rs.getString("rule_expression"));
                r.setParameters(rs.getString("parameters"));
                r.setAction(rs.getString("action_type"));
                r.setScore(rs.getInt("score_impact"));
                r.setPriority(rs.getInt("priority"));
                r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                r.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                seeds.add(r);
                codes.put(r.getId(), rs.getString("external_code"));
            }
        }
        assertTrue(seeds.size() >= 60, "the seeded catalog has " + seeds.size() + " rules");
        assertTrue(seeds.stream().allMatch(r -> "SPEL".equals(r.getRuleType())), "every seeded rule is SpEL");

        EdgeReferenceLists lists = new EdgeReferenceLists() {
            /** Fixed sample lists; the edge-host typology tests rely on these values. */
            @Override
            public List<String> values(Long pspId, String list) {
                return switch (list) {
                    case HIGH_RISK_COUNTRIES -> List.of("IR", "KP", "MM");
                    case MERCHANTS_HIGH_RISK_COUNTRY -> List.of("m-hrc");
                    case "blacklist_pan" -> List.of("pan-blocked");
                    case "blacklist_device" -> List.of("dev-blocked");
                    case "blacklist_reference_keyword" -> List.of("casino");
                    default -> List.of();
                };
            }

            /** Fixed stamp. */
            @Override
            public long stamp(Long pspId) {
                return 0L;
            }
        };
        EdgeBundleService bundleService = new EdgeBundleService();
        EdgeRuleCompiler.CompilationResult result = new EdgeRuleCompiler(bundleService, lists).compile(seeds, 42L);

        Set<String> uncompiledCodes = new TreeSet<>();
        StringBuilder inventory = new StringBuilder("code\tresult\trule\treason\n");
        Map<Long, String> reasons = new java.util.HashMap<>();
        result.uncompiled().forEach(u -> reasons.put(u.id(), u.reason()));
        for (RuleDefinition r : seeds) {
            String code = codes.get(r.getId());
            String reason = reasons.get(r.getId());
            if (reason != null) {
                uncompiledCodes.add(code);
            }
            inventory.append(code).append('\t').append(reason == null ? "COMPILED" : "NOT_ON_EDGE").append('\t')
                    .append(r.getName()).append('\t').append(reason == null ? "" : reason).append('\n');
        }
        Files.writeString(Path.of("target", "edge-typology-inventory.txt"), inventory, StandardCharsets.UTF_8);
        System.out.println(inventory);

        assertEquals(seeds.size(), result.rules().size() + result.uncompiled().size(), "no rule silently dropped");
        assertEquals(new TreeSet<>(NOT_ON_EDGE), uncompiledCodes);
        for (String reason : reasons.values()) {
            assertFalse(reason.isBlank());
        }
        // The whole compiled catalog is a valid IR v2 bundle for the Edge interpreter.
        byte[] bundle = bundleService.buildBundleJson(result.version(), 42L, result.rules(),
                result.manifestHash(), result.lists(), result.uncompiled());
        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        interpreter.loadBundle(bundle);
        // Shared with edge-host (EdgeTypologyEvaluateTest): the compiled seed catalog, so the Edge
        // evaluate tests run the exact IR this compiler emits.
        Path vector = Path.of("..", "edge-engine", "test-vectors", "typology_catalog_bundle.json");
        if (Files.isDirectory(vector.getParent())) {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            Files.write(vector, m.writerWithDefaultPrettyPrinter().writeValueAsBytes(m.readTree(bundle)));
        }
        // E2's burst: 12 card transactions within a minute trip R-172 and R-30 (both ALERT).
        EdgeRuleInterpreter.Decision burst = interpreter.evaluate(Map.of("pan_txn_count_1m", 11L,
                "pan_txn_count_24h", 11L, "amount", 10.11, "txn_direction", "OUTBOUND"));
        Set<String> fired = new TreeSet<>();
        burst.triggeredRuleIds().forEach(id -> fired.add(codes.get(id)));
        assertTrue(fired.containsAll(Set.of("R-172", "R-30")), "burst fired " + fired);
        assertTrue(burst.action().ordinal() >= EdgeRuleInterpreter.Action.ALERT.ordinal());
    }
}
