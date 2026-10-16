package com.posgateway.aml.service.edge.records;

import com.posgateway.aml.entity.edge.EdgeNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the Edge Site of a node (data residency contract §1): the {@code edge_nodes.site_id}
 * registered by the Control Plane, or the node's own {@code edgeId} for a single-node site.
 *
 * <p>The column is introduced by V263 (tunnel server, R4). The resolver checks once whether the
 * column exists, so decision record ingest behaves per contract both before that migration
 * (every node is its own single-node site) and after it (registered sites apply).
 */
@Component
public class EdgeSiteResolver {

    private final JdbcTemplate jdbc;
    private volatile Boolean columnPresent;

    public EdgeSiteResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The node's site id: registered site when set, else the node's edgeId. */
    public String siteOf(EdgeNode node) {
        if (node.getId() != null && hasSiteColumn()) {
            List<String> rows = jdbc.queryForList("SELECT site_id FROM edge_nodes WHERE id = ?", String.class, node.getId());
            if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                return rows.get(0);
            }
        }
        return node.getEdgeId();
    }

    /** Whether edge_nodes.site_id exists in this database (checked once, then cached when present). */
    private boolean hasSiteColumn() {
        Boolean known = columnPresent;
        if (Boolean.TRUE.equals(known)) {
            return true;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'edge_nodes' AND column_name = 'site_id'",
                Integer.class);
        boolean present = count != null && count > 0;
        // Only a positive answer is cached: the column can appear later via migration, never disappear.
        if (present) {
            columnPresent = true;
        }
        return present;
    }
}
