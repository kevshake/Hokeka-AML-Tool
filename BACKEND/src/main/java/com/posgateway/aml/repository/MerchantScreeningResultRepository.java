package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    List<MerchantScreeningResult> findByScreenedAtBetween(@Param("startDate") java.time.LocalDateTime startDate,
                                                           @Param("endDate") java.time.LocalDateTime endDate);
}
