package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Merchant Screening Results
 */
@Repository
public interface MerchantScreeningResultRepository extends JpaRepository<MerchantScreeningResult, Long> {

    /**
     * Find screening results by merchant ID
     */
    List<MerchantScreeningResult> findByMerchant_MerchantIdOrderByScreenedAtDesc(Long merchantId);

    /**
     * Find latest screening result for merchant
     */
    @Query("SELECT msr FROM MerchantScreeningResult msr WHERE msr.merchant.merchantId = :merchantId " +
            "ORDER BY msr.screenedAt DESC LIMIT 1")
    Optional<MerchantScreeningResult> findLatestByMerchantId(@Param("merchantId") Long merchantId);

    /**
     * Find screening results by status
     */
    List<MerchantScreeningResult> findByScreeningStatus(String status);

    /**
     * Find screening results with matches
     */
    @Query("SELECT msr FROM MerchantScreeningResult msr WHERE msr.matchCount > 0")
    List<MerchantScreeningResult> findAllWithMatches();

    /**
     * Find screening results by screened date range
     */
    @Query("SELECT msr FROM MerchantScreeningResult msr WHERE msr.screenedAt >= :startDate AND msr.screenedAt <= :endDate")
    List<MerchantScreeningResult> findByScreenedAtBetween(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find the single most recent screening result across all merchants
     */
    @Query("SELECT msr FROM MerchantScreeningResult msr ORDER BY msr.screenedAt DESC LIMIT 1")
    Optional<MerchantScreeningResult> findLatestScreeningResult();

    /**
     * Count screening records screened after a given timestamp (all statuses)
     */
    @Query("SELECT COUNT(msr) FROM MerchantScreeningResult msr WHERE msr.screenedAt >= :since")
    long countScreenedSince(@Param("since") LocalDateTime since);

    /**
     * Count screening records with a specific status screened after a given timestamp
     */
    @Query("SELECT COUNT(msr) FROM MerchantScreeningResult msr WHERE msr.screeningStatus = :status AND msr.screenedAt >= :since")
    long countByScreeningStatusAndScreenedAtAfter(@Param("status") String status, @Param("since") LocalDateTime since);

    /**
     * Per-hit-list-type counts for today's screening results. The
     * MerchantScreeningResult entity stores the matched list type as
     * {@code match_details->>'hitListType'} (JSONB). We group on that key
     * and only count rows whose screening_status is a positive match.
     *
     * Returns rows of [hit_list_type (TEXT, may be NULL), count (BIGINT)].
     */
    @Query(value = "SELECT COALESCE(msr.match_details->>'hitListType', 'UNKNOWN') AS hit_list_type, " +
                   "       COUNT(*) AS cnt " +
                   "FROM merchant_screening_results msr " +
                   "WHERE msr.screened_at >= :since " +
                   "  AND msr.screening_status IN ('MATCH','POTENTIAL_MATCH') " +
                   "GROUP BY COALESCE(msr.match_details->>'hitListType', 'UNKNOWN')",
           nativeQuery = true)
    List<Object[]> countTodayByHitListType(@Param("since") LocalDateTime since);

    /**
     * Daily watchlist/sanctions match counts for KPI sparklines.
     * Returns rows of [date (java.sql.Date), count (Long)].
     */
    @Query(value = "SELECT DATE(msr.screened_at) AS d, COUNT(*) AS cnt " +
                   "FROM merchant_screening_results msr " +
                   "WHERE msr.screened_at >= :start AND msr.screened_at < :end " +
                   "  AND msr.screening_status IN ('MATCH','POTENTIAL_MATCH') " +
                   "GROUP BY DATE(msr.screened_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyMatchCounts(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);
}
