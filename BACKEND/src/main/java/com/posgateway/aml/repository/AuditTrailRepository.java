package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Audit Trail
 * READ-ONLY - No updates or deletes allowed
 */
@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    /**
     * Find audit logs by merchant ID
     */
    List<AuditTrail> findByMerchantIdOrderByPerformedAtDesc(Long merchantId);

    /**
     * Find audit logs by action
     */
    List<AuditTrail> findByAction(String action);

    /**
     * Find audit logs by performed by
     */
    List<AuditTrail> findByPerformedBy(String performedBy);

    /**
     * Find audit logs by timestamp range
     */
    List<AuditTrail> findByPerformedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
