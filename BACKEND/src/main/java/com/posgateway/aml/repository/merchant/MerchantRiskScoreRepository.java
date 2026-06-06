package com.posgateway.aml.repository.merchant;

import com.posgateway.aml.entity.merchant.MerchantRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantRiskScoreRepository extends JpaRepository<MerchantRiskScore, Long> {

    @Query("SELECT s FROM MerchantRiskScore s WHERE s.merchant.merchantId = :merchantId ORDER BY s.calculatedAt DESC")
    List<MerchantRiskScore> findHistoryByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT s FROM MerchantRiskScore s WHERE s.merchant.merchantId = :merchantId ORDER BY s.calculatedAt DESC LIMIT 1")
    Optional<MerchantRiskScore> findLatestByMerchantId(@Param("merchantId") Long merchantId);
}
