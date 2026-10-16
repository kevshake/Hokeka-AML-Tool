package com.hokeka.edge.residency;

import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import com.hokeka.edge.tunnel.TunnelRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legal holds against a real ledger (contract §7.10, §8.3): idempotent place/release, matched rows,
 * the {@code activeHoldDigest} rule (fixture-checked), the insert trigger for rows that arrive
 * later inside a merchant range, and that releasing one hold keeps rows another hold covers.
 */
class LegalHoldServiceIntegrationTest {

    private TunnelRig rig;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    @Test
    void placeIsIdempotentAndTheDigestMatchesTheFixture() throws Exception {
        // notify--hold-place.json / response--hold-ack.json: one active hold → its digest.
        UUID holdId = UUID.fromString(rig.fixture("notify--hold-place.json").get("holdId").asText());
        List<UUID> ids = Stream.of("3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44", "c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f",
                "5a7b9c1d-3e5f-4a6b-9c8d-7e6f5a4b3c2d").map(UUID::fromString).toList();
        for (UUID id : ids) {
            rig.row().id(id).insert();
        }
        LegalHoldService.Placement p = new LegalHoldService.Placement(holdId, Instant.parse("2026-10-16T10:30:00Z"),
                "u-4512", "SAR", "SAR-2026-0077", ids, null, null, null);
        LegalHoldService.Ack ack = rig.holds.place(p);
        assertEquals("APPLIED", ack.state());
        assertEquals(3, ack.matchedRows());
        assertEquals(rig.fixture("response--hold-ack.json").get("items").get(0).get("activeHoldDigest").asText(),
                ack.activeHoldDigest());
        assertEquals(ack, rig.holds.place(p), "a re-sent hold.place answers the same");
        for (UUID id : ids) {
            assertTrue(marked(id));
        }
    }

    @Test
    void merchantRangeHoldsCoverLaterRowsAndReleaseRespectsOtherHolds() throws Exception {
        Instant now = rig.clock.instant().truncatedTo(ChronoUnit.SECONDS);
        UUID before = rig.row().merchant("mrc_x").at(now.minusSeconds(60)).insert();
        UUID range = UUID.randomUUID();
        rig.holds.place(new LegalHoldService.Placement(range, now, "u-1", "NEW_FUTURE_REASON", "REQ-1", null, "mrc_x",
                now.minusSeconds(3600), now.plusSeconds(3600)));
        UUID later = rig.row().merchant("mrc_x").at(now.plusSeconds(60)).insert();
        UUID outside = rig.row().merchant("mrc_x").at(now.plusSeconds(7200)).insert();
        assertTrue(marked(before));
        assertTrue(marked(later), "rows inserted later inside the range are held too");
        assertFalse(marked(outside));

        UUID ids = UUID.randomUUID();
        rig.holds.place(new LegalHoldService.Placement(ids, now, "u-1", "CASE", "CASE-1", List.of(later), null, null,
                null));
        LegalHoldService.Ack released = rig.holds.release(range, now, "u-1", "CASE_CLOSED");
        assertEquals("RELEASED", released.state());
        assertEquals(2, released.matchedRows());
        assertFalse(marked(before));
        assertTrue(marked(later), "still covered by the second hold");
        assertEquals(Jcs.sha256Hex(ids.toString().getBytes(StandardCharsets.UTF_8)), released.activeHoldDigest());
        assertEquals("RELEASED", rig.holds.release(range, now, "u-1", "CASE_CLOSED").state(), "idempotent");
        assertEquals("UNKNOWN_HOLD", rig.holds.release(UUID.randomUUID(), now, "u-1", "CASE_CLOSED").state());

        LegalHoldService.Summary s = rig.holds.summary();
        assertEquals(1, s.active());
        assertEquals(1, s.heldRows());
    }

    private boolean marked(UUID id) throws Exception {
        try (Connection c = LedgerTestDatabase.connect(rig.schema);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT legal_hold FROM edge_evaluations WHERE edge_assessment_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
