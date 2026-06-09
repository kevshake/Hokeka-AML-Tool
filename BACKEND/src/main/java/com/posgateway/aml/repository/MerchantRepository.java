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

    Optional<Merchant> findByMerchantId(Long merchantId);

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

    // -----------------------------------------------------------------------
    // Dashboard aggregates (DashboardController)
    // -----------------------------------------------------------------------

    /** Total merchants count (used as denominator for compliance health %). */
    @Query("SELECT COUNT(m) FROM Merchant m")
    long countAllMerchants();

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.psp.pspId = :pspId")
    long countAllMerchantsByPsp(@Param("pspId") Long pspId);

    /** Merchants with kyc_status = 'APPROVED' (numerator for KYC completion). */
    long countByKycStatus(String kycStatus);

    long countByPspPspIdAndKycStatus(Long pspId, String kycStatus);

    /** CDD review numerator: merchants whose last_cdd_review_at >= since. */
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.lastCddReviewAt >= :since")
    long countWithCddReviewSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.psp.pspId = :pspId AND m.lastCddReviewAt >= :since")
    long countWithCddReviewSinceByPsp(@Param("pspId") Long pspId,
                                      @Param("since") java.time.LocalDateTime since);

    /** EDD review denominator: HIGH-risk merchants. */
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.riskLevel = 'HIGH'")
    long countHighRiskMerchants();

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.psp.pspId = :pspId AND m.riskLevel = 'HIGH'")
    long countHighRiskMerchantsByPsp(@Param("pspId") Long pspId);

    /** EDD review numerator: HIGH-risk merchants whose last_edd_review_at >= since. */
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.riskLevel = 'HIGH' AND m.lastEddReviewAt >= :since")
    long countHighRiskWithEddReviewSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.psp.pspId = :pspId AND m.riskLevel = 'HIGH' AND m.lastEddReviewAt >= :since")
    long countHighRiskWithEddReviewSinceByPsp(@Param("pspId") Long pspId,
                                              @Param("since") java.time.LocalDateTime since);

    /**
     * Daily high-risk merchant activity for KPI sparklines — merchants
     * currently HIGH/CRITICAL whose record was updated on that day.
     * Returns rows of [date (java.sql.Date), count (Long)].
     */
    @Query(value = "SELECT DATE(m.updated_at) AS d, COUNT(*) AS cnt " +
                   "FROM merchants m " +
                   "WHERE m.updated_at >= :start AND m.updated_at < :end " +
                   "  AND m.risk_level IN ('HIGH', 'CRITICAL') " +
                   "GROUP BY DATE(m.updated_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyHighRiskActivityCounts(@Param("start") java.time.LocalDateTime start,
                                                  @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT DATE(m.updated_at) AS d, COUNT(*) AS cnt " +
                   "FROM merchants m " +
                   "WHERE m.psp_id = :pspId " +
                   "  AND m.updated_at >= :start AND m.updated_at < :end " +
                   "  AND m.risk_level IN ('HIGH', 'CRITICAL') " +
                   "GROUP BY DATE(m.updated_at) ORDER BY d", nativeQuery = true)
    List<Object[]> getDailyHighRiskActivityCountsByPsp(@Param("pspId") Long pspId,
                                                       @Param("start") java.time.LocalDateTime start,
                                                       @Param("end") java.time.LocalDateTime end);

    /**
     * Top-N merchants by stored risk score (krs), then by riskLevel ordinal.
     * Uses a native query so we can rank LOW/MEDIUM/HIGH/CRITICAL/UNKNOWN
     * inside the database and push the LIMIT to Postgres. Returns rows of
     * [merchant_id (BIGINT), legal_name (TEXT), trading_name (TEXT),
     *  krs (DOUBLE), risk_level (TEXT)].
     */
    @Query(value =
        "SELECT m.merchant_id, m.legal_name, m.trading_name, " +
        "       COALESCE(m.krs, 0) AS krs, " +
        "       COALESCE(m.risk_level, 'UNKNOWN') AS risk_level " +
        "FROM merchants m " +
        "ORDER BY " +
        "  CASE COALESCE(m.risk_level, 'UNKNOWN') " +
        "       WHEN 'CRITICAL' THEN 0 " +
        "       WHEN 'HIGH'     THEN 1 " +
        "       WHEN 'MEDIUM'   THEN 2 " +
        "       WHEN 'LOW'      THEN 3 " +
        "       ELSE 4 END, " +
        "  COALESCE(m.krs, 0) DESC " +
        "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopRiskMerchants(@Param("limit") int limit);

    @Query(value =
        "SELECT m.merchant_id, m.legal_name, m.trading_name, " +
        "       COALESCE(m.krs, 0) AS krs, " +
        "       COALESCE(m.risk_level, 'UNKNOWN') AS risk_level " +
        "FROM merchants m " +
        "WHERE m.psp_id = :pspId " +
        "ORDER BY " +
        "  CASE COALESCE(m.risk_level, 'UNKNOWN') " +
        "       WHEN 'CRITICAL' THEN 0 " +
        "       WHEN 'HIGH'     THEN 1 " +
        "       WHEN 'MEDIUM'   THEN 2 " +
        "       WHEN 'LOW'      THEN 3 " +
        "       ELSE 4 END, " +
        "  COALESCE(m.krs, 0) DESC " +
        "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopRiskMerchantsByPsp(@Param("pspId") Long pspId,
                                              @Param("limit") int limit);
}
