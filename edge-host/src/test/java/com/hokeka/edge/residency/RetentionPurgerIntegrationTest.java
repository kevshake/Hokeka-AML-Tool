package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import com.hokeka.edge.tunnel.TunnelErrorCode;
import com.hokeka.edge.tunnel.TunnelException;
import com.hokeka.edge.tunnel.TunnelRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daily retention purge against a real ledger (contract §8.2, §8.3): no purge before config,
 * whole-partition drops past the cutoff, held rows copied out first and still readable, txn refs,
 * the purge log, single-node advisory lock, the daily slot, the retention floor, and deletion of
 * released held copies at the next run.
 */
class RetentionPurgerIntegrationTest {

    /** "Now" for every test: 2026-10-17 03:00 UTC (after the 02:00 slot). */
    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    private TunnelRig rig;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
        rig.clock.set(NOW);
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    private Instant at(int year, int month, int day) {
        return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
    }

    @Test
    void nothingIsPurgedBeforeTheFirstConfigDocument() throws Exception {
        UUID old = rig.row().at(at(2015, 3, 1)).insert();
        RetentionPurger.Report r = rig.purger.purgeNow();
        assertEquals("SKIPPED_NO_CONFIG", r.outcome());
        assertTrue(r.droppedPartitions().isEmpty());
        assertTrue(exists(old));
        assertEquals(List.of("SKIPPED_NO_CONFIG"), purgeOutcomes());
    }

    @Test
    void dropsWholePartitionsPastTheCutoffAndKeepsHeldRowsReadable() throws Exception {
        rig.acceptConfig(14, null);
        UUID heldById = rig.row().at(at(2021, 3, 5)).ref("OLD-HELD").insert();
        UUID heldByRange = rig.row().at(at(2021, 3, 20)).merchant("mrc_held").insert();
        UUID purged = rig.row().at(at(2021, 3, 21)).ref("OLD-GONE").insert();
        UUID september = rig.row().at(at(2021, 9, 10)).insert();
        UUID insideMonthOfCutoff = rig.row().at(at(2021, 10, 5)).ref("KEEP-OCT").insert();
        UUID recent = rig.row().at(at(2026, 10, 1)).insert();

        rig.holds.place(new LegalHoldService.Placement(UUID.randomUUID(), NOW, "u-1", "SAR", "SAR-1",
                List.of(heldById), null, null, null));
        rig.holds.place(new LegalHoldService.Placement(UUID.randomUUID(), NOW, "u-1", "LITIGATION", "LIT-1", null,
                "mrc_held", at(2021, 1, 1), at(2021, 4, 1)));

        RetentionPurger.Report r = rig.purger.purgeNow();
        assertEquals("OK", r.outcome());
        assertEquals(5, r.retentionYears());
        assertEquals(Instant.parse("2021-10-17T03:00:00Z"), r.cutoff());
        // 2021-10 ends after the cutoff, so it stays; 2021-03 and 2021-09 are dropped whole.
        assertTrue(r.droppedPartitions().containsAll(List.of("edge_evaluations_2021_03", "edge_evaluations_2021_09")));
        assertFalse(r.droppedPartitions().contains("edge_evaluations_2021_10"));
        assertEquals(2, r.heldRowsCopied());

        assertFalse(exists(purged));
        assertFalse(exists(september));
        assertTrue(exists(insideMonthOfCutoff), "data lives up to one month past retention, never less");
        assertTrue(exists(recent));
        assertTrue(held(heldById));
        assertTrue(held(heldByRange));

        // Held rows remain readable by every op, flagged legalHold; their txn ref still resolves.
        ObjectNode byRef = rig.mapper.createObjectNode().put("txnRef", "OLD-HELD");
        ObjectNode item = rig.run(rig.request("txn.get", byRef, null, null, 5000, rig.actor())).items().get(0);
        assertEquals(heldById.toString(), item.get("edgeAssessmentId").asText());
        assertTrue(item.get("legalHold").asBoolean());
        ObjectNode goneRef = rig.mapper.createObjectNode().put("txnRef", "OLD-GONE");
        assertEquals(TunnelErrorCode.NOT_FOUND, assertThrows(TunnelException.class,
                () -> rig.run(rig.request("txn.get", goneRef, null, null, 5000, rig.actor()))).code());

        try (Connection c = LedgerTestDatabase.connect(rig.schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT cutoff, retention_years, config_version, cardinality(dropped_partitions), held_rows_copied,
                            outcome, node_id FROM edge_purge_log ORDER BY run_id DESC LIMIT 1
                     """)) {
            assertTrue(rs.next());
            assertEquals(Instant.parse("2021-10-17T03:00:00Z"), rs.getObject(1, java.time.OffsetDateTime.class).toInstant());
            assertEquals(5, rs.getInt(2));
            assertEquals(14, rs.getLong(3));
            assertEquals(r.droppedPartitions().size(), rs.getInt(4));
            assertEquals(2, rs.getLong(5));
            assertEquals("OK", rs.getString(6));
            assertEquals(TunnelRig.EDGE_ID, rs.getString(7));
        }
    }

    @Test
    void releasedHeldRowsAreDeletedAtTheNextRun() throws Exception {
        rig.acceptConfig(14, null);
        UUID id = rig.row().at(at(2020, 6, 1)).ref("HELD-REL").insert();
        UUID holdId = UUID.randomUUID();
        rig.holds.place(new LegalHoldService.Placement(holdId, NOW, "u-1", "CASE", "CASE-1", List.of(id), null, null,
                null));
        rig.purger.purgeNow();
        assertTrue(held(id));

        rig.holds.release(holdId, NOW, "u-1", "CASE_CLOSED");
        rig.clock.set(NOW.plusSeconds(86_400));
        RetentionPurger.Report next = rig.purger.purgeNow();
        assertEquals(1, next.heldRowsDeleted());
        assertFalse(held(id));
        try (Connection c = LedgerTestDatabase.connect(rig.schema);
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM edge_txn_refs WHERE txn_ref = 'HELD-REL'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    void longerRetentionKeepsMoreAndALowerFloorIsIgnored() throws Exception {
        rig.acceptConfig(15, d -> ((ObjectNode) d.get("retention")).put("years", 10));
        UUID eightYears = rig.row().at(at(2018, 6, 1)).insert();
        RetentionPurger.Report r = rig.purger.purgeNow();
        assertEquals(10, r.retentionYears());
        assertTrue(exists(eightYears));

        rig.ledgerProperties.setRetentionMinYears(3);
        assertEquals(OptionalInt.of(5), RetentionPolicy.effectiveYears(Optional.of(
                EdgeConfigDocument.parse(rig.fixture("edge-config--default.json"), "x")), 3));
        rig.ledgerProperties.setRetentionMinYears(12);
        assertEquals(OptionalInt.of(12), RetentionPolicy.effectiveYears(rig.config.current(), 12),
                "the local floor can only raise retention");
    }

    /**
     * Another node of the site holding the purge lock makes this node skip; once that node releases
     * it (as {@code RetentionPurger} does, explicitly, before returning its connection) this node
     * purges, and only once per daily slot.
     *
     * <p>The other node unlocks explicitly rather than by closing its connection: a closed JDBC
     * connection returns before the server backend has exited, and a backend releases its session
     * locks only on exit, so an immediate try-lock after a close can still be refused.
     */
    @Test
    void onlyOneNodePurgesAndOnlyOncePerDay() throws Exception {
        rig.acceptConfig(14, null);
        try (Connection other = LedgerTestDatabase.connect(rig.schema)) {
            advisory(other, "pg_advisory_lock");
            rig.purger.runIfDue();
            assertTrue(purgeOutcomes().isEmpty(), "another node holds the purge lock");
            assertTrue(advisory(other, "pg_advisory_unlock"), "the other node held the schema's purge lock");
        }
        rig.purger.runIfDue();
        assertEquals(List.of("OK"), purgeOutcomes());
        rig.purger.runIfDue();
        assertEquals(List.of("OK"), purgeOutcomes(), "not due again the same day");
        assertEquals(Instant.parse("2026-10-18T02:00:00Z"), rig.purger.nextPurgeAt(NOW));
        rig.clock.set(Instant.parse("2026-10-18T02:00:01Z"));
        rig.purger.runIfDue();
        assertEquals(List.of("OK", "OK"), purgeOutcomes());
    }

    /**
     * The purge lock is scoped to the ledger schema: a node purging a ledger in another schema of
     * the same database (another site, or another test's ledger) holds a different key and does not
     * stop this site's purge.
     */
    @Test
    void aPurgeLockInAnotherSchemaDoesNotBlockThisSite() throws Exception {
        rig.acceptConfig(14, null);
        TunnelRig otherSite = new TunnelRig();
        try (Connection other = LedgerTestDatabase.connect(otherSite.schema)) {
            advisory(other, "pg_advisory_lock");
            rig.purger.runIfDue();
            assertEquals(List.of("OK"), purgeOutcomes(), "another schema's purge lock is a different key");
            assertEquals("LOCKED", otherSite.purger.run(NOW, true).outcome(),
                    "the same schema's purge lock is the same key");
            assertTrue(advisory(other, "pg_advisory_unlock"));
        } finally {
            otherSite.close();
        }
    }

    @Test
    void aFailedDropLeavesThePartitionWhole() throws Exception {
        rig.acceptConfig(14, null);
        UUID old = rig.row().at(at(2019, 2, 2)).insert();
        try (Connection blocker = LedgerTestDatabase.connect(rig.schema)) {
            blocker.setAutoCommit(false);
            try (Statement st = blocker.createStatement()) {
                st.execute("LOCK TABLE edge_evaluations_2019_02 IN ACCESS EXCLUSIVE MODE");
            }
            RetentionPurger.Report r = rig.purger.purgeNow();
            assertEquals("FAILED", r.outcome());
            assertFalse(r.droppedPartitions().contains("edge_evaluations_2019_02"));
            blocker.rollback();
        }
        assertTrue(exists(old), "a lock timeout drops nothing of that partition");
        assertEquals("OK", rig.purger.purgeNow().outcome());
        assertFalse(exists(old));
    }

    /**
     * Calls an advisory lock function on the purge key of {@code c}'s schema, built the way
     * {@code RetentionPurger} builds it, and returns its boolean result (true for a void lock).
     */
    private static boolean advisory(Connection c, String function) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + function + "(" + RetentionPurger.LOCK_KEY_SQL + ")::text")) {
            ps.setString(1, RetentionPurger.LOCK_KEY_PREFIX);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return !"false".equals(rs.getString(1));
            }
        }
    }

    private boolean exists(UUID id) throws Exception {
        return count("edge_evaluations", id) == 1;
    }

    private boolean held(UUID id) throws Exception {
        return count("edge_held_evaluations", id) == 1;
    }

    private int count(String table, UUID id) throws Exception {
        try (Connection c = LedgerTestDatabase.connect(rig.schema);
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM " + table + " WHERE edge_assessment_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<String> purgeOutcomes() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = LedgerTestDatabase.connect(rig.schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT outcome FROM edge_purge_log ORDER BY run_id")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
