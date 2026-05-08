package com.posgateway.aml.repository;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.cbk.projection.BillingClassificationAggRow;
import com.posgateway.aml.service.cbk.projection.CardBrandAggRow;
import com.posgateway.aml.service.cbk.projection.FailedTransactionAggRow;
import com.posgateway.aml.service.cbk.projection.HourlyActivityAggRow;
import com.posgateway.aml.service.cbk.projection.MerchantSettlementAggRow;
import com.posgateway.aml.service.cbk.projection.TransactionDetailAggRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    // CBK transaction-aggregate queries (date-windowed, PSP-scoped)
    // -----------------------------------------------------------------------

    /**
     * CARD_BRANDS — aggregate count and value by card_brand for a PSP in window.
     * Monthly window (previous calendar month).
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.CardBrandAggRow(" +
           "  t.cardBrand, COUNT(t), COALESCE(SUM(t.amountCents), 0L)" +
           ") FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "GROUP BY t.cardBrand")
    List<CardBrandAggRow> aggregateCardBrandsByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * TRANSACTION_DETAILS — aggregate by (card_brand, card_type, card_class, channel_type).
     * Monthly window (previous calendar month).
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.TransactionDetailAggRow(" +
           "  t.cardBrand, t.cardType, t.cardClass, t.channelType," +
           "  COUNT(t), COALESCE(SUM(t.amountCents), 0L)" +
           ") FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "GROUP BY t.cardBrand, t.cardType, t.cardClass, t.channelType")
    List<TransactionDetailAggRow> aggregateTransactionDetailsByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * BILLING_TEMPLATE — aggregate count and value by bill_classification_code.
     * Daily window (yesterday).
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.BillingClassificationAggRow(" +
           "  t.billClassificationCode, COUNT(t), COALESCE(SUM(t.amountCents), 0L)" +
           ") FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "GROUP BY t.billClassificationCode")
    List<BillingClassificationAggRow> aggregateBillingClassificationByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * SYSTEM_ACTIVITY — transaction count per hour-of-day.
     * Daily window (yesterday). Returns up to 24 rows; mapper pads missing hours.
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.HourlyActivityAggRow(" +
           "  FUNCTION('hour', t.txnTs), COUNT(t)" +
           ") FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "GROUP BY FUNCTION('hour', t.txnTs) " +
           "ORDER BY FUNCTION('hour', t.txnTs)")
    List<HourlyActivityAggRow> aggregateHourlyActivityByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * MERCHANT_TRANSACTIONS — aggregate approved transactions per merchant,
     * joined to Merchant for country, email, mcc.
     * Daily window (yesterday), only APPROVED decisions.
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.MerchantSettlementAggRow(" +
           "  t.merchantId, m.country, m.contactEmail, m.mcc," +
           "  COUNT(t), COALESCE(SUM(t.amountCents), 0L)" +
           ") FROM TransactionEntity t " +
           "JOIN com.posgateway.aml.entity.merchant.Merchant m ON m.merchantId = CAST(t.merchantId AS long) " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "AND t.decision = 'APPROVED' " +
           "GROUP BY t.merchantId, m.country, m.contactEmail, m.mcc")
    List<MerchantSettlementAggRow> aggregateMerchantSettlementByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * FAILED_TRANSACTIONS — aggregate declined/rejected transactions per
     * (merchant_id, acquirer_response).
     * Daily window (yesterday), decisions DECLINED or MANUAL_REVIEW.
     */
    @Query("SELECT new com.posgateway.aml.service.cbk.projection.FailedTransactionAggRow(" +
           "  t.merchantId, t.acquirerResponse, COUNT(t), COALESCE(SUM(t.amountCents), 0L)" +
           ") FROM TransactionEntity t " +
           "WHERE t.pspId = :pspId AND t.txnTs >= :start AND t.txnTs < :end " +
           "AND t.decision IN ('DECLINED', 'MANUAL_REVIEW') " +
           "GROUP BY t.merchantId, t.acquirerResponse")
    List<FailedTransactionAggRow> aggregateFailedTransactionsByPspAndWindow(
            @Param("pspId") Long pspId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
