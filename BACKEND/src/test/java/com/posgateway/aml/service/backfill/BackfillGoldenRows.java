package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixed legacy rows (PSP 12, the PSP of the contract fixtures and of the edge-host test rig) and the
 * shared contract fixtures the cloud mapper produces from them:
 * {@code docs/contracts/tunnel/examples/ledger.backfill.params--cloud-{begin,batch,commit}.json}.
 * The edge-host {@code CloudBackfillFixturesIntegrationTest} imports those fixtures with the real
 * {@code BackfillImporter} into a real ledger, so both runtimes agree on the row format, the
 * hygiene and the digest.
 */
final class BackfillGoldenRows {

    static final long PSP = 12;
    static final UUID BACKFILL_ID = UUID.fromString("b4c0ffee-0000-4000-8000-000000000015");
    static final UUID ASSESSMENT = UUID.fromString("3b0a8f7e-5c41-4d2e-9a7b-6f1e2d3c4b5a");
    static final String SOURCE_FROM = "2021-10-17T00:00:00.000Z";
    static final String SOURCE_TO = "2026-10-17T00:00:00.000Z";
    static final ObjectMapper MAPPER = new ObjectMapper();

    /** Not instantiable. */
    private BackfillGoldenRows() {
    }

    /** Rule names the golden rows' legacy engine reported, resolved like rule_definitions would be. */
    static LegacyRowMapper.RuleNameIndex rules() {
        return LegacyRowMapper.RuleNameIndex.of(Map.of("Card velocity above 10 per hour", 1204L));
    }

    /** The four legacy rows: two card rows, a crypto row and a mobile-money row. */
    static List<LegacyRow> rows() {
        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("is_cross_border", false);
        f1.put("device_id", "dv-77a1");
        f1.put("pan", "4111111111111111");
        f1.put("cvv", "123");
        f1.put("track2", "4111111111111111=2512");
        f1.put("expiry", "12/25");
        f1.put("card_number_echo", "4111 1111 1111 1111");
        f1.put("BadKey", "x");
        f1.put("nested", Map.of("a", 1));
        f1.put("long_note", "n".repeat(600));
        f1.put("txn_count_1h", 11);
        f1.put("mcc", "5411");
        f1.put("legacy_source", "cloud_ingest");
        LegacyRow card = new LegacyRow(LegacySource.TXN, 55120, PSP, ldt("2025-03-02T14:21:09.331412"),
                instant("2025-03-02T14:21:09.331Z"), null, null, null, null, List.of(), List.of(),
                "AUTH-2025-03-02-000017", "sha256:9c1185a5c5e9fc54612808977ee8f548b2258d31", "mrc_00421", 1_250_000L,
                null, "KES", "KEN", "pos", "REVIEW", 0.3, false,
                List.of("Card velocity above 10 per hour", "Legacy night-time rule"), List.of(), f1);

        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("ip_address", "10.1.2.3");
        f2.put("legacy_source", "cloud_ingest");
        LegacyRow assessed = new LegacyRow(LegacySource.TXN, 55121, PSP, ldt("2025-03-02T14:25:00.5"),
                instant("2025-03-02T14:25:00.500Z"), ASSESSMENT, null, "AUTH-2025-03-02-000018", 64, List.of(77L, 78L),
                List.of(9L), "4111-1111-1111-1111", "UNKNOWN", "mrc_00421", -500L, null, "KES", "KE", "ATM", "SUSPEND",
                0.9, true, List.of(), List.of(), f2);

        Map<String, Object> f3 = new LinkedHashMap<>();
        f3.put("asset_class", "CRYPTO");
        f3.put("asset_symbol", "BTC");
        f3.put("beneficiary_name", "Jane Beneficiary");
        f3.put("travel_rule_required", true);
        f3.put("travel_rule_transfer_status", "TRANSMITTED");
        f3.put("legacy_source", "multi_asset");
        LegacyRow crypto = new LegacyRow(LegacySource.MAT, 9001, PSP, ldt("2026-01-05T08:00:00"),
                instant("2026-01-05T08:00:00Z"), null, null, null, null, List.of(), List.of(), "VA-TX-9001", null, null,
                null, new BigDecimal("0.50000000"), "BTC", "KE", null, "BLOCK", 85.0, false, List.of(),
                List.of("MIXER_EXPOSURE", "TRAVEL_RULE_MISSING"), f3);

        Map<String, Object> f4 = new LinkedHashMap<>();
        f4.put("account_id", "wallet-254700000001");
        f4.put("mobile_money_event_type", "P2P_TRANSFER");
        f4.put("latitude", "-1.2921");
        f4.put("legacy_source", "multi_asset");
        LegacyRow mobile = new LegacyRow(LegacySource.MAT, 9002, PSP, ldt("2026-02-10T19:30:15.25"),
                instant("2026-02-10T19:30:15.250Z"), null, null, null, null, List.of(), List.of(), "MM-9002", null, null,
                null, new BigDecimal("150.25"), "KES", "KEN", null, "ALLOW", 0.0, false, List.of(), List.of(), f4);
        return List.of(card, assessed, crypto, mobile);
    }

    /** The BATCH params the cloud sends for {@link #rows()}. */
    static ObjectNode batchParams() {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("phase", "BATCH");
        p.put("backfillId", BACKFILL_ID.toString());
        p.put("batchSeq", 0);
        ArrayNode rows = p.putArray("rows");
        rows().forEach(r -> rows.add(LegacyRowMapper.toBackfillRow(r, rules())));
        return p;
    }

    /** The BEGIN params. */
    static ObjectNode beginParams() {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("phase", "BEGIN");
        p.put("backfillId", BACKFILL_ID.toString());
        p.put("sourceFrom", SOURCE_FROM);
        p.put("sourceTo", SOURCE_TO);
        p.put("expectedRows", rows().size());
        p.put("retentionCutoff", SOURCE_FROM);
        return p;
    }

    /** The COMMIT params (digest over the sorted, newline-joined legacy ids). */
    static ObjectNode commitParams(String digest) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("phase", "COMMIT");
        p.put("backfillId", BACKFILL_ID.toString());
        p.put("expectedRows", rows().size());
        p.put("expectedIdDigest", digest);
        return p;
    }

    /** {@code docs/contracts/tunnel/examples}. */
    static Path examples() {
        String override = System.getProperty("contracts.dir");
        Path base = override != null ? Paths.get(override) : Paths.get("..", "docs", "contracts");
        Path p = base.resolve("tunnel").resolve("examples");
        if (!Files.isDirectory(p)) {
            throw new IllegalStateException("contract examples not found: " + p.toAbsolutePath());
        }
        return p;
    }

    /** A UTC wall-clock time. */
    private static LocalDateTime ldt(String s) {
        return LocalDateTime.parse(s);
    }

    /** An instant. */
    private static Instant instant(String s) {
        return Instant.parse(s);
    }
}
