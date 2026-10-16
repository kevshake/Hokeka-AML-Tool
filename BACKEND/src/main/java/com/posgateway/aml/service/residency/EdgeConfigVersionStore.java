package com.posgateway.aml.service.residency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Per-PSP Edge config version history ({@code edge_config_versions}, V261). A version is minted
 * whenever the canonical content hash of the PSP's config document differs from the latest issued
 * one, so {@code configVersion} is strictly increasing and changes on every input change
 * (contract 6.2), including time-driven ones such as a pending reduction coming into force.
 */
@Component
public class EdgeConfigVersionStore {

    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;

    public EdgeConfigVersionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param version the configVersion for this content
     * @param minted  true when this call issued a new version (the content changed)
     */
    public record VersionResult(long version, boolean minted) {}

    /**
     * Returns the version for {@code contentHash}, minting the next one when the content differs from
     * the latest. Concurrent callers are serialised by the (psp_id, config_version) unique key: the
     * loser of an insert race re-reads and either finds its hash or mints the following version.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VersionResult ensureVersion(long pspId, String contentHash) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<Object[]> latest = jdbc.query(
                    "SELECT config_version, content_hash FROM edge_config_versions "
                            + "WHERE psp_id = ? ORDER BY config_version DESC LIMIT 1",
                    (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2)},
                    pspId);
            if (!latest.isEmpty() && contentHash.equals(latest.get(0)[1])) {
                return new VersionResult((Long) latest.get(0)[0], false);
            }
            long next = latest.isEmpty() ? 1L : (Long) latest.get(0)[0] + 1L;
            int inserted = jdbc.update(
                    "INSERT INTO edge_config_versions (psp_id, config_version, content_hash, created_at) "
                            + "VALUES (?, ?, ?, NOW()) ON CONFLICT (psp_id, config_version) DO NOTHING",
                    pspId, next, contentHash);
            if (inserted == 1) {
                return new VersionResult(next, true);
            }
        }
        throw new IllegalStateException("could not issue an Edge config version for PSP " + pspId
                + " after " + MAX_ATTEMPTS + " attempts");
    }

    /** Latest issued version of a PSP, or 0 when none has been issued yet. */
    public long latestVersion(long pspId) {
        Long version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(config_version), 0) FROM edge_config_versions WHERE psp_id = ?",
                Long.class, pspId);
        return version == null ? 0L : version;
    }
}
