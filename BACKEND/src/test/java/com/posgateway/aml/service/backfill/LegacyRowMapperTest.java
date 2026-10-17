package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.Jcs;
import com.posgateway.aml.edge.tunnel.TunnelSchemas;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cloud → Edge row mapping of R15 (contract §11, §2.4): ids, stripping of card secrets, field
 * conversions, schema validity, and the shared golden fixtures the edge-host imports for real.
 */
class LegacyRowMapperTest {

    private static final TunnelSchemas SCHEMAS = new TunnelSchemas();

    @Test
    void mintsTheContractUuidV5AndPrefixesLegacyIds() {
        // Vector of the contract fixture request--ledger-backfill-batch.json (PSP 12, legacy txn-55120),
        // cross-checked with Python's uuid.uuid5.
        assertThat(LegacyBackfillIds.mint(12, "txn-55120"))
                .isEqualTo(UUID.fromString("4d298ed2-d12f-5908-8ed9-6bbc73cc7213"));
        assertThat(LegacyBackfillIds.mint(12, "55120"))
                .isEqualTo(UUID.fromString("477591ba-294e-5f85-829f-aa11acd00f6d"));
        assertThat(LegacyBackfillIds.cardLegacyId(7)).isEqualTo("txn-7");
        assertThat(LegacyBackfillIds.multiAssetLegacyId(7)).isEqualTo("mat-7");
        assertThat(LegacyBackfillIds.mint(12, "txn-1")).isNotEqualTo(LegacyBackfillIds.mint(13, "txn-1"));
    }

    @Test
    void carriesTheCloudAssessmentIdAndMintsOtherwise() {
        List<LegacyRow> rows = BackfillGoldenRows.rows();
        assertThat(rows.get(0).edgeAssessmentId()).isEqualTo(LegacyBackfillIds.mint(12, "txn-55120"));
        assertThat(rows.get(0).minted()).isTrue();
        assertThat(rows.get(1).edgeAssessmentId()).isEqualTo(BackfillGoldenRows.ASSESSMENT);
        assertThat(rows.get(1).minted()).isFalse();
        assertThat(rows.get(0).toString()).isEqualTo("LegacyRow[txn-55120]").doesNotContain("1250000");
    }

    @Test
    void stripsCardSecretsAndAppliesFeatureHygiene() {
        ObjectNode row = LegacyRowMapper.toBackfillRow(BackfillGoldenRows.rows().get(0), BackfillGoldenRows.rules());
        JsonNode f = row.get("features");
        assertThat(f.has("pan")).isFalse();
        assertThat(f.has("cvv")).isFalse();
        assertThat(f.has("track2")).isFalse();
        assertThat(f.has("expiry")).isFalse();
        assertThat(f.has("card_number_echo")).as("PAN-like value").isFalse();
        assertThat(f.has("BadKey")).isFalse();
        assertThat(f.has("nested")).isFalse();
        assertThat(f.has("long_note")).isFalse();
        assertThat(f.get("device_id").asText()).isEqualTo("dv-77a1");
        assertThat(f.get("txn_count_1h").asInt()).isEqualTo(11);
        assertThat(row.toString()).doesNotContain("4111111111111111").doesNotContain("4111 1111");
        assertThat(row.get("panHash").asText()).isEqualTo("sha256:9c1185a5c5e9fc54612808977ee8f548b2258d31");
    }

    @Test
    void mapsFieldsAndKeepsWhatTheContractFieldCannotHold() {
        List<LegacyRow> rows = BackfillGoldenRows.rows();
        ObjectNode card = LegacyRowMapper.toBackfillRow(rows.get(0), BackfillGoldenRows.rules());
        assertThat(card.get("decision").asText()).isEqualTo("ALERT");
        assertThat(card.get("score").asInt()).isEqualTo(30);
        assertThat(card.get("countryCode").asText()).isEqualTo("KE");
        assertThat(card.get("channel").asText()).isEqualTo("POS");
        assertThat(card.get("mcc").asText()).isEqualTo("5411");
        assertThat(card.get("amountMinor").asLong()).isEqualTo(1_250_000L);
        assertThat(card.get("evaluatedAt").asText()).isEqualTo("2025-03-02T14:21:09.331Z");
        assertThat(card.get("triggeredRuleIds").toString()).isEqualTo("[1204]");
        assertThat(card.get("reasons").toString()).isEqualTo("[\"Legacy rule Legacy night-time rule\"]");

        ObjectNode assessed = LegacyRowMapper.toBackfillRow(rows.get(1), BackfillGoldenRows.rules());
        assertThat(assessed.has("panHash")).as("UNKNOWN placeholder").isFalse();
        assertThat(assessed.get("txnRef").asText()).isEqualTo("AUTH-2025-03-02-000018");
        assertThat(assessed.get("decision").asText()).isEqualTo("HOLD");
        assertThat(assessed.get("score").asInt()).as("assessment v2 score wins").isEqualTo(64);
        assertThat(assessed.get("triggeredRuleIds").toString()).isEqualTo("[77,78]");
        assertThat(assessed.get("ruleVersionIds").toString()).isEqualTo("[9]");
        assertThat(assessed.has("amountMinor")).isFalse();
        assertThat(assessed.get("features").get("amount_minor_signed").asText()).isEqualTo("-500");

        ObjectNode crypto = LegacyRowMapper.toBackfillRow(rows.get(2), BackfillGoldenRows.rules());
        assertThat(crypto.has("amountMinor")).isFalse();
        assertThat(crypto.has("currency")).isFalse();
        assertThat(crypto.get("features").get("amount_decimal").asText()).isEqualTo("0.5");
        assertThat(crypto.get("features").get("amount_currency").asText()).isEqualTo("BTC");
        assertThat(crypto.get("score").asInt()).isEqualTo(85);
        assertThat(crypto.get("reasons").toString())
                .isEqualTo("[\"Legacy risk signal MIXER_EXPOSURE\",\"Legacy risk signal TRAVEL_RULE_MISSING\"]");

        ObjectNode mobile = LegacyRowMapper.toBackfillRow(rows.get(3), BackfillGoldenRows.rules());
        assertThat(mobile.get("amountMinor").asLong()).isEqualTo(15_025L);
        assertThat(mobile.get("currency").asText()).isEqualTo("KES");
        assertThat(mobile.get("countryCode").asText()).isEqualTo("KE");
        assertThat(mobile.get("txnRef").asText()).isEqualTo("MM-9002");
    }

    @Test
    void amountsOutsideTheCurrencyScaleTravelAsDecimals() {
        LegacyRow base = BackfillGoldenRows.rows().get(3);
        LegacyRow usd3 = new LegacyRow(base.source(), base.sourceId(), base.pspId(), base.sortTs(), base.evaluatedAt(),
                null, null, null, null, List.of(), List.of(), null, null, null, null, new java.math.BigDecimal("1.005"),
                "USD", null, null, null, null, false, List.of(), List.of(), Map.of());
        ObjectNode n = LegacyRowMapper.toBackfillRow(usd3, LegacyRowMapper.RuleNameIndex.NONE);
        assertThat(n.has("amountMinor")).isFalse();
        assertThat(n.get("features").get("amount_decimal").asText()).isEqualTo("1.005");
        assertThat(n.get("decision").asText()).isEqualTo("ALLOW");
        assertThat(n.get("score").asInt()).isZero();
        LegacyRow jpy = new LegacyRow(base.source(), base.sourceId(), base.pspId(), base.sortTs(), base.evaluatedAt(),
                null, null, null, null, List.of(), List.of(), null, null, null, null, new java.math.BigDecimal("1200"),
                "JPY", null, null, null, null, false, List.of(), List.of(), Map.of());
        assertThat(LegacyRowMapper.toBackfillRow(jpy, LegacyRowMapper.RuleNameIndex.NONE).get("amountMinor").asLong())
                .isEqualTo(1200L);
    }

    @Test
    void everyMappedBatchValidatesAgainstTheContractSchema() {
        ObjectNode batch = BackfillGoldenRows.batchParams();
        assertThat(SCHEMAS.validate("tunnel/ops/ledger.backfill.params.schema.json", batch)).isEmpty();
        assertThat(SCHEMAS.validate("tunnel/ops/ledger.backfill.params.schema.json", BackfillGoldenRows.beginParams()))
                .isEmpty();
    }

    @Test
    void sharedGoldenFixturesMatchTheMapper() throws Exception {
        ObjectNode batch = BackfillGoldenRows.batchParams();
        TreeSet<String> ids = new TreeSet<>();
        batch.get("rows").forEach(r -> ids.add(r.get("legacyTransactionId").asText()));
        String digest = Jcs.sha256Hex(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
        Map<String, JsonNode> expected = Map.of(
                "ledger.backfill.params--cloud-begin.json", BackfillGoldenRows.beginParams(),
                "ledger.backfill.params--cloud-batch.json", batch,
                "ledger.backfill.params--cloud-commit.json", BackfillGoldenRows.commitParams(digest));
        Path dir = BackfillGoldenRows.examples();
        for (Map.Entry<String, JsonNode> e : expected.entrySet()) {
            Path file = dir.resolve(e.getKey());
            if (Boolean.getBoolean("backfill.golden.write")) {
                Files.writeString(file, BackfillGoldenRows.MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(e.getValue()) + "\n", StandardCharsets.UTF_8);
            }
            JsonNode onDisk = BackfillGoldenRows.MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            assertThat(Jcs.canonicalize(onDisk)).as(e.getKey()).isEqualTo(Jcs.canonicalize(e.getValue()));
        }
    }
}
