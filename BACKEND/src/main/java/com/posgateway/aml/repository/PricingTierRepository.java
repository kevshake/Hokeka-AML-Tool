package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingTierRepository extends JpaRepository<PricingTier, Integer> {

    Optional<PricingTier> findByTierCode(String tierCode);

    @Query("SELECT pt FROM PricingTier pt WHERE pt.isActive = true ORDER BY pt.monthlyFeeUsd ASC")
    List<PricingTier> findAllActive();

    @Query("SELECT pt FROM PricingTier pt WHERE pt.isActive = true AND pt.tierCode != 'FREE' ORDER BY pt.monthlyFeeUsd ASC")
    List<PricingTier> findAllPaidTiers();
}
