package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Merchant entity
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long>, JpaSpecificationExecutor<Merchant> {

    /**
     * Find merchant by registration number and country
     */
    Optional<Merchant> findByCountryAndRegistrationNumber(String country, String registrationNumber);

    /**
     * Find merchants needing rescreening (next_screening_due <= today)
     * Excludes already REJECTED or TERMINATED merchants
     */
    @Query("SELECT m FROM Merchant m WHERE m.status NOT IN ('REJECTED', 'TERMINATED') " +
            "AND (m.nextScreeningDue IS NULL OR m.nextScreeningDue <= :today)")
    List<Merchant> findMerchantsNeedingRescreening(@Param("today") LocalDate today);

    /**
     * Find merchants by status
     */
    List<Merchant> findByStatus(String status);

    /**
     * Find merchants by country
     */
    List<Merchant> findByCountry(String country);

    /**
     * Count active merchants
     */
    long countByStatus(String status);

    /**
     * Find by PSP ID
     */
    List<Merchant> findByPspPspId(Long pspId);

    /**
     * Find by PSP ID and Status
     */
    List<Merchant> findByPspPspIdAndStatus(Long pspId, String status);

    long countByPspPspId(Long pspId);

    long countByPspPspIdAndStatus(Long pspId, String status);

    /**
     * Count merchants by risk level
     */
    long countByRiskLevel(String riskLevel);

    /**
     * Count merchants by PSP ID and risk level
     */
    long countByPspPspIdAndRiskLevel(Long pspId, String riskLevel);

    /**
     * Find merchants by MCC and country
     */
    List<Merchant> findByMccAndCountry(String mcc, String country);

    /**
     * Batch status counts for a PSP — returns [{status, count}] rows.
     * Replaces N separate countByPspPspIdAndStatus calls with one GROUP BY query.
     */
    @Query(value = "SELECT status, COUNT(*) FROM merchants WHERE psp_id = :pspId GROUP BY status",
           nativeQuery = true)
    List<Object[]> countByPspIdGroupByStatus(@Param("pspId") Long pspId);

    /**
     * Batch risk-level counts for a PSP — returns [{risk_level, count}] rows.
     */
    @Query(value = "SELECT risk_level, COUNT(*) FROM merchants WHERE psp_id = :pspId GROUP BY risk_level",
           nativeQuery = true)
    List<Object[]> countByPspIdGroupByRiskLevel(@Param("pspId") Long pspId);

    /**
     * Global batch status counts (admin view).
     */
    @Query(value = "SELECT status, COUNT(*) FROM merchants GROUP BY status", nativeQuery = true)
    List<Object[]> countAllGroupByStatus();

    /**
     * Global batch risk-level counts (admin view).
     */
    @Query(value = "SELECT risk_level, COUNT(*) FROM merchants GROUP BY risk_level", nativeQuery = true)
    List<Object[]> countAllGroupByRiskLevel();
}
