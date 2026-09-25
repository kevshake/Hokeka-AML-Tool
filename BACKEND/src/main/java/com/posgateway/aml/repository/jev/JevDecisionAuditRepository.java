package com.posgateway.aml.repository.jev;

import com.posgateway.aml.entity.jev.JevDecisionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JevDecisionAuditRepository extends JpaRepository<JevDecisionAudit, Long> {

    List<JevDecisionAudit> findByTransactionIdOrderByCreatedAtDesc(Long transactionId);

    List<JevDecisionAudit> findByAlertIdOrderByCreatedAtDesc(Long alertId);

    List<JevDecisionAudit> findByCaseIdOrderByCreatedAtDesc(Long caseId);

    List<JevDecisionAudit> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    Page<JevDecisionAudit> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);

    @Query("""
            SELECT AVG(a.latencyMs) FROM JevDecisionAudit a
            WHERE a.createdAt >= :since AND a.latencyMs IS NOT NULL
            """)
    Optional<Double> averageLatencySince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(a) FROM JevDecisionAudit a
            WHERE a.createdAt >= :since AND a.fallbackReason IS NOT NULL
            """)
    long countFallbacksSince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(a) FROM JevDecisionAudit a
            WHERE a.createdAt >= :since
            """)
    long countCallsSince(@Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(a.estimatedCostUsd), 0) FROM JevDecisionAudit a
            WHERE a.createdAt >= :since
            """)
    Optional<java.math.BigDecimal> totalSpendSince(@Param("since") Instant since);
}
