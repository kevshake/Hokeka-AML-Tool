package com.posgateway.aml.repository.edge;

import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code edge_data_access_log} (§7.15). Rows are written by {@code EdgeDataAccessAuditor} only;
 * no caller deletes them (the table is "never deletable by PSP or platform users").
 */
@Repository
public interface EdgeDataAccessLogRepository extends JpaRepository<EdgeDataAccessLog, Long>,
        JpaSpecificationExecutor<EdgeDataAccessLog> {

    /** The row of one tunnel request, by the audit id it shares with the Edge row. */
    Optional<EdgeDataAccessLog> findByAuditId(UUID auditId);

    /** Tenant-scoped listing for {@code GET /api/v1/edge-data/access-log} (newest first). */
    Page<EdgeDataAccessLog> findByPspIdOrderByRequestedAtDesc(Long pspId, Pageable pageable);
}
