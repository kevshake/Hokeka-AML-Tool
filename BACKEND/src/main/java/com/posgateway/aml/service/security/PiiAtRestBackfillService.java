package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.converter.AesGcmStringConverter;
import com.posgateway.aml.entity.converter.VersionedAesGcmStringConverter;
import com.posgateway.aml.util.PanHashGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Idempotent startup backfill for PII-at-rest (gap P2-11).
 *
 * <ol>
 *   <li><b>Account-number encryption.</b> Encrypts legacy plaintext values in the account-number
 *       columns that are now mapped through {@link VersionedAesGcmStringConverter}
 *       (merchant settlement account, PSP trust bank account, market settlement account).
 *       A row is legacy when its value does not start with {@code enc:v1:}; already-encrypted
 *       rows are never touched, so re-running is a no-op. Where a keyed lookup-hash column exists
 *       (merchant settlement account) a missing hash is filled from the plaintext first.</li>
 *   <li><b>Raw-PAN scrub.</b> Replaces any {@code pan_hash} value shaped like a raw card number
 *       (see {@link PanHashGuard}) in {@code transactions} and {@code alerts} with the keyed HMAC
 *       used by the ingest path. This is a full-table scan, so completion is recorded in
 *       {@code pii_backfill_runs} and the scrub does not repeat; new raw PANs are blocked at
 *       write time by the entity guard.</li>
 * </ol>
 *
 * <p>Each update is conditional on the row still holding the value that was read, so a
 * concurrent application write is never overwritten. Logs carry counts only, never values.
 * Steps that need a key (encryption key / PII HMAC key) are skipped with a WARN when that key
 * is not configured, and run on the next boot once it is.
 */
@Service
public class PiiAtRestBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PiiAtRestBackfillService.class);

    static final String ENCRYPTED_PREFIX = "enc:v1:";
    static final String RAW_PAN_SCRUB_JOB = "raw_pan_hash_scrub_v1";
    private static final int BATCH = 500;

    /** An encrypted account-number column, with its optional keyed lookup-hash column. */
    record EncryptedColumn(String table, String idColumn, String valueColumn, String hashColumn) {
    }

    /** A pan_hash column to scrub for raw card numbers. */
    record PanColumn(String table, String idColumn, String valueColumn) {
    }

    static final List<EncryptedColumn> ENCRYPTED_COLUMNS = List.of(
            new EncryptedColumn("merchants", "merchant_id", "cbk_settlement_account_number",
                    "cbk_settlement_account_hash"),
            new EncryptedColumn("psp_trust_accounts", "id", "bank_account_number", null),
            new EncryptedColumn("market_executions", "id", "settlement_account_reference", null));

    static final List<PanColumn> PAN_COLUMNS = List.of(
            new PanColumn("transactions", "txn_id", "pan_hash"),
            new PanColumn("alerts", "alert_id", "pan_hash"));

    private final JdbcTemplate jdbc;
    private final PiiLookupHasher hasher;
    private final VersionedAesGcmStringConverter converter = new VersionedAesGcmStringConverter();
    private final boolean enabled;

    /** Wires JDBC access, the keyed PII hasher and the on/off switch. */
    public PiiAtRestBackfillService(JdbcTemplate jdbc, PiiLookupHasher hasher,
                                    @Value("${security.pii.backfill.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.enabled = enabled;
    }

    /** Runs both backfill steps once the application is up; failures are logged, not fatal. */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        if (!enabled) {
            log.info("PII-at-rest backfill disabled (security.pii.backfill.enabled=false)");
            return;
        }
        try {
            encryptLegacyAccountNumbers();
        } catch (RuntimeException e) {
            log.error("Legacy account-number encryption backfill failed: {}", e.getMessage(), e);
        }
        try {
            scrubRawPanHashes();
        } catch (RuntimeException e) {
            log.error("Raw-PAN pan_hash scrub failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Encrypts every legacy plaintext account number in {@link #ENCRYPTED_COLUMNS}.
     *
     * @return number of rows encrypted (0 when the key is not configured)
     */
    public int encryptLegacyAccountNumbers() {
        if (AesGcmStringConverter.keyStatus() != AesGcmStringConverter.KeyStatus.OK) {
            log.warn("Field encryption key not usable; legacy plaintext account numbers were NOT encrypted. "
                    + "Set ENCRYPTION_KEY / SECURITY_ENCRYPTION_KEY and restart.");
            return 0;
        }
        int total = 0;
        for (EncryptedColumn column : ENCRYPTED_COLUMNS) {
            int changed = encryptColumn(column);
            if (changed > 0) {
                log.info("Encrypted {} legacy plaintext value(s) in {}.{}", changed, column.table(), column.valueColumn());
            }
            total += changed;
        }
        return total;
    }

    /** Encrypts one column's legacy rows in id-ordered batches, filling a missing lookup hash first. */
    int encryptColumn(EncryptedColumn c) {
        String select = "SELECT " + c.idColumn() + " AS id, " + c.valueColumn() + " AS val"
                + (c.hashColumn() != null ? ", " + c.hashColumn() + " AS hash" : "")
                + " FROM " + c.table()
                + " WHERE " + c.valueColumn() + " IS NOT NULL AND " + c.valueColumn() + " <> ''"
                + " AND " + c.valueColumn() + " NOT LIKE '" + ENCRYPTED_PREFIX + "%'"
                + " AND " + c.idColumn() + " > ? ORDER BY " + c.idColumn() + " LIMIT " + BATCH;
        String updateValue = "UPDATE " + c.table() + " SET " + c.valueColumn() + " = ?"
                + " WHERE " + c.idColumn() + " = ? AND " + c.valueColumn() + " = ?";
        String updateValueAndHash = c.hashColumn() == null ? null
                : "UPDATE " + c.table() + " SET " + c.valueColumn() + " = ?, " + c.hashColumn() + " = ?"
                + " WHERE " + c.idColumn() + " = ? AND " + c.valueColumn() + " = ?";
        boolean canHash = c.hashColumn() != null && hasher.isConfigured();

        int changed = 0;
        long cursor = Long.MIN_VALUE;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(select, cursor);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                cursor = Math.max(cursor, id);
                String plaintext = (String) row.get("val");
                String ciphertext = converter.convertToDatabaseColumn(plaintext);
                Object existingHash = row.get("hash");
                if (canHash && existingHash == null) {
                    changed += jdbc.update(updateValueAndHash, ciphertext, hasher.hashIdentifier(plaintext), id, plaintext);
                } else {
                    changed += jdbc.update(updateValue, ciphertext, id, plaintext);
                }
            }
            if (rows.size() < BATCH) {
                break;
            }
        }
        return changed;
    }

    /**
     * Replaces raw-PAN-shaped {@code pan_hash} values with their keyed HMAC, once.
     *
     * @return rows changed, or -1 when skipped (already completed, or HMAC key not configured)
     */
    public long scrubRawPanHashes() {
        Integer done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pii_backfill_runs WHERE job_name = ?", Integer.class, RAW_PAN_SCRUB_JOB);
        if (done != null && done > 0) {
            return -1;
        }
        if (!hasher.isConfigured()) {
            log.warn("PII_LOOKUP_HMAC_KEY not configured; raw-PAN scrub of pan_hash columns deferred to a later boot.");
            return -1;
        }
        long total = 0;
        for (PanColumn column : PAN_COLUMNS) {
            long changed = scrubColumn(column);
            if (changed > 0) {
                log.warn("Replaced {} raw card number(s) found in {}.{} with keyed hashes",
                        changed, column.table(), column.valueColumn());
            }
            total += changed;
        }
        jdbc.update("INSERT INTO pii_backfill_runs (job_name, completed_at, rows_changed) "
                + "VALUES (?, CURRENT_TIMESTAMP, ?)", RAW_PAN_SCRUB_JOB, total);
        log.info("Raw-PAN scrub of pan_hash columns complete ({} row(s) changed)", total);
        return total;
    }

    /**
     * Scrubs one pan_hash column. The portable SQL prefilter (13-40 chars of only digits, spaces
     * and dashes) is confirmed by {@link PanHashGuard} in Java before any update.
     */
    long scrubColumn(PanColumn c) {
        String select = "SELECT " + c.idColumn() + " AS id, " + c.valueColumn() + " AS val FROM " + c.table()
                + " WHERE LENGTH(" + c.valueColumn() + ") BETWEEN 13 AND 40"
                // Map every allowed char to '0': value is digits/spaces/dashes only iff all zeros.
                + " AND TRANSLATE(" + c.valueColumn() + ", '0123456789 -', '000000000000')"
                + " = REPEAT('0', LENGTH(" + c.valueColumn() + "))"
                + " AND " + c.idColumn() + " > ?"
                + " ORDER BY " + c.idColumn() + " LIMIT " + BATCH;
        String update = "UPDATE " + c.table() + " SET " + c.valueColumn() + " = ? WHERE "
                + c.idColumn() + " = ? AND " + c.valueColumn() + " = ?";
        long changed = 0;
        long cursor = Long.MIN_VALUE;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(select, cursor);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                cursor = Math.max(cursor, id);
                String value = (String) row.get("val");
                if (PanHashGuard.looksLikeRawPan(value)) {
                    changed += jdbc.update(update, hasher.hashIdentifier(value), id, value);
                }
            }
            if (rows.size() < BATCH) {
                break;
            }
        }
        return changed;
    }
}
