package com.hokeka.edge.ledger;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the Edge Ledger (EDGE_DATA_RESIDENCY_CONTRACT.md §1): the site's PostgreSQL 16
 * store of evaluations and the decision-record outbox. Bound from {@code hokeka.ledger.*}, which
 * {@code application.yml} maps onto the {@code EDGE_LEDGER_*} / {@code EDGE_SITE_ID} environment
 * variables written by the installers.
 *
 * <p>There are no permissive defaults for the connection: without a URL, user and password the
 * ledger is reported as not configured (listing the missing <i>variable names</i>, never values),
 * and with {@code fail-closed=true} every evaluation answers {@code HOLD / LEDGER_UNAVAILABLE}.
 */
@ConfigurationProperties(prefix = "hokeka.ledger")
public class LedgerProperties {

    /** JDBC URL of the site ledger, e.g. {@code jdbc:postgresql://ledger:5432/hokeka_ledger}. */
    private String url = "";
    private String username = "";
    private String password = "";

    /** Schema holding the ledger tables (created by Flyway when absent). */
    private String schema = "edge_ledger";

    /**
     * Edge Site id (contract §1). Empty means a single-node site, whose site id defaults to the
     * node's {@code edgeId}.
     */
    private String siteId = "";

    /**
     * Contract §4.3 / invariant I8: when the evaluation + outbox transaction fails, answer
     * {@code HOLD} with {@code failClosedCode = LEDGER_UNAVAILABLE} instead of the evaluated decision.
     */
    private boolean failClosed = true;

    /** Connection pool size (shared by the evaluate path, the shipper and maintenance). */
    private int poolSize = 16;

    /** How long a caller waits for a pooled connection before the write counts as failed. */
    private Duration connectionTimeout = Duration.ofMillis(1000);

    /**
     * Socket read timeout for ledger statements. Bounds how long one evaluation can wait on a
     * stalled database before failing closed.
     */
    private Duration writeTimeout = Duration.ofSeconds(2);

    /** TCP connect timeout to the database. */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** Monthly partitions kept ready beyond the current UTC month. */
    private int partitionMonthsAhead = 3;

    /** How often partitions are topped up and an unreachable ledger is re-initialised. */
    private Duration maintenanceInterval = Duration.ofMinutes(10);

    /** How often a not-yet-initialised ledger (database down at boot) is retried. */
    private Duration initRetryInterval = Duration.ofSeconds(10);

    /**
     * Evaluations whose ledger write failed, kept in memory and retried until the ledger is
     * writable again (§4.3). When full, the oldest entry is dropped and counted.
     */
    private int retryBufferSize = 10_000;

    /** How often the in-memory retry buffer is drained. */
    private Duration retryInterval = Duration.ofSeconds(1);

    /** Decision records per POST (contract §4.2: 1..500). */
    private int outboxBatchSize = 500;

    /**
     * Upper bound of one sealed batch body; the Control Plane answers 413 above
     * {@code edge.metrics.max-payload-bytes} (default 256 KiB), so batches are sized to fit.
     */
    private int outboxMaxPayloadBytes = 256 * 1024;

    /**
     * Connections of the separate read pool used by Edge Tunnel requests (contract §7.6). Kept apart
     * from the evaluate-path pool so a burst of cloud reads can never starve evaluations, and
     * configured without the write-path socket timeout: tunnel statements are bounded by
     * {@code statement_timeout} from each request's deadline instead.
     */
    private int readPoolSize = 4;

    /**
     * Local retention floor in years ({@code EDGE_RETENTION_MIN_YEARS}, contract §6.3): effective
     * retention = max(config {@code retention.years}, this). Values below the contract floor of 5
     * are ignored with a warning; it can only raise retention, never lower it.
     */
    private int retentionMinYears = 5;

    /** Site-local time of the daily purge (contract §8.2 default 02:00), {@code HH:mm}. */
    private String purgeTime = "02:00";

    /** IANA zone of the site's local clock for the purge time; empty = the host's default zone. */
    private String siteTimezone = "";

    /** How often the purge scheduler checks whether today's run is due. */
    private Duration purgeCheckInterval = Duration.ofMinutes(5);

    /**
     * Longest wait for the lock needed to drop one partition. A partition DROP takes an exclusive
     * lock on edge_evaluations; capping the wait keeps evaluations from queueing behind it. On a
     * timeout that run fails without dropping anything and is retried the next day (§8.2).
     */
    private Duration purgeLockTimeout = Duration.ofSeconds(1);

    /**
     * Names of the environment variables that must be set but are not. Values are never returned,
     * so the result is safe to log.
     *
     * @return e.g. {@code [EDGE_LEDGER_URL, EDGE_LEDGER_PASSWORD]}; empty when fully configured
     */
    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (isBlank(url)) {
            missing.add("EDGE_LEDGER_URL");
        }
        if (isBlank(username)) {
            missing.add("EDGE_LEDGER_USER");
        }
        if (isBlank(password)) {
            missing.add("EDGE_LEDGER_PASSWORD");
        }
        return missing;
    }

    /**
     * The site id in force: the configured one, or the node's edge id for a single-node site.
     *
     * @param edgeId this node's id ({@code EDGE_EDGEID})
     */
    public String effectiveSiteId(String edgeId) {
        return isBlank(siteId) ? (edgeId == null ? "" : edgeId.trim()) : siteId.trim();
    }

    /**
     * A log-safe description of the target: scheme, host, port and database only. Credentials and
     * query parameters (which may carry a password) are cut off.
     */
    public String redactedTarget() {
        if (isBlank(url)) {
            return "(unset)";
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        int at = u.indexOf('@');
        int scheme = u.indexOf("//");
        if (at >= 0 && scheme >= 0 && at > scheme) {
            u = u.substring(0, scheme + 2) + u.substring(at + 1);
        }
        return u;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** @return the JDBC URL */
    public String getUrl() {
        return url;
    }

    /** @param url the JDBC URL */
    public void setUrl(String url) {
        this.url = url;
    }

    /** @return the database user */
    public String getUsername() {
        return username;
    }

    /** @param username the database user */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return the database password (never log it) */
    public String getPassword() {
        return password;
    }

    /** @param password the database password */
    public void setPassword(String password) {
        this.password = password;
    }

    /** @return the ledger schema */
    public String getSchema() {
        return schema;
    }

    /** @param schema the ledger schema */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /** @return the configured site id (may be blank) */
    public String getSiteId() {
        return siteId;
    }

    /** @param siteId the configured site id */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /** @return whether a failed ledger write turns the decision into HOLD */
    public boolean isFailClosed() {
        return failClosed;
    }

    /** @param failClosed whether a failed ledger write turns the decision into HOLD */
    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    /** @return the pool size */
    public int getPoolSize() {
        return poolSize;
    }

    /** @param poolSize the pool size */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /** @return the pool checkout timeout */
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /** @param connectionTimeout the pool checkout timeout */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /** @return the statement socket timeout */
    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    /** @param writeTimeout the statement socket timeout */
    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    /** @return the TCP connect timeout */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @param connectTimeout the TCP connect timeout */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /** @return months of partitions kept ready ahead */
    public int getPartitionMonthsAhead() {
        return partitionMonthsAhead;
    }

    /** @param partitionMonthsAhead months of partitions kept ready ahead */
    public void setPartitionMonthsAhead(int partitionMonthsAhead) {
        this.partitionMonthsAhead = partitionMonthsAhead;
    }

    /** @return the maintenance interval */
    public Duration getMaintenanceInterval() {
        return maintenanceInterval;
    }

    /** @param maintenanceInterval the maintenance interval */
    public void setMaintenanceInterval(Duration maintenanceInterval) {
        this.maintenanceInterval = maintenanceInterval;
    }

    /** @return the initialisation retry interval */
    public Duration getInitRetryInterval() {
        return initRetryInterval;
    }

    /** @param initRetryInterval the initialisation retry interval */
    public void setInitRetryInterval(Duration initRetryInterval) {
        this.initRetryInterval = initRetryInterval;
    }

    /** @return the retry buffer capacity */
    public int getRetryBufferSize() {
        return retryBufferSize;
    }

    /** @param retryBufferSize the retry buffer capacity */
    public void setRetryBufferSize(int retryBufferSize) {
        this.retryBufferSize = retryBufferSize;
    }

    /** @return the retry drain interval */
    public Duration getRetryInterval() {
        return retryInterval;
    }

    /** @param retryInterval the retry drain interval */
    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }

    /** @return records per outbox batch */
    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    /** @param outboxBatchSize records per outbox batch (clamped to 1..500 by the shipper) */
    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    /** @return the maximum sealed batch size in bytes */
    public int getOutboxMaxPayloadBytes() {
        return outboxMaxPayloadBytes;
    }

    /** @param outboxMaxPayloadBytes the maximum sealed batch size in bytes */
    public void setOutboxMaxPayloadBytes(int outboxMaxPayloadBytes) {
        this.outboxMaxPayloadBytes = outboxMaxPayloadBytes;
    }

    /** @return connections of the tunnel read pool */
    public int getReadPoolSize() {
        return readPoolSize;
    }

    /** @param readPoolSize connections of the tunnel read pool */
    public void setReadPoolSize(int readPoolSize) {
        this.readPoolSize = readPoolSize;
    }

    /** @return the configured local retention floor in years (may be below 5; see RetentionPolicy) */
    public int getRetentionMinYears() {
        return retentionMinYears;
    }

    /** @param retentionMinYears the local retention floor in years */
    public void setRetentionMinYears(int retentionMinYears) {
        this.retentionMinYears = retentionMinYears;
    }

    /** @return the site-local purge time, {@code HH:mm} */
    public String getPurgeTime() {
        return purgeTime;
    }

    /** @param purgeTime the site-local purge time, {@code HH:mm} */
    public void setPurgeTime(String purgeTime) {
        this.purgeTime = purgeTime;
    }

    /** @return the IANA zone of the site clock, or blank for the host default */
    public String getSiteTimezone() {
        return siteTimezone;
    }

    /** @param siteTimezone the IANA zone of the site clock */
    public void setSiteTimezone(String siteTimezone) {
        this.siteTimezone = siteTimezone;
    }

    /** @return how often the purge scheduler checks for a due run */
    public Duration getPurgeCheckInterval() {
        return purgeCheckInterval;
    }

    /** @param purgeCheckInterval how often the purge scheduler checks for a due run */
    public void setPurgeCheckInterval(Duration purgeCheckInterval) {
        this.purgeCheckInterval = purgeCheckInterval;
    }

    /** @return the longest wait for a partition-drop lock */
    public Duration getPurgeLockTimeout() {
        return purgeLockTimeout;
    }

    /** @param purgeLockTimeout the longest wait for a partition-drop lock */
    public void setPurgeLockTimeout(Duration purgeLockTimeout) {
        this.purgeLockTimeout = purgeLockTimeout;
    }
}
