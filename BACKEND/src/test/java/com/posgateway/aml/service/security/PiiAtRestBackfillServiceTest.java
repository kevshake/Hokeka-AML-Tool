package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.converter.ConverterKeyTestSupport;
import com.posgateway.aml.entity.converter.VersionedAesGcmStringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the PII-at-rest backfill against real tables (H2, PostgreSQL mode). */
class PiiAtRestBackfillServiceTest {

    private static final String HMAC_KEY = "unit-test-pii-lookup-key-0123456789abcdef";

    private JdbcTemplate jdbc;
    private PiiLookupHasher hasher;

    @BeforeEach
    void setUp() {
        ConverterKeyTestSupport.useZeroKey();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:pii" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE merchants (merchant_id BIGINT PRIMARY KEY, cbk_settlement_account_number TEXT, "
                + "cbk_settlement_account_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE psp_trust_accounts (id BIGINT PRIMARY KEY, bank_account_number TEXT)");
        jdbc.execute("CREATE TABLE market_executions (id BIGINT PRIMARY KEY, settlement_account_reference TEXT)");
        jdbc.execute("CREATE TABLE transactions (txn_id BIGINT PRIMARY KEY, pan_hash VARCHAR(255))");
        jdbc.execute("CREATE TABLE alerts (alert_id BIGINT PRIMARY KEY, pan_hash VARCHAR(128))");
        jdbc.execute("CREATE TABLE pii_backfill_runs (job_name VARCHAR(100) PRIMARY KEY, "
                + "completed_at TIMESTAMP NOT NULL, rows_changed BIGINT NOT NULL DEFAULT 0)");
        hasher = new PiiLookupHasher(HMAC_KEY);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void encryptsLegacyPlaintextAccountNumbersOnceAndBackfillsLookupHash() {
        VersionedAesGcmStringConverter converter = new VersionedAesGcmStringConverter();
        String alreadyEncrypted = converter.convertToDatabaseColumn("KES-999");
        jdbc.update("INSERT INTO merchants VALUES (1, 'KES-0023040100089', NULL)");
        jdbc.update("INSERT INTO merchants VALUES (2, ?, 'existinghash')", alreadyEncrypted);
        jdbc.update("INSERT INTO merchants VALUES (3, NULL, NULL)");
        jdbc.update("INSERT INTO psp_trust_accounts VALUES (10, 'USD-4892017364')");
        jdbc.update("INSERT INTO market_executions VALUES (20, 'SETTLE-ACC-77')");

        PiiAtRestBackfillService service = new PiiAtRestBackfillService(jdbc, hasher, true);
        assertThat(service.encryptLegacyAccountNumbers()).isEqualTo(3);

        String merchant1 = jdbc.queryForObject(
                "SELECT cbk_settlement_account_number FROM merchants WHERE merchant_id = 1", String.class);
        assertThat(merchant1).startsWith("enc:v1:");
        assertThat(converter.convertToEntityAttribute(merchant1)).isEqualTo("KES-0023040100089");
        assertThat(jdbc.queryForObject("SELECT cbk_settlement_account_hash FROM merchants WHERE merchant_id = 1",
                String.class)).isEqualTo(hasher.hashIdentifier("KES-0023040100089"));
        assertThat(jdbc.queryForObject("SELECT cbk_settlement_account_number FROM merchants WHERE merchant_id = 2",
                String.class)).isEqualTo(alreadyEncrypted);
        String trust = jdbc.queryForObject("SELECT bank_account_number FROM psp_trust_accounts WHERE id = 10",
                String.class);
        assertThat(converter.convertToEntityAttribute(trust)).isEqualTo("USD-4892017364");
        String market = jdbc.queryForObject(
                "SELECT settlement_account_reference FROM market_executions WHERE id = 20", String.class);
        assertThat(converter.convertToEntityAttribute(market)).isEqualTo("SETTLE-ACC-77");

        // Idempotent: nothing left to encrypt.
        assertThat(service.encryptLegacyAccountNumbers()).isZero();
    }

    @Test
    void skipsEncryptionWhenKeyIsMissing() {
        ConverterKeyTestSupport.clearKey();
        try {
            jdbc.update("INSERT INTO psp_trust_accounts VALUES (10, 'USD-4892017364')");
            assertThat(new PiiAtRestBackfillService(jdbc, hasher, true).encryptLegacyAccountNumbers()).isZero();
            assertThat(jdbc.queryForObject("SELECT bank_account_number FROM psp_trust_accounts WHERE id = 10",
                    String.class)).isEqualTo("USD-4892017364");
        } finally {
            ConverterKeyTestSupport.useZeroKey();
        }
    }

    @Test
    void scrubsRawPansFromPanHashOnceAndLeavesHashesAlone() {
        String realHash = hasher.hashIdentifier("4111111111111111");
        jdbc.update("INSERT INTO transactions VALUES (1, '4111111111111111')");
        jdbc.update("INSERT INTO transactions VALUES (2, ?)", realHash);
        jdbc.update("INSERT INTO transactions VALUES (3, 'UNKNOWN')");
        jdbc.update("INSERT INTO transactions VALUES (4, '5500 0000 0000 0004')");
        jdbc.update("INSERT INTO alerts VALUES (7, '4111111111111111')");

        PiiAtRestBackfillService service = new PiiAtRestBackfillService(jdbc, hasher, true);
        assertThat(service.scrubRawPanHashes()).isEqualTo(3);

        assertThat(jdbc.queryForObject("SELECT pan_hash FROM transactions WHERE txn_id = 1", String.class))
                .isEqualTo(realHash);
        assertThat(jdbc.queryForObject("SELECT pan_hash FROM transactions WHERE txn_id = 4", String.class))
                .isEqualTo(hasher.hashIdentifier("5500000000000004"));
        assertThat(jdbc.queryForObject("SELECT pan_hash FROM transactions WHERE txn_id = 3", String.class))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT pan_hash FROM alerts WHERE alert_id = 7", String.class))
                .isEqualTo(realHash);
        assertThat(jdbc.queryForObject("SELECT rows_changed FROM pii_backfill_runs WHERE job_name = ?",
                Long.class, PiiAtRestBackfillService.RAW_PAN_SCRUB_JOB)).isEqualTo(3L);

        // Recorded as complete: does not run again.
        assertThat(service.scrubRawPanHashes()).isEqualTo(-1);
    }

    @Test
    void scrubIsDeferredWithoutHmacKey() {
        jdbc.update("INSERT INTO transactions VALUES (1, '4111111111111111')");
        PiiAtRestBackfillService service = new PiiAtRestBackfillService(jdbc, new PiiLookupHasher(""), true);
        assertThat(service.scrubRawPanHashes()).isEqualTo(-1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pii_backfill_runs", Integer.class)).isZero();
    }
}
