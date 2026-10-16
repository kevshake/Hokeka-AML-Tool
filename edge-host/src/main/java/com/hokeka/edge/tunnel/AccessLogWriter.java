package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.hokeka.edge.ledger.EdgeLedger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The Edge side of the tunnel audit (EDGE_DATA_RESIDENCY_CONTRACT.md §7.15): one
 * {@code edge_access_log} row per request — written on receipt, also for requests that are refused,
 * and completed with the outcome, the item count and the returned ids. Never params, never items:
 * the params are recorded only as their RFC 8785 SHA-256 digest.
 *
 * <p>export.stream ids go to {@code edge_access_log_exports}, one row per chunk.
 */
public class AccessLogWriter {

    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * Actor and request facts as received (best effort: a malformed request is logged with what
     * could be read).
     *
     * @param auditId      the cloud's audit id, or the request frame id when the body has none
     * @param op           op name as received ({@code "?"} when absent)
     * @param actorKind    actor.kind, or null
     * @param actorUserId  actor.userId, or null
     * @param actorRole    actor.role, or null
     * @param actorPspId   actor.pspId, or null
     * @param purpose      actor.purpose, or null
     * @param caseId       actor.caseId, or null
     * @param alertId      actor.alertId, or null
     * @param filingRef    actor.filingRef, or null
     * @param supportReason actor.supportReason, or null
     * @param paramsDigest SHA-256 hex of the JCS params, or null when params are absent
     */
    public record Entry(UUID auditId, String op, String actorKind, String actorUserId, String actorRole,
                        Long actorPspId, String purpose, String caseId, String alertId, String filingRef,
                        String supportReason, String paramsDigest) {

        /** Whether this is a support-access pull (§9: flagged in both audit logs). */
        public boolean supportAccess() {
            return "PLATFORM_USER".equals(actorKind);
        }

        /**
         * Reads the audit facts from a request body without trusting its shape.
         *
         * @param body    the opened request body
         * @param frameId the request frame id (fallback audit id)
         */
        public static Entry from(JsonNode body, String frameId) {
            JsonNode actor = body.path("actor");
            String audit = body.path("auditId").asText("");
            UUID auditId = UUID_TEXT.matcher(audit).matches() ? UUID.fromString(audit) : UUID.fromString(frameId);
            JsonNode params = body.get("params");
            return new Entry(auditId, clip(text(body, "op"), 64, "?"), clip(text(actor, "kind"), 32, null),
                    clip(text(actor, "userId"), 128, null), clip(text(actor, "role"), 64, null),
                    actor.path("pspId").isIntegralNumber() ? actor.get("pspId").asLong() : null,
                    clip(text(actor, "purpose"), 64, null), clip(text(actor, "caseId"), 64, null),
                    clip(text(actor, "alertId"), 64, null), clip(text(actor, "filingRef"), 64, null),
                    clip(text(actor, "supportReason"), 500, null),
                    params != null && params.isObject() ? TunnelCursor.queryHash(params) : null);
        }

        /** A textual member, or null. */
        private static String text(JsonNode node, String name) {
            JsonNode v = node.get(name);
            return v != null && v.isTextual() ? v.asText() : null;
        }

        /** Cuts a value to {@code max} characters; null becomes {@code fallback}. */
        private static String clip(String s, int max, String fallback) {
            if (s == null) {
                return fallback;
            }
            return s.length() > max ? s.substring(0, max) : s;
        }
    }

    private final EdgeLedger ledger;

    /** @param ledger the site ledger (write pool) */
    public AccessLogWriter(EdgeLedger ledger) {
        this.ledger = ledger;
    }

    /**
     * Writes the row on receipt.
     *
     * @return the row id, used to complete it
     * @throws SQLException when the ledger cannot take it (the request is then refused: I5/I8)
     */
    public long begin(Entry e, String sessionId, Instant receivedAt) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO edge_access_log (audit_id, received_at, session_id, node_id, op, actor_kind, actor_user_id,
                                                  actor_role, actor_psp_id, purpose, case_id, alert_id, filing_ref,
                                                  support_access, support_reason, params_digest)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     RETURNING id
                     """)) {
            int i = 1;
            ps.setObject(i++, e.auditId());
            ps.setObject(i++, OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC));
            ps.setObject(i++, sessionId == null ? null : UUID.fromString(sessionId));
            ps.setString(i++, ledger.nodeId());
            ps.setString(i++, e.op());
            ps.setString(i++, e.actorKind());
            ps.setString(i++, e.actorUserId());
            ps.setString(i++, e.actorRole());
            if (e.actorPspId() == null) {
                ps.setNull(i++, java.sql.Types.BIGINT);
            } else {
                ps.setLong(i++, e.actorPspId());
            }
            ps.setString(i++, e.purpose());
            ps.setString(i++, e.caseId());
            ps.setString(i++, e.alertId());
            ps.setString(i++, e.filingRef());
            ps.setBoolean(i++, e.supportAccess());
            ps.setString(i++, e.supportReason());
            ps.setString(i, e.paramsDigest());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Completes the row.
     *
     * @param outcome     {@code OK} or the §7.11 error code
     * @param itemCount   items returned
     * @param returnedIds ids of the evaluations returned (not used for export.stream, see
     *                    {@link #exportChunk})
     */
    public void complete(long rowId, String outcome, int itemCount, List<UUID> returnedIds, Instant completedAt)
            throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE edge_access_log
                        SET completed_at = ?, outcome = ?, item_count = ?, returned_ids = ?
                      WHERE id = ?
                     """)) {
            ps.setObject(1, OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC));
            ps.setString(2, outcome);
            ps.setInt(3, Math.max(0, itemCount));
            ps.setArray(4, c.createArrayOf("uuid", returnedIds.toArray()));
            ps.setLong(5, rowId);
            ps.executeUpdate();
        }
    }

    /** Records the ids of one export chunk. */
    public void exportChunk(long rowId, int chunkSeq, List<UUID> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO edge_access_log_exports (access_log_id, chunk_seq, returned_ids) VALUES (?, ?, ?)")) {
            ps.setLong(1, rowId);
            ps.setInt(2, chunkSeq);
            ps.setArray(3, c.createArrayOf("uuid", ids.toArray()));
            ps.executeUpdate();
        }
    }

    /** A write-pool connection, or a fail-fast error when the ledger is not ready. */
    private Connection connection() throws SQLException {
        if (!ledger.ready() || ledger.dataSource() == null) {
            throw new SQLException("Edge Ledger is not ready", "08000");
        }
        return ledger.dataSource().getConnection();
    }
}
