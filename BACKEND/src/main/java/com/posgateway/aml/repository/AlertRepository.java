package com.posgateway.aml.repository;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.model.AlertDisposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Alerts
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    /**
     * Find alerts by status
     */
    List<Alert> findByStatus(String status);

    /**
     * Find open alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.status = 'open' ORDER BY a.createdAt DESC")
    List<Alert> findOpenAlerts();

    /**
     * Find alerts by transaction ID
     */
    List<Alert> findByTxnId(Long txnId);

    /**
     * Count alerts by status
     */
    Long countByStatus(String status);

    /**
     * Count alerts for a merchant created on/after a given timestamp.
     * Used by Customer Risk Assessment to weight historical alert volume.
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.merchantId = :merchantId AND a.createdAt >= :since")
    long countByMerchantIdSince(@Param("merchantId") Long merchantId, @Param("since") LocalDateTime since);

    /**
     * Find alerts created in time range
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt >= :startTime AND a.createdAt <= :endTime")
    List<Alert> findAlertsInTimeRange(@Param("startTime") LocalDateTime startTime, 
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * Find unassigned alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.investigator IS NULL OR a.investigator = ''")
    List<Alert> findByAssignedToIsNull();

    /**
     * Find alerts created after a date
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt >= :afterDate")
    List<Alert> findByCreatedAtAfter(@Param("afterDate") LocalDateTime afterDate);

    /**
     * Find alerts created between dates
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate")
    List<Alert> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent open alerts filtered by PSP ID (through merchant)
     * Returns alerts for merchants belonging to the specified PSP
     */
    @Query(value = "SELECT DISTINCT a.* FROM alerts a " +
            "INNER JOIN merchants m ON a.merchant_id = m.merchant_id " +
            "WHERE m.psp_id = :pspId AND a.status = 'open' " +
            "ORDER BY a.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Alert> findRecentOpenAlertsByPspId(@Param("pspId") Long pspId, @Param("limit") int limit);

    /**
     * Find alerts by status and PSP ID (through merchant)
     * Returns alerts for merchants belonging to the specified PSP with the given status
     */
    @Query(value = "SELECT DISTINCT a.* FROM alerts a " +
            "INNER JOIN merchants m ON a.merchant_id = m.merchant_id " +
            "WHERE m.psp_id = :pspId AND a.status = :status " +
            "ORDER BY a.created_at DESC", nativeQuery = true)
    List<Alert> findByStatusAndPspId(@Param("status") String status, @Param("pspId") Long pspId);

    /**
     * Find all alerts filtered by PSP ID (through merchant)
     * Returns all alerts for merchants belonging to the specified PSP
     */
    @Query(value = "SELECT DISTINCT a.* FROM alerts a " +
            "INNER JOIN merchants m ON a.merchant_id = m.merchant_id " +
            "WHERE m.psp_id = :pspId " +
            "ORDER BY a.created_at DESC", nativeQuery = true)
    List<Alert> findAllByPspId(@Param("pspId") Long pspId);

    /**
     * Find recent open alerts for all PSPs (admin view)
     * Limited to most recent alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.status = 'open' ORDER BY a.createdAt DESC")
    List<Alert> findRecentOpenAlerts();

    /**
     * Count alerts that have been disposed with any of the specified dispositions
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.disposition IN :dispositions")
    long countByDispositionIn(@Param("dispositions") List<AlertDisposition> dispositions);

    /**
     * Count all alerts that have a non-null disposition (i.e. have been reviewed)
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.disposition IS NOT NULL")
    long countReviewedAlerts();

    /**
     * Count CRITICAL alerts created in a date window, optionally scoped to a PSP via txn join.
     * Used by the /transactions/stats endpoint for fraudAlertCount.
     */
    @Query(value = "SELECT COUNT(DISTINCT a.alert_id) FROM alerts a " +
                   "INNER JOIN transactions t ON a.txn_id = t.txn_id " +
                   "WHERE (:pspId IS NULL OR t.psp_id = :pspId) " +
                   "AND a.severity = 'CRITICAL' " +
                   "AND a.created_at >= :start AND a.created_at < :end",
           nativeQuery = true)
    long countFraudAlertsByPspAndPeriod(@Param("pspId") Long pspId,
                                        @Param("start") java.time.LocalDateTime start,
                                        @Param("end") java.time.LocalDateTime end);

    // -----------------------------------------------------------------------
    // Dashboard alert-trend / count-in-window aggregates
    // -----------------------------------------------------------------------

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt >= :start AND a.createdAt < :end")
    long countCreatedInPeriod(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Query(value = "SELECT COUNT(DISTINCT a.alert_id) FROM alerts a " +
                   "JOIN merchants m ON m.merchant_id = a.merchant_id " +
                   "WHERE m.psp_id = :pspId AND a.created_at >= :start AND a.created_at < :end",
           nativeQuery = true)
    long countCreatedInPeriodByPsp(@Param("pspId") Long pspId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /** Daily alert counts for the trend chart. Returns [date, count]. */
    @Query(value = "SELECT DATE(a.created_at) AS d, COUNT(*) AS cnt " +
                   "FROM alerts a " +
                   "WHERE a.created_at >= :start AND a.created_at < :end " +
                   "GROUP BY DATE(a.created_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyAlertCounts(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE(a.created_at) AS d, COUNT(DISTINCT a.alert_id) AS cnt " +
                   "FROM alerts a " +
                   "JOIN merchants m ON m.merchant_id = a.merchant_id " +
                   "WHERE m.psp_id = :pspId " +
                   "  AND a.created_at >= :start AND a.created_at < :end " +
                   "GROUP BY DATE(a.created_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyAlertCountsByPsp(@Param("pspId") Long pspId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);
}

