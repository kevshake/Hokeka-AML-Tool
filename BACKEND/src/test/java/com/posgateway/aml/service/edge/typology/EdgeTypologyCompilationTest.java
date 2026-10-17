package com.posgateway.aml.service.edge.typology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.host.EdgeRuleInterpreter;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.edge.EdgeRuleCompiler;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SpEL typology catalog compiled to Edge IR v2 and evaluated by the reference interpreter:
 * each case uses the expression and parameters exactly as the Flyway seeds leave them (V142–V249).
 */
class EdgeTypologyCompilationTest {

    private final EdgeBundleService bundleService = new EdgeBundleService();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Fixed reference lists standing in for the cloud tables. */
    private final EdgeReferenceLists lists = new EdgeReferenceLists() {
        /** Sample values per list. */
        @Override
        public List<String> values(Long pspId, String list) {
            return switch (list) {
                case HIGH_RISK_COUNTRIES -> List.of("IR", "KP");
                case MERCHANTS_HIGH_RISK_COUNTRY -> List.of("77");
                case "blacklist_pan" -> List.of("pan-bad");
                case "blacklist_device" -> List.of("dev-bad");
                case "blacklist_wallet" -> List.of("wal-bad");
                case "blacklist_payment_details" -> List.of();
                case "blacklist_card_country" -> List.of("RU");
                case "blacklist_reference_keyword" -> List.of("casino", "lottery");
                default -> throw new UncompilableRuleException("unknown list " + list);
            };
        }

        /** Fixed stamp. */
        @Override
        public long stamp(Long pspId) {
            return 1_800_000_000L;
        }
    };

    private final EdgeRuleCompiler compiler = new EdgeRuleCompiler(bundleService, lists);

    /** A catalog SpEL rule as seeded. */
    private static RuleDefinition spel(long id, String code, String action, String expression, String params) {
        RuleDefinition r = new RuleDefinition();
        r.setId(id);
        r.setName(code);
        r.setRuleType("SPEL");
        r.setRuleExpression(expression);
        r.setParameters(params);
        r.setAction(action);
        r.setScore(10);
        r.setPriority(1);
        r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        r.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return r;
    }

    /** Compiles one rule and loads it into a fresh interpreter. */
    private EdgeRuleInterpreter load(RuleDefinition rule) {
        EdgeRuleCompiler.CompilationResult result = compiler.compile(List.of(rule), 42L);
        assertEquals(1, result.rules().size(), "not compiled: " + result.uncompiled());
        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        interpreter.loadBundle(bundleService.buildBundleJson(result.version(), 42L, result.rules(),
                result.manifestHash(), result.lists(), result.uncompiled()));
        return interpreter;
    }

    /** True when the single compiled rule fires on these features. */
    private static boolean fires(EdgeRuleInterpreter interpreter, Object... kv) {
        Map<String, Object> f = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            f.put((String) kv[i], kv[i + 1]);
        }
        return !interpreter.evaluate(f).triggeredRuleIds().isEmpty();
    }

    /** R-7: structuring band [0.9 T, T) with 24h repeat count and normalised direction. */
    @Test
    void structuringInboundCompilesWithFoldedParameters() {
        EdgeRuleInterpreter r7 = load(spel(7, "R-7", "ALERT",
                "#tx.amount.doubleValue() >= ((#params['threshold_amount'] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params['threshold_amount'] ?: 9000) && #tx.panTxnCount24h >= (#params['min_transactions'] ?: 5) && #features['direction'] == 'INBOUND'",
                "{\"min_transactions\": 5, \"threshold_amount\": 9000, \"lookback_days\": 1}"));
        assertTrue(fires(r7, "amount", 8100.0, "pan_txn_count_24h", 5, "txn_direction", "INBOUND"));
        assertFalse(fires(r7, "amount", 8099.99, "pan_txn_count_24h", 5, "txn_direction", "INBOUND"));
        assertFalse(fires(r7, "amount", 9000.0, "pan_txn_count_24h", 5, "txn_direction", "INBOUND"));
        assertFalse(fires(r7, "amount", 8500.0, "pan_txn_count_24h", 4, "txn_direction", "INBOUND"));
        assertFalse(fires(r7, "amount", 8500.0, "pan_txn_count_24h", 9, "txn_direction", "OUTBOUND"));
    }

    /** R-30 / R-172: the E2 burst rules. */
    @Test
    void velocityRulesCompile() {
        EdgeRuleInterpreter r30 = load(spel(30, "R-30", "ALERT",
                "#tx.panTxnCount24h >= (#params['max_transactions'] ?: 10)",
                "{\"max_transactions\": 10, \"time_window_minutes\": 1440}"));
        assertTrue(fires(r30, "pan_txn_count_24h", 10));
        assertFalse(fires(r30, "pan_txn_count_24h", 9));
        EdgeRuleInterpreter r172 = load(spel(172, "R-172", "ALERT",
                "#features['pan_txn_count_1m'] != null && #features['pan_txn_count_1m'] >= (#params['max_txn'] ?: 8)",
                "{\"max_txn\": 8}"));
        assertTrue(fires(r172, "pan_txn_count_1m", 8));
        assertFalse(fires(r172, "pan_txn_count_1m", 7));
        assertFalse(fires(r172));
    }

    /** R-171 z-score. */
    @Test
    void zscoreRuleCompiles() {
        EdgeRuleInterpreter r171 = load(spel(171, "R-171", "HOLD",
                "#features['zscore_amount_vs_pan_history'] != null && #features['zscore_amount_vs_pan_history'] >= (#params['z'] ?: 3)",
                "{\"z\": 3}"));
        assertTrue(fires(r171, "zscore_amount_vs_pan_history", 3.2));
        assertFalse(fires(r171, "zscore_amount_vs_pan_history", 2.9));
    }

    /** R-14 / R-77: high-risk country via the bundle list. */
    @Test
    void highRiskCountryCompilesToInList() {
        RuleDefinition rule = spel(77, "R-77", "ALERT",
                "#tx.isHighRiskCountry() && #tx.panTxnCount24h >= (#params['min_transactions'] ?: 5)",
                "{\"min_transactions\": 5}");
        EdgeRuleCompiler.CompilationResult result = compiler.compile(List.of(rule), 42L);
        assertEquals(List.of("IR", "KP"), result.lists().get(EdgeReferenceLists.HIGH_RISK_COUNTRIES));
        EdgeRuleInterpreter r77 = load(rule);
        assertTrue(fires(r77, "country_code", "ir", "pan_txn_count_24h", 5));
        assertFalse(fires(r77, "country_code", "KE", "pan_txn_count_24h", 50));
    }

    /** R-6 with its seeded empty list is inert (const false), as in the cloud. */
    @Test
    void emptyCurrencyListCompilesToFalse() throws Exception {
        RuleDefinition rule = spel(6, "R-6", "ALERT",
                "#params['high_risk_currencies'] != null && #params['high_risk_currencies'].contains(#tx.currency)",
                "{\"high_risk_currencies\": []}");
        EdgeRuleCompiler.CompilationResult result = compiler.compile(List.of(rule), 42L);
        assertEquals("const", result.rules().get(0).condition().get("type").asText());
        assertFalse(result.rules().get(0).condition().get("value").asBoolean());
        RuleDefinition configured = spel(6, "R-6", "ALERT", rule.getRuleExpression(),
                "{\"high_risk_currencies\": [\"IRR\", \"KPW\"]}");
        EdgeRuleInterpreter r6 = load(configured);
        assertTrue(fires(r6, "currency", "KPW"));
        assertFalse(fires(r6, "currency", "KES"));
    }

    /** R-132 / R-24 blacklists. */
    @Test
    void blacklistRulesCompile() {
        EdgeRuleInterpreter r132 = load(spel(132, "R-132", "BLOCK", "#features['variable_blacklist_hit'] == true", "{}"));
        assertTrue(fires(r132, "pan_hash", "PAN-BAD"));
        assertTrue(fires(r132, "device_key", "dev-bad"));
        assertFalse(fires(r132, "pan_hash", "pan-ok"));
        EdgeRuleInterpreter r24 = load(spel(24, "R-24", "ALERT", "#features['reference_keyword_blacklist'] == true", "{}"));
        assertTrue(fires(r24, "reference_lc", "deposit for casino night"));
        // "lottery" is blacklisted but is not one of the keywords the cloud scans for.
        assertFalse(fires(r24, "reference_lc", "lottery win"));
    }

    /** SpEL null semantics: null < x and null != x are true in SpEL, so they fire on a missing feature. */
    @Test
    void comparisonsKeepSpelNullSemantics() {
        EdgeRuleInterpreter lt = load(spel(1, "lt", "ALERT", "#features['days_since_last_activity'] < 3", "{}"));
        assertTrue(fires(lt), "null < 3 is true in SpEL");
        assertTrue(fires(lt, "days_since_last_activity", 2));
        assertFalse(fires(lt, "days_since_last_activity", 5));
        EdgeRuleInterpreter ne = load(spel(2, "ne", "ALERT", "#features['direction'] != 'INBOUND'", "{}"));
        assertTrue(fires(ne));
        assertFalse(fires(ne, "txn_direction", "INBOUND"));
        EdgeRuleInterpreter gt = load(spel(3, "gt", "ALERT", "#features['unique_senders'] > 3", "{}"));
        assertFalse(fires(gt));
    }

    /** Rules the Edge cannot run are reported with a reason, never emitted. */
    @Test
    void inputsTheEdgeLacksAreReportedWithReasons() {
        List<RuleDefinition> rules = List.of(
                spel(17, "R-17", "HOLD", "#features['sanctions_hit'] == true || #features['pep_hit'] == true", "{}"),
                spel(45, "R-45", "ALERT", "#features['distinct_account_holder_names'] > 1", "{}"),
                spel(91, "R-CB-1", "ALERT", "#features['merchant_chargeback_ratio'] > 0.01", "{}"),
                spel(92, "ml", "ALERT", "#tx.mlScore > 0.9", "{}"),
                spel(93, "typeref", "ALERT", "T(java.lang.Math).abs(#tx.amount) > 5", "{}"),
                spel(30, "R-30", "ALERT", "#tx.panTxnCount24h >= 10", "{}"));
        EdgeRuleCompiler.CompilationResult result = compiler.compile(rules, 42L);
        assertEquals(1, result.rules().size());
        assertEquals(5, result.uncompiled().size());
        assertTrue(result.uncompiled().get(0).reason().contains("R10b"), result.uncompiled().get(0).reason());
        assertTrue(result.uncompiled().get(1).reason().contains("legal-name"));
        assertTrue(result.uncompiled().get(2).reason().contains("dispute"));
        assertTrue(result.uncompiled().get(3).reason().contains("ML"));
        assertTrue(result.uncompiled().get(4).reason().contains("type references"));
    }

    /** The bundle JSON carries ir_version, lists and the uncompiled manifest. */
    @Test
    void bundleCarriesListsAndUncompiledManifest() throws Exception {
        EdgeRuleCompiler.CompilationResult result = compiler.compile(List.of(
                spel(14, "R-14", "ALERT", "#tx.isHighRiskCountry()", "{}"),
                spel(17, "R-17", "HOLD", "#features['sanctions_hit'] == true", "{}")), 42L);
        JsonNode bundle = mapper.readTree(bundleService.buildBundleJson(result.version(), 42L, result.rules(),
                result.manifestHash(), result.lists(), result.uncompiled()));
        assertEquals(2, bundle.get("ir_version").asInt());
        assertEquals("IR", bundle.get("lists").get("high_risk_countries").get(0).asText());
        assertEquals(17, bundle.get("uncompiled").get(0).get("id").asLong());
        assertTrue(bundle.get("uncompiled").get(0).get("reason").asText().length() > 10);
    }

    /** Without a reference-list source, list rules are reported, not emitted with empty lists. */
    @Test
    void missingListSourceMakesListRulesUncompiled() {
        EdgeRuleCompiler bare = new EdgeRuleCompiler(bundleService);
        EdgeRuleCompiler.CompilationResult result = bare.compile(
                List.of(spel(14, "R-14", "ALERT", "#tx.isHighRiskCountry()", "{}")), 42L);
        assertTrue(result.rules().isEmpty());
        assertTrue(result.uncompiled().get(0).reason().contains("unavailable"));
    }

    /** A list change advances the version through the reference stamp. */
    @Test
    void listStampParticipatesInTheVersion() {
        long version = compiler.compile(List.of(spel(14, "R-14", "ALERT", "#tx.isHighRiskCountry()", "{}")), 42L)
                .version();
        assertEquals(1_800_000_000L * 1000 + 1, version);
    }

    /** Parser rejects constructs outside the subset with a position, not the expression text. */
    @Test
    void parserRejectsUnsupportedConstructs() {
        for (String bad : List.of("new java.io.File('x')", "@bean.x", "#tx.amount > 5 ? true : false",
                "#features['a'] matches 'x'", "#features.?[x > 1]", "#tx.amount >", "'open")) {
            UncompilableRuleException e = assertThrows(UncompilableRuleException.class,
                    () -> SpelSubsetParser.parse(bad), bad);
            assertTrue(e.getMessage().startsWith("SpEL"), e.getMessage());
        }
    }
}
