package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The field-by-field comparison of the verification sample and the retry back-off. */
class BackfillVerifierTest {

    /** The Edge detail of a sent row, as the edge-host returns it. */
    private static ObjectNode edgeCopy(ObjectNode sent) {
        ObjectNode d = sent.deepCopy();
        d.remove("legacyTransactionId");
        d.remove("panHash");
        d.put("siteId", "site-x");
        d.put("nodeId", "site-x-1");
        d.put("evaluatedAt", sent.get("evaluatedAt").asText().replace(".331Z", ".331000Z"));
        d.put("origin", "BACKFILL");
        d.put("evaluator", "cloud-legacy");
        d.put("bundleVersion", -1);
        d.put("legalHold", false);
        d.put("sanctionsHit", false);
        return d;
    }

    @Test
    void identicalRowsMatchAndEveryChangedFieldIsNamed() {
        ObjectNode sent = LegacyRowMapper.toBackfillRow(BackfillGoldenRows.rows().get(0), BackfillGoldenRows.rules());
        assertThat(BackfillVerifier.differences(sent, edgeCopy(sent), "site-x")).isEmpty();

        ObjectNode tampered = edgeCopy(sent);
        tampered.put("amountMinor", 1L);
        ((ObjectNode) tampered.get("features")).put("device_id", "other");
        tampered.put("origin", "LIVE");
        tampered.put("decision", "ALLOW");
        assertThat(BackfillVerifier.differences(sent, tampered, "site-x"))
                .containsExactlyInAnyOrder("amountMinor", "features", "origin", "decision");
        assertThat(BackfillVerifier.differences(sent, edgeCopy(sent), "site-y")).containsExactly("siteId");
        ObjectNode noRef = edgeCopy(sent);
        noRef.remove("txnRef");
        assertThat(BackfillVerifier.differences(sent, noRef, "site-x")).containsExactly("txnRef");
    }

    @Test
    void backOffDoublesUpToTheCap() {
        BackfillProperties p = new BackfillProperties();
        p.setInitialBackoffMs(1_000);
        p.setMaxBackoffMs(5_000);
        assertThat(p.backoffMs(1)).isEqualTo(1_000);
        assertThat(p.backoffMs(2)).isEqualTo(2_000);
        assertThat(p.backoffMs(3)).isEqualTo(4_000);
        assertThat(p.backoffMs(10)).isEqualTo(5_000);
        p.setBatchRows(9_999);
        assertThat(p.getBatchRows()).as("contract maximum").isEqualTo(500);
    }
}
