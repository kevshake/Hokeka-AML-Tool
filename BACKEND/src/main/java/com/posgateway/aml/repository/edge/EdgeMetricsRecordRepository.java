package com.posgateway.aml.repository.edge;

import com.posgateway.aml.entity.edge.EdgeMetricsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EdgeMetricsRecordRepository extends JpaRepository<EdgeMetricsRecord, Long> {

    List<EdgeMetricsRecord> findTop20ByEdgeNodeIdOrderByWindowEndDesc(Long edgeNodeId);

    List<EdgeMetricsRecord> findTop50ByPspIdOrderByWindowEndDesc(Long pspId);

    /**
     * Fleet rollup of the aggregate counts pushed by the on-prem engines, for one PSP over a window.
     * Evaluation happens on customer premises; these summed counters are how the control plane sees
     * what the fleet actually did. Returns a single row:
     * {@code [nodes, totalEvaluated, allowed, alerted, held, blocked, avgP95Micros]}.
     */
    @Query("SELECT COUNT(DISTINCT m.edgeNodeId), COALESCE(SUM(m.totalEvaluated), 0), "
            + "COALESCE(SUM(m.allowed), 0), COALESCE(SUM(m.alerted), 0), "
            + "COALESCE(SUM(m.held), 0), COALESCE(SUM(m.blocked), 0), "
            + "COALESCE(AVG(m.p95LatencyMicros), 0) "
            + "FROM EdgeMetricsRecord m WHERE m.pspId = :pspId "
            + "AND m.windowStart >= :from AND m.windowStart < :to")
    Object[] summarizeForPsp(@Param("pspId") Long pspId,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    /**
     * Same rollup across every PSP — the platform-wide fleet view. Grouped by PSP so the operator can
     * see which tenants' on-prem engines are carrying load. Rows:
     * {@code [pspId, nodes, totalEvaluated, allowed, alerted, held, blocked]}.
     */
    @Query("SELECT m.pspId, COUNT(DISTINCT m.edgeNodeId), COALESCE(SUM(m.totalEvaluated), 0), "
            + "COALESCE(SUM(m.allowed), 0), COALESCE(SUM(m.alerted), 0), "
            + "COALESCE(SUM(m.held), 0), COALESCE(SUM(m.blocked), 0) "
            + "FROM EdgeMetricsRecord m WHERE m.windowStart >= :from AND m.windowStart < :to "
            + "GROUP BY m.pspId ORDER BY SUM(m.totalEvaluated) DESC")
    List<Object[]> summarizeAllPsps(@Param("from") Instant from, @Param("to") Instant to);
}
