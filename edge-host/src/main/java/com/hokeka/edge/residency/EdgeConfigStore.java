package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.ledger.EdgeLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds the Edge config document in force and applies the contract's acceptance rules
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §6.3) to every candidate, whoever fetched it:
 *
 * <ol>
 *   <li>validate against {@code edge-config.schema.json} (sections closed, top level open);</li>
 *   <li>{@code pspId} must equal this node's PSP ({@code EDGE_PSPID});</li>
 *   <li>a lower {@code configVersion} than the one held is refused (rollback protection); the same
 *       version must carry the same canonical content;</li>
 *   <li>the document is appended to {@code edge_config} (history) <b>before</b> it is used — if the
 *       ledger cannot take it, it is refused and the previous one stays in force.</li>
 * </ol>
 *
 * <p>Opening the HSE-1 envelope (signature, AEAD, replay, {@code edgeId}) is the fetcher's job
 * ({@link EdgeConfigPoller}); this class receives only an opened payload. After a restart the
 * newest stored version is restored from {@code edge_config}, so a node that has accepted a
 * document once keeps purging and gating with it even while the Control Plane is unreachable.
 */
public class EdgeConfigStore {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfigStore.class);

    /** Schema of the document (contract §6). */
    public static final String SCHEMA = "edge-config.schema.json";

    /** Outcome of one {@link #offer}. */
    public enum Outcome {
        /** Stored in the history and now in force. */
        ACCEPTED,
        /** Same version and content as the one in force; nothing changed. */
        UNCHANGED,
        /** Not used; the previous document (if any) stays in force. */
        REFUSED
    }

    /**
     * Result of {@link #offer}.
     *
     * @param outcome what happened
     * @param reason  log-safe reason for a refusal ({@code ""} otherwise); never a document value
     * @param version the version offered (0 when unreadable)
     */
    public record OfferResult(Outcome outcome, String reason, long version) {
    }

    private final EdgeLedger ledger;
    private final ContractSchemas schemas;
    private final ObjectMapper mapper;
    private final Long pspId;
    private final String nodeId;
    private final AtomicReference<EdgeConfigDocument> current = new AtomicReference<>();
    private final List<Consumer<EdgeConfigDocument>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean restored;
    private volatile Instant acceptedAt;
    private volatile String lastRefusal = "";

    /**
     * @param ledger  the site ledger holding {@code edge_config}
     * @param schemas contract schema validator
     * @param mapper  JSON mapper
     * @param pspId   this node's PSP id ({@code EDGE_PSPID}); null when unset or not a positive integer,
     *                in which case every document is refused
     * @param nodeId  this node's edge id (recorded as {@code accepted_by})
     */
    public EdgeConfigStore(EdgeLedger ledger, ContractSchemas schemas, ObjectMapper mapper, Long pspId, String nodeId) {
        this.ledger = ledger;
        this.schemas = schemas;
        this.mapper = mapper;
        this.pspId = pspId;
        this.nodeId = nodeId == null ? "" : nodeId;
    }

    /**
     * Parses {@code EDGE_PSPID}: a positive integer, else null (the caller logs the variable name).
     *
     * @param raw the configured value
     */
    public static Long parsePspId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v >= 1 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** This node's PSP id, or null when not configured. */
    public Long pspId() {
        return pspId;
    }

    /** Registers a callback run (on the accepting thread) after each newly accepted document. */
    public void addListener(Consumer<EdgeConfigDocument> listener) {
        listeners.add(listener);
    }

    /**
     * The document in force, restoring the newest stored one from the ledger on first use (and
     * retrying that while the ledger is down).
     */
    public Optional<EdgeConfigDocument> current() {
        if (!restored) {
            restoreFromLedger();
        }
        return Optional.ofNullable(current.get());
    }

    /** {@code configVersion} in force, if any. */
    public OptionalLong currentVersion() {
        EdgeConfigDocument d = current().orElse(null);
        return d == null ? OptionalLong.empty() : OptionalLong.of(d.configVersion());
    }

    /** When the document in force was accepted by this process (null when restored or none). */
    public Instant acceptedAt() {
        return acceptedAt;
    }

    /** Reason of the most recent refusal ({@code ""} when the last offer was not refused). */
    public String lastRefusal() {
        return lastRefusal;
    }

    /**
     * Applies the acceptance rules to an opened document and, when it passes, stores and activates it.
     *
     * @param document the opened {@code payload} of the sealed config envelope
     * @param now      acceptance time
     */
    public synchronized OfferResult offer(JsonNode document, Instant now) {
        current();
        long version = document != null && document.path("configVersion").isIntegralNumber()
                ? document.get("configVersion").asLong() : 0L;
        if (document == null || !document.isObject()) {
            return refuse("document is not a JSON object", version);
        }
        List<String> violations = schemas.validate(SCHEMA, document);
        if (!violations.isEmpty()) {
            return refuse("document fails edge-config.schema.json at " + violations, version);
        }
        if (pspId == null) {
            return refuse("EDGE_PSPID is not configured as a positive integer", version);
        }
        if (document.get("pspId").asLong() != pspId) {
            return refuse("document is for another PSP", version);
        }
        String sha = Jcs.sha256Hex(document);
        EdgeConfigDocument held = current.get();
        if (held != null) {
            if (version < held.configVersion()) {
                return refuse("configVersion " + version + " is lower than the held " + held.configVersion()
                        + " (rollback refused)", version);
            }
            if (version == held.configVersion()) {
                if (sha.equals(held.sha256())) {
                    lastRefusal = "";
                    return new OfferResult(Outcome.UNCHANGED, "", version);
                }
                return refuse("configVersion " + version + " re-issued with different content", version);
            }
        }
        EdgeConfigDocument parsed;
        try {
            parsed = EdgeConfigDocument.parse(document, sha);
        } catch (RuntimeException e) {
            return refuse("document could not be parsed (" + e.getClass().getSimpleName() + ")", version);
        }
        try {
            String stored = append(parsed, now);
            if (!sha.equals(stored)) {
                return refuse("the site ledger already holds a different document under configVersion " + version,
                        version);
            }
        } catch (SQLException | RuntimeException e) {
            return refuse("could not append to edge_config (" + EdgeLedger.safeMessage(e) + ")", version);
        }
        current.set(parsed);
        acceptedAt = now;
        lastRefusal = "";
        log.info("Edge config v{} accepted (retention {} y, support access {}, tunnel {}, {} op(s) allowed)",
                version, parsed.retentionYears(), parsed.supportAccess() ? "on" : "off",
                parsed.tunnel().enabled() ? "enabled" : "disabled", parsed.tunnel().opsAllowed().size());
        for (Consumer<EdgeConfigDocument> l : listeners) {
            try {
                l.accept(parsed);
            } catch (RuntimeException e) {
                log.warn("Edge config listener {} failed: {}", l.getClass().getSimpleName(), e.getClass().getSimpleName());
            }
        }
        return new OfferResult(Outcome.ACCEPTED, "", version);
    }

    /** Records and logs a refusal; the document in force is untouched. */
    private OfferResult refuse(String reason, long version) {
        lastRefusal = reason;
        log.warn("Edge config v{} refused: {} — the previous document stays in force", version, reason);
        return new OfferResult(Outcome.REFUSED, reason, version);
    }

    /**
     * Inserts the document into {@code edge_config} (idempotent per version, so every node of a
     * site can accept the same version) and returns the digest stored under that version.
     */
    private String append(EdgeConfigDocument doc, Instant now) throws SQLException {
        if (!ledger.ready() || ledger.dataSource() == null) {
            throw new SQLException("Edge Ledger is not ready", "08000");
        }
        try (Connection c = ledger.dataSource().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO edge_config (config_version, psp_id, document, document_sha256, accepted_at, accepted_by)
                    VALUES (?, ?, ?::jsonb, ?, ?, ?)
                    ON CONFLICT (config_version) DO NOTHING
                    """)) {
                ps.setLong(1, doc.configVersion());
                ps.setLong(2, doc.pspId());
                ps.setString(3, mapper.writeValueAsString(doc.document()));
                ps.setString(4, doc.sha256());
                ps.setObject(5, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                ps.setString(6, nodeId);
                ps.executeUpdate();
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new SQLException("config document could not be serialised", "22000", e);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT document_sha256 FROM edge_config WHERE config_version = ?")) {
                ps.setLong(1, doc.configVersion());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : "";
                }
            }
        }
    }

    /**
     * Loads the newest stored document of this PSP (re-validated against the schema) so a restart
     * keeps the accepted settings. Retried on later calls while the ledger is not ready.
     */
    private synchronized void restoreFromLedger() {
        if (restored) {
            return;
        }
        if (pspId == null || !ledger.ready() || ledger.dataSource() == null) {
            return;
        }
        try (Connection c = ledger.dataSource().getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT document::text FROM edge_config WHERE psp_id = ? ORDER BY config_version DESC LIMIT 1
                     """)) {
            ps.setLong(1, pspId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    JsonNode doc = mapper.readTree(rs.getString(1));
                    List<String> violations = schemas.validate(SCHEMA, doc);
                    if (violations.isEmpty()) {
                        EdgeConfigDocument parsed = EdgeConfigDocument.parse(doc, Jcs.sha256Hex(doc));
                        if (current.get() == null || current.get().configVersion() < parsed.configVersion()) {
                            current.set(parsed);
                            log.info("Edge config v{} restored from the site ledger", parsed.configVersion());
                        }
                    } else {
                        log.warn("Stored Edge config no longer passes edge-config.schema.json ({}); waiting for a fresh "
                                + "document", violations);
                    }
                }
            }
            restored = true;
        } catch (SQLException | java.io.IOException | RuntimeException e) {
            log.warn("Could not restore the Edge config from the ledger ({}); will retry", EdgeLedger.safeMessage(e));
        }
    }
}
