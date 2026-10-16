package com.posgateway.aml.repository.ai;

import com.posgateway.aml.entity.ai.AiDecisionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiDecisionAuditRepository extends JpaRepository<AiDecisionAudit, Long> {

    List<AiDecisionAudit> findByTransactionIdOrderByCreatedAtDesc(Long transactionId);

    List<AiDecisionAudit> findByAlertIdOrderByCreatedAtDesc(Long alertId);

    List<AiDecisionAudit> findByCaseIdOrderByCreatedAtDesc(Long caseId);

    List<AiDecisionAudit> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    List<AiDecisionAudit> findByMerchantIdAndEngineCodeOrderByCreatedAtDesc(
            Long merchantId, String engineCode);

    List<AiDecisionAudit> findByScreeningHitIdOrderByCreatedAtDesc(String screeningHitId);

    Page<AiDecisionAudit> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);

    @Query("""
            SELECT AVG(a.latencyMs) FROM AiDecisionAudit a
            WHERE a.createdAt >= :since AND a.latencyMs IS NOT NULL
            """)
    Optional<Double> averageLatencySince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(a) FROM AiDecisionAudit a
            WHERE a.createdAt >= :since AND a.fallbackReason IS NOT NULL
            """)
    long countFallbacksSince(@Param("since") Instant since);

    @Query("""
            SELECT COUNT(a) FROM AiDecisionAudit a
            WHERE a.createdAt >= :since
            """)
    long countCallsSince(@Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(a.estimatedCostUsd), 0) FROM AiDecisionAudit a
            WHERE a.createdAt >= :since
            """)
    Optional<java.math.BigDecimal> totalSpendSince(@Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(a.inputTokens), 0) FROM AiDecisionAudit a
            WHERE a.createdAt >= :since
            """)
    Optional<Long> totalInputTokensSince(@Param("since") Instant since);
}
