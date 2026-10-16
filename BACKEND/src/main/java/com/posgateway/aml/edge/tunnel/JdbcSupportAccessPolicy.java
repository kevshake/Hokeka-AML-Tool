package com.posgateway.aml.edge.tunnel;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * {@link SupportAccessPolicy} reading {@code psps.support_access_enabled} (§9; column added by
 * work package R3 in migration V261, default {@code false}). Read on every platform pull, never
 * cached, so switching access off takes effect for the next request immediately.
 *
 * <p>No row or a {@code NULL} value means disabled (the contract default). Database errors are not
 * swallowed: they propagate, and the gateway answers {@code INTERNAL} without contacting the Edge,
 * so a failure can never open access.
 */
public class JdbcSupportAccessPolicy implements SupportAccessPolicy {

    static final String SQL = "SELECT support_access_enabled FROM psps WHERE psp_id = ?";

    private final JdbcTemplate jdbc;

    /** Uses the application's primary data source. */
    public JdbcSupportAccessPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Looks up the flag; absent row or NULL is {@code false}. */
    @Override
    public boolean isSupportAccessEnabled(long pspId) {
        List<Boolean> rows = jdbc.query(SQL, (rs, i) -> {
            boolean v = rs.getBoolean(1);
            return rs.wasNull() ? Boolean.FALSE : v;
        }, pspId);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }
}
