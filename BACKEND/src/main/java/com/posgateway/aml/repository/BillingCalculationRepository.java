package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.BillingCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingCalculationRepository extends JpaRepository<BillingCalculation, Long> {

    @Query("SELECT bc FROM BillingCalculation bc WHERE bc.pspId = :pspId ORDER BY bc.periodStart DESC")
    List<BillingCalculation> findByPspId(@Param("pspId") Long pspId);

    @Query("SELECT bc FROM BillingCalculation bc WHERE bc.pspId = :pspId AND bc.periodStart = :periodStart")
    Optional<BillingCalculation> findByPspIdAndPeriod(@Param("pspId") Long pspId,
            @Param("periodStart") LocalDate periodStart);

    @Query("SELECT bc FROM BillingCalculation bc WHERE bc.periodStart = :periodStart ORDER BY bc.pspId")
    List<BillingCalculation> findByPeriodStart(@Param("periodStart") LocalDate periodStart);
}
