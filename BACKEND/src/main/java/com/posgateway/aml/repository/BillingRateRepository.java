package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.BillingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Billing Rate Repository
 */
@Repository
public interface BillingRateRepository extends JpaRepository<BillingRate, Long> {

    List<BillingRate> findByServiceType(String serviceType);

    List<BillingRate> findByPsp_PspId(Long pspId);

    @Query("SELECT r FROM BillingRate r WHERE r.psp.pspId = :pspId " +
            "AND r.serviceType = :serviceType " +
            "AND r.isActive = true " +
            "AND r.effectiveFrom <= :date " +
            "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)")
    Optional<BillingRate> findActiveRateForPsp(
            @Param("pspId") Long pspId,
            @Param("serviceType") String serviceType,
            @Param("date") LocalDate date);

    @Query("SELECT r FROM BillingRate r WHERE r.psp IS NULL " +
            "AND r.serviceType = :serviceType " +
            "AND r.isActive = true " +
            "AND r.effectiveFrom <= :date " +
            "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)")
    Optional<BillingRate> findDefaultRate(
            @Param("serviceType") String serviceType,
            @Param("date") LocalDate date);

    List<BillingRate> findByIsActive(Boolean isActive);
}
