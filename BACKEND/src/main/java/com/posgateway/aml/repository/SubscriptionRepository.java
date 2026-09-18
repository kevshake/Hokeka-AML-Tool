package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("SELECT s FROM Subscription s WHERE s.psp.pspId = :pspId AND s.status IN ('ACTIVE', 'TRIAL') ORDER BY s.createdAt DESC")
    Optional<Subscription> findActiveByPspId(@Param("pspId") Long pspId);

    @Query("SELECT s FROM Subscription s WHERE s.status IN ('ACTIVE', 'TRIAL')")
    List<Subscription> findAllActive();

    @Query("SELECT s FROM Subscription s WHERE s.psp.pspId = :pspId ORDER BY s.createdAt DESC")
    List<Subscription> findByPspId(@Param("pspId") Long pspId);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.pricingTier.tierCode = :tierCode AND s.status = 'ACTIVE'")
    long countActiveByTierCode(@Param("tierCode") String tierCode);
}
