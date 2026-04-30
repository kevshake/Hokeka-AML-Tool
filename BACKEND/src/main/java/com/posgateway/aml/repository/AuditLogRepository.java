package com.posgateway.aml.repository;

import com.posgateway.aml.entity.AuditLog;
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

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    List<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

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
