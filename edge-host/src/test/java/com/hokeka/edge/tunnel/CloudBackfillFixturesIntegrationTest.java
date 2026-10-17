package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.Jcs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-check of the cloud R15 back-fill against the real Edge: the BEGIN / BATCH / COMMIT params the
 * Control Plane's {@code LegacyRowMapper} produces (shared fixtures
 * {@code ledger.backfill.params--cloud-*.json}, asserted byte-equal to the mapper output by the BACKEND
 * {@code LegacyRowMapperTest}) are imported by the real {@link BackfillImporter} into a real ledger.
 *
 * <p>Asserts that every cloud row is accepted (cloud hygiene leaves nothing the Edge would strip or
 * reject), a re-send is all duplicates, COMMIT verifies with the digest the cloud computed, and the
 * detail the Edge returns equals what the cloud sent on every field the cloud's verification sample
 * compares.
 */
class CloudBackfillFixturesIntegrationTest {

    private TunnelRig rig;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
        rig.acceptConfig(15, TunnelRig::allowAllOps);
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    @Test
    void cloudMapperBatchImportsVerifiesAndReadsBackUnchanged() throws Exception {
        ObjectNode begin = rig.fixture("ledger.backfill.params--cloud-begin.json");
        ObjectNode batch = rig.fixture("ledger.backfill.params--cloud-batch.json");
        ObjectNode commit = rig.fixture("ledger.backfill.params--cloud-commit.json");

        assertEquals("OPEN", backfill(begin).get("state").asText());
        ObjectNode ack = backfill(batch);
        int rows = batch.get("rows").size();
        assertEquals(rows, ack.get("accepted").asInt(), "every cloud row is accepted: " + ack);
        assertEquals(0, ack.get("rejected").size());
        assertFalse(ack.toString().contains("sha256:9c11"), "acks never echo row content");

        ObjectNode again = backfill(batch);
        assertEquals(0, again.get("accepted").asInt());
        assertEquals(rows, again.get("duplicates").asInt(), "a re-sent batch is all duplicates");

        ObjectNode verified = backfill(commit);
        assertEquals("VERIFIED", verified.get("state").asText(), "Edge digest = cloud digest: " + verified);
        assertEquals(commit.get("expectedIdDigest").asText(), verified.get("idDigest").asText());
        assertEquals(rows, verified.get("rowsTotal").asInt());

        ObjectNode ids = rig.mapper.createObjectNode();
        ArrayNode list = ids.putArray("edgeAssessmentIds");
        batch.get("rows").forEach(r -> list.add(r.get("edgeAssessmentId").asText()));
        List<ObjectNode> details = rig.run(rig.request("txn.batchGet", ids, null, null, 8000, actor())).items();
        assertEquals(rows, details.size());
        Map<String, JsonNode> byId = new HashMap<>();
        details.forEach(d -> byId.put(d.get("edgeAssessmentId").asText(), d));
        for (JsonNode sent : batch.get("rows")) {
            JsonNode edge = byId.get(sent.get("edgeAssessmentId").asText());
            for (String f : List.of("txnRef", "decision", "merchantId", "currency", "mcc", "countryCode", "channel")) {
                assertEquals(sent.path(f).asText(null), edge.path(f).asText(null), f);
            }
            for (String f : List.of("score", "amountMinor", "ctrRequired", "triggeredRuleIds", "ruleVersionIds",
                    "reasons", "features")) {
                JsonNode a = sent.get(f);
                JsonNode b = edge.get(f);
                assertEquals(a == null ? null : Jcs.canonicalize(a), b == null ? null : Jcs.canonicalize(b), f);
            }
            assertEquals(java.time.Instant.parse(sent.get("evaluatedAt").asText()),
                    java.time.Instant.parse(edge.get("evaluatedAt").asText()));
            assertEquals("BACKFILL", edge.get("origin").asText());
            assertEquals("cloud-legacy", edge.get("evaluator").asText());
            assertEquals(TunnelRig.SITE_ID, edge.get("siteId").asText());
            assertFalse(edge.has("panHash"), "tunnel items never carry the card hash");
            assertTrue(edge.get("features").size() <= 256);
        }
    }

    /** One ledger.backfill phase as the cloud's SYSTEM actor. */
    private ObjectNode backfill(ObjectNode params) throws TunnelException {
        return rig.run(rig.request("ledger.backfill", params, null, null, 60000, actor())).items().get(0);
    }

    /** The cloud back-fill actor ({@code system:backfill}, purpose LEDGER_BACKFILL). */
    private ObjectNode actor() {
        return rig.mapper.createObjectNode().put("kind", "SYSTEM").put("userId", "system:backfill").put("role", "SYSTEM")
                .put("pspId", TunnelRig.PSP_ID).put("purpose", "LEDGER_BACKFILL");
    }
}
