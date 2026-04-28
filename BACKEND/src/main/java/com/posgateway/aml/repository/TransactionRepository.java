package com.posgateway.aml.repository;

import com.posgateway.aml.entity.TransactionEntity;
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
}
