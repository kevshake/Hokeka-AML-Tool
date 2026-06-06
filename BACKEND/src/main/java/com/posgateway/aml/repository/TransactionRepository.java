package com.posgateway.aml.repository;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Transaction Entity
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {

    /**
     * Find transactions by merchant ID
     */
    List<TransactionEntity> findByMerchantId(String merchantId);

    /**
     * Find transactions by PAN hash
     */
    List<TransactionEntity> findByPanHash(String panHash);

    /**
     * Count transactions by merchant in time window
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Long countByMerchantInTimeWindow(@Param("merchantId") String merchantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Sum transaction amounts by merchant in time window
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Long sumAmountByMerchantInTimeWindow(@Param("merchantId") String merchantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Count transactions by PAN in time window
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.panHash = :panHash AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Long countByPanInTimeWindow(@Param("panHash") String panHash,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Sum transaction amounts by PAN in time window
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM TransactionEntity t WHERE t.panHash = :panHash AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Long sumAmountByPanInTimeWindow(@Param("panHash") String panHash,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find distinct terminals for PAN in time window
     */
    @Query("SELECT COUNT(DISTINCT t.terminalId) FROM TransactionEntity t WHERE t.panHash = :panHash AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Long countDistinctTerminalsByPan(@Param("panHash") String panHash,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Get average amount by PAN in time window
     */
    @Query("SELECT COALESCE(AVG(t.amountCents), 0) FROM TransactionEntity t WHERE t.panHash = :panHash AND t.txnTs >= :startTime AND t.txnTs <= :endTime")
    Double avgAmountByPanInTimeWindow(@Param("panHash") String panHash,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find last transaction timestamp for PAN
     */
    @Query("SELECT MAX(t.txnTs) FROM TransactionEntity t WHERE t.panHash = :panHash")
    LocalDateTime findLastTransactionTimeByPan(@Param("panHash") String panHash);

    /**
     * Find distinct merchant IDs by Device Fingerprint
     */
    @Query("SELECT DISTINCT t.merchantId FROM TransactionEntity t WHERE t.deviceFingerprint = :deviceFingerprint")
    List<String> findMerchantIdsByDeviceFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    /**
     * Find distinct merchant IDs by IP Address
     */
    @Query("SELECT DISTINCT t.merchantId FROM TransactionEntity t WHERE t.ipAddress = :ipAddress")
    List<String> findMerchantIdsByIpAddress(@Param("ipAddress") String ipAddress);

    /**
     * Count distinct merchant IDs seen from a given device fingerprint within a time window.
     * Used for device-velocity fraud scoring (many merchants = card-testing signal).
     */
    @Query("SELECT COUNT(DISTINCT t.merchantId) FROM TransactionEntity t " +
           "WHERE t.deviceFingerprint = :deviceFingerprint " +
           "AND t.txnTs >= :since AND t.txnTs <= :until")
    Long countDistinctMerchantsByDeviceSince(@Param("deviceFingerprint") String deviceFingerprint,
                                             @Param("since") LocalDateTime since,
                                             @Param("until") LocalDateTime until);

    /**
     * Count transactions from a given IP address within a time window.
     * Used for IP-velocity fraud scoring (high rate = credential-stuffing / bot signal).
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t " +
           "WHERE t.ipAddress = :ipAddress " +
           "AND t.txnTs >= :since AND t.txnTs <= :until")
    Long countByIpAddressSince(@Param("ipAddress") String ipAddress,
                               @Param("since") LocalDateTime since,
                               @Param("until") LocalDateTime until);

    /**
     * Check whether any FRAUD-severity alert exists for a transaction that used
     * the given device fingerprint.  Returns &gt; 0 when at least one such alert
     * is present, 0 otherwise.
     */
    @Query("SELECT COUNT(DISTINCT a.alertId) FROM Alert a " +
           "JOIN TransactionEntity t ON a.txnId = t.txnId " +
           "WHERE t.deviceFingerprint = :deviceFingerprint " +
           "AND a.severity = 'CRITICAL'")
    Long countFraudAlertsByDeviceFingerprint(@Param("deviceFingerprint") String deviceFingerprint);

    /**
     * Retrieve up to 30 most-recent transactions for a PAN hash, for behavioral
     * baseline calculation (average amount, typical txn type).
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.panHash = :panHash " +
           "ORDER BY t.txnTs DESC")
    List<TransactionEntity> findRecentByPanHash(@Param("panHash") String panHash,
                                                org.springframework.data.domain.Pageable pageable);

    /**
     * Find transactions by merchant ID and timestamp range
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.txnTs >= :startDate AND t.txnTs <= :endDate ORDER BY t.txnTs")
    List<TransactionEntity> findByMerchantIdAndTimestampBetween(@Param("merchantId") String merchantId,
                                                                 @Param("startDate") LocalDateTime startDate,
                                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by merchant ID and timestamp after
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.txnTs >= :startDate ORDER BY t.txnTs")
    List<TransactionEntity> findByMerchantIdAndTimestampAfter(@Param("merchantId") String merchantId,
                                                               @Param("startDate") LocalDateTime startDate);

    /**
     * Find latest transaction timestamp before a date
     */
    @Query("SELECT MAX(t.txnTs) FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.txnTs < :beforeDate")
    LocalDateTime findLatestTransactionTimestampBefore(@Param("merchantId") String merchantId,
                                                       @Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Get daily transaction volume (count) grouped by date, filtered by PSP ID
     * Returns list of Object arrays: [date (LocalDate), count (Long)]
     */
    @Query(value = "SELECT DATE(t.txn_ts) as transaction_date, COUNT(*) as transaction_count " +
            "FROM transactions t " +
            "WHERE t.psp_id = :pspId AND t.txn_ts >= :startDate AND t.txn_ts < :endDate " +
            "GROUP BY DATE(t.txn_ts) " +
            "ORDER BY transaction_date ASC", nativeQuery = true)
    List<Object[]> getDailyTransactionCountByPspId(@Param("pspId") Long pspId,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * Get daily transaction volume (amount sum) grouped by date, filtered by PSP ID
     * Returns list of Object arrays: [date (LocalDate), sum (Long)]
     */
    @Query(value = "SELECT DATE(t.txn_ts) as transaction_date, COALESCE(SUM(t.amount_cents), 0) as total_amount " +
            "FROM transactions t " +
            "WHERE t.psp_id = :pspId AND t.txn_ts >= :startDate AND t.txn_ts < :endDate " +
            "GROUP BY DATE(t.txn_ts) " +
            "ORDER BY transaction_date ASC", nativeQuery = true)
    List<Object[]> getDailyTransactionVolumeByPspId(@Param("pspId") Long pspId,
                                                      @Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Get daily transaction volume (count) grouped by date for all PSPs (admin view)
     */
    @Query(value = "SELECT DATE(t.txn_ts) as transaction_date, COUNT(*) as transaction_count " +
            "FROM transactions t " +
            "WHERE t.txn_ts >= :startDate AND t.txn_ts < :endDate " +
            "GROUP BY DATE(t.txn_ts) " +
            "ORDER BY transaction_date ASC", nativeQuery = true)
    List<Object[]> getDailyTransactionCountAll(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    /**
     * Get daily transaction volume (amount sum) grouped by date for all PSPs (admin view)
     */
    @Query(value = "SELECT DATE(t.txn_ts) as transaction_date, COALESCE(SUM(t.amount_cents), 0) as total_amount " +
            "FROM transactions t " +
            "WHERE t.txn_ts >= :startDate AND t.txn_ts < :endDate " +
            "GROUP BY DATE(t.txn_ts) " +
            "ORDER BY transaction_date ASC", nativeQuery = true)
    List<Object[]> getDailyTransactionVolumeAll(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent transactions filtered by PSP ID
     * Returns transactions ordered by timestamp descending
     */
    List<TransactionEntity> findByPspIdOrderByTxnTsDesc(Long pspId);

    /**
     * Find recent transactions for all PSPs (admin view)
     * Returns transactions ordered by timestamp descending
     */
    List<TransactionEntity> findAllByOrderByTxnTsDesc();

    /**
     * Pageable variants — use these for dashboard to avoid full-table scans.
     * Caller: PageRequest.of(0, limit, Sort.by("txnTs").descending())
     */
    org.springframework.data.domain.Page<TransactionEntity> findByPspId(
            Long pspId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<TransactionEntity> findAll(
            org.springframework.data.domain.Pageable pageable);

    List<TransactionEntity> findByPspIdAndTxnTsBetween(Long pspId, LocalDateTime start, LocalDateTime end);

    /**
     * Find all transactions in a date window regardless of PSP (platform admin view).
     * Replaces findAll() + in-memory filter in regulatory reporting.
     * Indexed on (txn_ts) — used by RegulatoryReportingService instead of findAll() + in-memory filter.
     */
    List<TransactionEntity> findByTxnTsBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Pageable variant for large date-range fetches in regulatory reporting.
     * Caller: PageRequest.of(0, batchSize, Sort.by("txnTs").descending())
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs <= :end ORDER BY t.txnTs DESC")
    org.springframework.data.domain.Page<TransactionEntity> findByPspIdAndTxnTsBetween(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            org.springframework.data.domain.Pageable pageable);

    // -----------------------------------------------------------------------
    // Transaction stats aggregations — used by /transactions/stats endpoint
    // -----------------------------------------------------------------------

    /**
     * Count all transactions in a date window, optionally scoped to a PSP.
     * When pspId is null (platform admin) the PSP filter is skipped.
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t " +
           "WHERE (:pspId IS NULL OR t.pspId = :pspId) " +
           "AND t.txnTs >= :start AND t.txnTs < :end")
    long countByPspAndPeriod(@Param("pspId") Long pspId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * Count transactions filtered by decision in a date window, optionally scoped to a PSP.
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t " +
           "WHERE (:pspId IS NULL OR t.pspId = :pspId) " +
           "AND t.decision = :decision " +
           "AND t.txnTs >= :start AND t.txnTs < :end")
    long countByPspAndDecisionAndPeriod(@Param("pspId") Long pspId,
                                        @Param("decision") String decision,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Count transactions filtered by risk level in a date window, optionally scoped to a PSP.
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t " +
           "WHERE (:pspId IS NULL OR t.pspId = :pspId) " +
           "AND t.riskLevel = :riskLevel " +
           "AND t.txnTs >= :start AND t.txnTs < :end")
    long countByPspAndRiskLevelAndPeriod(@Param("pspId") Long pspId,
                                         @Param("riskLevel") String riskLevel,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /**
     * Sum amount_cents for all transactions in a date window, optionally scoped to a PSP.
     * Returns BigDecimal to avoid long overflow for large volumes.
     */
    @Query("SELECT COALESCE(SUM(t.amountCents), 0) FROM TransactionEntity t " +
           "WHERE (:pspId IS NULL OR t.pspId = :pspId) " +
           "AND t.txnTs >= :start AND t.txnTs < :end")
    BigDecimal sumAmountByPspAndPeriod(@Param("pspId") Long pspId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);


    // -----------------------------------------------------------------------
    // Live monitoring page — indexed top-N queries (replace findAll() + filter)
    // -----------------------------------------------------------------------

    /**
     * Top 10 most-recent transactions for a single PSP — indexed by (psp_id, txn_ts).
     * Replaces in-memory limit() of findByPspIdOrderByTxnTsDesc(pspId).
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.pspId = :pspId AND t.txnTs IS NOT NULL ORDER BY t.txnTs DESC")
    List<TransactionEntity> findTop10ByPspIdOrderByTxnTsDesc(@Param("pspId") Long pspId,
                                                             org.springframework.data.domain.Pageable pageable);

    /**
     * Top 10 most-recent transactions across all PSPs (platform admin view).
     */
    @Query("SELECT t FROM TransactionEntity t WHERE t.txnTs IS NOT NULL ORDER BY t.txnTs DESC")
    List<TransactionEntity> findTop10ByOrderByTxnTsDesc(org.springframework.data.domain.Pageable pageable);

    // -----------------------------------------------------------------------
    // Decision counts (used by live monitoring dashboard stats)
    // -----------------------------------------------------------------------

    /**
     * Count transactions by stored decision since a cutoff (PSP-scoped).
     * Replaces hard-coded fallback values (75/95/25) when TRS is null.
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.pspId = :pspId AND t.decision = :decision AND t.txnTs >= :since")
    long countByPspIdAndDecisionSince(@Param("pspId") Long pspId,
                                      @Param("decision") String decision,
                                      @Param("since") LocalDateTime since);

    /**
     * Count transactions by stored decision since a cutoff (admin view, all PSPs).
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.decision = :decision AND t.txnTs >= :since")
    long countByDecisionSince(@Param("decision") String decision, @Param("since") LocalDateTime since);

    /**
     * Count transactions by stored riskLevel since a cutoff (PSP-scoped).
     * Used for the "flagged"/"highRisk" tiles when TRS is null.
     */
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.pspId = :pspId AND t.riskLevel IN :levels AND t.txnTs >= :since")
    long countByPspIdAndRiskLevelInSince(@Param("pspId") Long pspId,
                                         @Param("levels") List<String> levels,
                                         @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.riskLevel IN :levels AND t.txnTs >= :since")
    long countByRiskLevelInSince(@Param("levels") List<String> levels, @Param("since") LocalDateTime since);

    // -----------------------------------------------------------------------
    // Risk-score distribution buckets — single grouped query, no in-memory bucketing
    // Native query: bucket = floor(trs/10)*10 (covers 0..100 in steps of 10).
    // -----------------------------------------------------------------------

    @Query(value = "SELECT (FLOOR(COALESCE(t.trs, 0)/10)*10)::int AS bucket, COUNT(*) AS cnt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.txn_ts >= :since " +
                   "GROUP BY bucket ORDER BY bucket", nativeQuery = true)
    List<Object[]> getRiskScoreBucketsByPspSince(@Param("pspId") Long pspId,
                                                  @Param("since") LocalDateTime since);

    @Query(value = "SELECT (FLOOR(COALESCE(t.trs, 0)/10)*10)::int AS bucket, COUNT(*) AS cnt " +
                   "FROM transactions t " +
                   "WHERE t.txn_ts >= :since " +
                   "GROUP BY bucket ORDER BY bucket", nativeQuery = true)
    List<Object[]> getRiskScoreBucketsAllSince(@Param("since") LocalDateTime since);

    // -----------------------------------------------------------------------
    // Success-rate stats (LimitsManagementService.avgSuccessRate)
    // Returns Object[] = [approved (Long), total (Long)]. Caller computes %.
    // -----------------------------------------------------------------------

    @Query("SELECT " +
           "  COALESCE(SUM(CASE WHEN t.decision = 'APPROVED' THEN 1 ELSE 0 END), 0), " +
           "  COUNT(t) " +
           "FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :since")
    Object[] getApprovedAndTotalCountByPspSince(@Param("pspId") Long pspId,
                                                @Param("since") LocalDateTime since);

    @Query("SELECT " +
           "  COALESCE(SUM(CASE WHEN t.decision = 'APPROVED' THEN 1 ELSE 0 END), 0), " +
           "  COUNT(t) " +
           "FROM TransactionEntity t " +
           "WHERE t.txnTs >= :since")
    Object[] getApprovedAndTotalCountSince(@Param("since") LocalDateTime since);

    // -----------------------------------------------------------------------
    // CBK GDI aggregations — used by CbkSubmissionOrchestrator
    //
    // The TransactionEntity does NOT carry CBK fields card_brand,
    // bill_classification_code, channel, card_class_type or transaction_type.
    // Sensible substitutes are chosen per query and documented at each call site.
    // -----------------------------------------------------------------------

    /**
     * Endpoint #12 (CARD_BRANDS) — group by direction (placeholder for card brand).
     * Returns rows of [groupKey (String), count (Long), sumAmountCents (Long)].
     */
    @Query(value = "SELECT COALESCE(t.direction, 'UNKNOWN') AS group_key, " +
                   "COUNT(*) AS cnt, COALESCE(SUM(t.amount_cents), 0) AS amt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY COALESCE(t.direction, 'UNKNOWN') " +
                   "ORDER BY group_key", nativeQuery = true)
    List<Object[]> findCardBrandSummaryForPsp(@Param("pspId") Long pspId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * Endpoint #14 (TRANSACTION_DETAILS) — group by direction × decision × merchant_country.
     * Returns rows of [direction, decision, country, count, sumAmountCents].
     */
    @Query(value = "SELECT COALESCE(t.direction, 'UNKNOWN') AS brand, " +
                   "COALESCE(t.decision, 'UNKNOWN') AS txn_type, " +
                   "COALESCE(t.merchant_country, 'XX') AS country, " +
                   "COUNT(*) AS cnt, COALESCE(SUM(t.amount_cents), 0) AS amt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY COALESCE(t.direction, 'UNKNOWN'), COALESCE(t.decision, 'UNKNOWN'), " +
                   "         COALESCE(t.merchant_country, 'XX') " +
                   "ORDER BY brand, txn_type, country", nativeQuery = true)
    List<Object[]> findTransactionMixForPsp(@Param("pspId") Long pspId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /**
     * Endpoint #9 (SYSTEM_ACTIVITY) — TPS+TPH per hour for [start, end].
     * Returns rows of [hour 0-23 (Integer), count (Long)].
     * Caller derives TPS as ceil(count / 3600.0).
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM t.txn_ts)::int AS hour, COUNT(*) AS cnt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY EXTRACT(HOUR FROM t.txn_ts) " +
                   "ORDER BY hour", nativeQuery = true)
    List<Object[]> findHourlyTpsTphForPsp(@Param("pspId") Long pspId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Endpoint #13 (BILLING_TEMPLATE) — group by merchant_country (placeholder for
     * bill_classification_code which is not yet on TransactionEntity).
     * Returns rows of [classificationCode, count, sumAmountCents].
     */
    @Query(value = "SELECT COALESCE(t.merchant_country, 'UNCLASSIFIED') AS bill_class, " +
                   "COUNT(*) AS cnt, COALESCE(SUM(t.amount_cents), 0) AS amt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY COALESCE(t.merchant_country, 'UNCLASSIFIED') " +
                   "ORDER BY bill_class", nativeQuery = true)
    List<Object[]> findBillClassificationSummaryForPsp(@Param("pspId") Long pspId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    /**
     * Endpoint #16 (MERCHANT_TRANSACTIONS) — successful transactions for the
     * window grouped by merchant. Successful = decision='APPROVED'.
     * Returns rows of [merchantId, merchantCountry, count, sumAmountCents].
     */
    @Query(value = "SELECT t.merchant_id AS merchant_id, " +
                   "COALESCE(t.merchant_country, 'XX') AS country, " +
                   "COUNT(*) AS cnt, COALESCE(SUM(t.amount_cents), 0) AS amt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId AND t.decision = 'APPROVED' " +
                   "  AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY t.merchant_id, COALESCE(t.merchant_country, 'XX') " +
                   "ORDER BY merchant_id", nativeQuery = true)
    List<Object[]> findSuccessfulYesterdayByPspId(@Param("pspId") Long pspId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /**
     * Endpoint #17 (FAILED_TRANSACTIONS) — failed/rejected transactions for the
     * window grouped by merchant + decision. TransactionStatus has no FAILED
     * literal — DECLINED/MANUAL_REVIEW are treated as failed/rejected.
     * Returns rows of [merchantId, decision, count, sumAmountCents].
     */
    @Query(value = "SELECT t.merchant_id AS merchant_id, " +
                   "COALESCE(t.decision, 'UNKNOWN') AS decision, " +
                   "COUNT(*) AS cnt, COALESCE(SUM(t.amount_cents), 0) AS amt " +
                   "FROM transactions t " +
                   "WHERE t.psp_id = :pspId " +
                   "  AND t.decision IN ('DECLINED','MANUAL_REVIEW') " +
                   "  AND t.txn_ts >= :start AND t.txn_ts <= :end " +
                   "GROUP BY t.merchant_id, COALESCE(t.decision, 'UNKNOWN') " +
                   "ORDER BY merchant_id, decision", nativeQuery = true)
    List<Object[]> findFailedRejectedForPspByDay(@Param("pspId") Long pspId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
