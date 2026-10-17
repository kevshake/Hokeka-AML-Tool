package com.posgateway.aml.repository.security;

import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentBlacklistRepository extends JpaRepository<PaymentBlacklistEntry, Long> {

    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE e.pspId = :pspId AND e.entryType = :type "
            + "AND e.entryValue = :value AND e.active = true")
    Optional<PaymentBlacklistEntry> findTenantActive(@Param("pspId") Long pspId,
                                                     @Param("type") String type,
                                                     @Param("value") String value);

    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE e.pspId IS NULL AND e.platformWide = true "
            + "AND e.entryType = :type AND e.entryValue = :value AND e.active = true")
    Optional<PaymentBlacklistEntry> findPlatformActive(@Param("type") String type,
                                                       @Param("value") String value);

    /**
     * True when an active tenant or platform-wide block exists and has not expired.
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM PaymentBlacklistEntry e "
            + "WHERE e.entryType = :type AND e.entryValue = :value AND e.active = true "
            + "AND (e.expiresAt IS NULL OR e.expiresAt > :now) "
            + "AND ((e.pspId IS NULL AND e.platformWide = true) "
            + "     OR (:pspId IS NOT NULL AND e.pspId = :pspId))")
    boolean existsActiveNonExpiredForTenantOrPlatform(@Param("pspId") Long pspId,
                                                      @Param("type") String type,
                                                      @Param("value") String value,
                                                      @Param("now") LocalDateTime now);

    /** Tenant row for (psp, type, value) regardless of active flag (unique per V250 index). */
    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE e.pspId = :pspId AND e.entryType = :type "
            + "AND e.entryValue = :value")
    Optional<PaymentBlacklistEntry> findTenantAny(@Param("pspId") Long pspId,
                                                  @Param("type") String type,
                                                  @Param("value") String value);

    /** Platform-wide row for (type, value) regardless of active flag (unique per V250 index). */
    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE e.pspId IS NULL AND e.platformWide = true "
            + "AND e.entryType = :type AND e.entryValue = :value")
    Optional<PaymentBlacklistEntry> findPlatformAny(@Param("type") String type,
                                                    @Param("value") String value);

    /** A PSP's own entries plus the platform-wide list, optionally filtered by type / active flag. */
    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE "
            + "(e.pspId = :pspId OR (e.pspId IS NULL AND e.platformWide = true)) "
            + "AND (:type IS NULL OR e.entryType = :type) "
            + "AND (:active IS NULL OR e.active = :active)")
    org.springframework.data.domain.Page<PaymentBlacklistEntry> searchForTenant(
            @Param("pspId") Long pspId, @Param("type") String type, @Param("active") Boolean active,
            org.springframework.data.domain.Pageable pageable);

    /** Every entry (platform view), optionally filtered by owning PSP, platform-wide flag, type, active. */
    @Query("SELECT e FROM PaymentBlacklistEntry e WHERE "
            + "(:pspId IS NULL OR e.pspId = :pspId) "
            + "AND (:platformWide IS NULL OR e.platformWide = :platformWide) "
            + "AND (:type IS NULL OR e.entryType = :type) "
            + "AND (:active IS NULL OR e.active = :active)")
    org.springframework.data.domain.Page<PaymentBlacklistEntry> searchAll(
            @Param("pspId") Long pspId, @Param("platformWide") Boolean platformWide,
            @Param("type") String type, @Param("active") Boolean active,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Values of every active, unexpired entry of one type that applies to a PSP (its own plus the
     * platform-wide list) — the set {@link #existsActiveNonExpiredForTenantOrPlatform} answers
     * membership against, distributed to the PSP's Edge in its rule bundle (R10a).
     */
    @Query("SELECT DISTINCT e.entryValue FROM PaymentBlacklistEntry e "
            + "WHERE e.entryType = :type AND e.active = true "
            + "AND (e.expiresAt IS NULL OR e.expiresAt > :now) "
            + "AND ((e.pspId IS NULL AND e.platformWide = true) "
            + "     OR (:pspId IS NOT NULL AND e.pspId = :pspId))")
    java.util.List<String> activeValuesForTenantOrPlatform(@Param("pspId") Long pspId, @Param("type") String type,
                                                          @Param("now") LocalDateTime now);

    /** Newest creation time of any entry that applies to a PSP (null when none). */
    @Query("SELECT MAX(e.createdAt) FROM PaymentBlacklistEntry e "
            + "WHERE (e.pspId IS NULL AND e.platformWide = true) OR (:pspId IS NOT NULL AND e.pspId = :pspId)")
    LocalDateTime newestCreatedAtForTenantOrPlatform(@Param("pspId") Long pspId);

    /** @deprecated Prefer tenant-aware queries. */
    @Deprecated
    Optional<PaymentBlacklistEntry> findByEntryTypeAndEntryValueAndActiveTrue(String entryType, String entryValue);

    /** @deprecated Prefer {@link #existsActiveNonExpiredForTenantOrPlatform}. */
    @Deprecated
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM PaymentBlacklistEntry e "
            + "WHERE e.entryType = :type AND e.entryValue = :value AND e.active = true "
            + "AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    boolean existsActiveNonExpired(@Param("type") String type, @Param("value") String value,
                                   @Param("now") LocalDateTime now);
}
