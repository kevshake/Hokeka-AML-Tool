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
