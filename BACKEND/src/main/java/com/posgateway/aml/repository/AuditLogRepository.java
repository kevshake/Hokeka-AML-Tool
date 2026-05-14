package com.posgateway.aml.repository;

import com.posgateway.aml.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Audit Logs
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // Allows dynamic filtering for audit log search UI
    // (e.g. username, actionType, entityType, time range, success, IP, session)


    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    Page<AuditLog> findByUsername(String username, Pageable pageable);

    List<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    long countByTimestampAfter(LocalDateTime timestamp);

    /**
     * Find recent log by user and action type
     */
    List<AuditLog> findTop1ByUserIdAndActionTypeOrderByTimestampDesc(String userId, String actionType);

    /**
     * Delete logs before a timestamp (for retention policy)
     */
    long deleteByTimestampBefore(LocalDateTime timestamp);
}
