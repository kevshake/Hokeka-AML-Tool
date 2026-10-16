package com.posgateway.aml.service;

import com.posgateway.aml.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Idempotent backfill that seals legacy {@code audit_logs_enhanced} rows into the HMAC hash
 * chain introduced in V232 (gap P2-10), using exactly {@link AuditLogService#computeChecksum}.
 *
 * <p>Candidates are rows not yet backfilled whose {@code checksum} or {@code previous_checksum}
 * is null — i.e. rows written before chaining existed, or by a path that saved without sealing.
 * Rows are processed in id order and each candidate is classified:
 * <ul>
 *   <li><b>No checksum</b> → sealed: linked to its predecessor's current checksum and hashed.</li>
 *   <li><b>Checksum valid in the current format</b> (a genesis row, or a row whose predecessor
 *       was unsealed) → left as is; it is already sealed.</li>
 *   <li><b>Checksum valid in the pre-V232 format</b> → content proven authentic, so sealed.</li>
 *   <li><b>Checksum matches neither format</b> → <em>not</em> touched and reported: it may have
 *       been tampered with, and re-sealing it would erase that evidence.</li>
 * </ul>
 * Sealing stores the row's original checksum in {@code legacy_checksum} and stamps
 * {@code chain_backfilled_at}; {@link AuditLogService#linkMatches} accepts a successor's link to
 * that preserved value, so rows written after the legacy row stay verifiable without being
 * re-signed. Sealed rows are no longer candidates, so re-running changes nothing.
 */
@Service
public class AuditChainBackfillService {

    private static final Logger log = LoggerFactory.getLogger(AuditChainBackfillService.class);
    private static final int BATCH = 500;
    private static final int MAX_REPORTED_IDS = 50;

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;
    private final boolean enabled;

    /** Uses the primary {@code auditLogService} bean so the checksum key matches live writes. */
    public AuditChainBackfillService(JdbcTemplate jdbc,
                                     @Qualifier("auditLogService") AuditLogService auditLogService,
                                     @Value("${audit.chain.backfill.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
        this.enabled = enabled;
    }

    /** Result of one backfill pass. */
    public record BackfillResult(int sealed, int alreadySealed, List<Long> unverifiableIds, int unverifiableCount) {
    }

    /** Runs the backfill after startup; a failure is logged and never blocks the application. */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        if (!enabled) {
            log.info("Audit hash-chain backfill disabled (audit.chain.backfill.enabled=false)");
            return;
        }
        try {
            BackfillResult result = backfill();
            if (result.sealed() > 0 || result.unverifiableCount() > 0) {
                log.info("Audit hash-chain backfill: sealed {} legacy row(s), {} already sealed, {} unverifiable",
                        result.sealed(), result.alreadySealed(), result.unverifiableCount());
            }
            if (result.unverifiableCount() > 0) {
                log.warn("Audit rows whose checksum matches neither the current nor the legacy format were left "
                        + "untouched for investigation (first ids: {}). Verify via GET /audit/logs/verify.",
                        result.unverifiableIds());
            }
        } catch (RuntimeException e) {
            log.error("Audit hash-chain backfill failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Seals every eligible legacy row, in id order.
     *
     * @return counts of sealed / already-sealed rows and the (capped) ids left unverifiable
     */
    public BackfillResult backfill() {
        int sealed = 0;
        int alreadySealed = 0;
        int unverifiable = 0;
        List<Long> unverifiableIds = new ArrayList<>();
        long cursor = 0L;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, user_id, action_type, entity_type, entity_id, timestamp, before_value, "
                            + "after_value, reason, checksum, previous_checksum FROM audit_logs_enhanced "
                            + "WHERE (checksum IS NULL OR previous_checksum IS NULL) "
                            + "AND chain_backfilled_at IS NULL AND id > ? ORDER BY id LIMIT " + BATCH,
                    cursor);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> raw : rows) {
                AuditLog row = toAuditLog(raw);
                cursor = row.getId();
                switch (classify(row)) {
                    case ALREADY_SEALED -> alreadySealed++;
                    case UNVERIFIABLE -> {
                        unverifiable++;
                        if (unverifiableIds.size() < MAX_REPORTED_IDS) {
                            unverifiableIds.add(row.getId());
                        }
                    }
                    case SEAL -> sealed += seal(row);
                }
            }
            if (rows.size() < BATCH) {
                break;
            }
        }
        return new BackfillResult(sealed, alreadySealed, unverifiableIds, unverifiable);
    }

    /** How a candidate row is handled. */
    enum Disposition { SEAL, ALREADY_SEALED, UNVERIFIABLE }

    /** Classifies a candidate row per the rules in the class description. */
    Disposition classify(AuditLog row) {
        if (row.getChecksum() == null) {
            return Disposition.SEAL;
        }
        if (Objects.equals(row.getChecksum(), auditLogService.computeChecksum(row))) {
            return Disposition.ALREADY_SEALED;
        }
        if (auditLogService.matchesLegacyChecksum(row)) {
            return Disposition.SEAL;
        }
        return Disposition.UNVERIFIABLE;
    }

    /**
     * Links the row to its predecessor's current checksum, recomputes its checksum and writes
     * both, preserving the original checksum. Conditional on the row still being un-backfilled
     * with the checksum that was read, so concurrent runs cannot double-seal.
     *
     * @return 1 when the row was updated, 0 otherwise
     */
    int seal(AuditLog row) {
        List<String> previous = jdbc.queryForList(
                "SELECT checksum FROM audit_logs_enhanced WHERE id < ? ORDER BY id DESC LIMIT 1",
                String.class, row.getId());
        String originalChecksum = row.getChecksum();
        row.setPreviousChecksum(previous.isEmpty() ? null : previous.get(0));
        String newChecksum = auditLogService.computeChecksum(row);
        String guard = originalChecksum == null ? "checksum IS NULL" : "checksum = ?";
        String sql = "UPDATE audit_logs_enhanced SET legacy_checksum = checksum, checksum = ?, "
                + "previous_checksum = ?, chain_backfilled_at = ? "
                + "WHERE id = ? AND chain_backfilled_at IS NULL AND " + guard;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return originalChecksum == null
                ? jdbc.update(sql, newChecksum, row.getPreviousChecksum(), now, row.getId())
                : jdbc.update(sql, newChecksum, row.getPreviousChecksum(), now, row.getId(), originalChecksum);
    }

    /** Maps a JDBC row to the fields {@link AuditLogService#computeChecksum} reads. */
    private static AuditLog toAuditLog(Map<String, Object> raw) {
        AuditLog row = new AuditLog();
        row.setId(((Number) raw.get("id")).longValue());
        row.setUserId((String) raw.get("user_id"));
        row.setActionType((String) raw.get("action_type"));
        row.setEntityType((String) raw.get("entity_type"));
        row.setEntityId((String) raw.get("entity_id"));
        Object ts = raw.get("timestamp");
        row.setTimestamp(ts instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) ts);
        row.setBeforeValue((String) raw.get("before_value"));
        row.setAfterValue((String) raw.get("after_value"));
        row.setReason((String) raw.get("reason"));
        row.setChecksum((String) raw.get("checksum"));
        row.setPreviousChecksum((String) raw.get("previous_checksum"));
        return row;
    }
}
