package com.posgateway.aml.repository.rules;

import com.posgateway.aml.entity.rules.RuleExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for {@link RuleExecutionLog}. Provides indexed aggregation
 * queries used by {@code RuleEffectivenessService} to compute the metrics
 * surfaced by GET /rules/{id}/effectiveness.
 */
@Repository
public interface RuleExecutionLogRepository extends JpaRepository<RuleExecutionLog, Long> {

    long countByRuleId(Long ruleId);

    long countByRuleIdAndDisposition(Long ruleId, String disposition);

    @Query("SELECT AVG(l.executionTimeMicros) FROM RuleExecutionLog l WHERE l.ruleId = :ruleId AND l.executionTimeMicros IS NOT NULL")
    Double averageExecutionTimeForRule(@Param("ruleId") Long ruleId);

    @Query("SELECT MAX(l.executedAt) FROM RuleExecutionLog l WHERE l.ruleId = :ruleId")
    Optional<Instant> findLastExecutedFor(@Param("ruleId") Long ruleId);

    /**
     * Back-fill disposition on every MATCH row for a transaction once the
     * resulting alert is dispositioned. Bulk update — invoked from
     * AlertDispositionService / AlertController#resolveAlert.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RuleExecutionLog l SET l.disposition = :disposition " +
           "WHERE l.txnId = :txnId AND l.result = com.posgateway.aml.entity.rules.RuleExecutionLog.Result.MATCH")
    int updateDispositionForMatches(@Param("txnId") String txnId,
                                    @Param("disposition") String disposition);
}
