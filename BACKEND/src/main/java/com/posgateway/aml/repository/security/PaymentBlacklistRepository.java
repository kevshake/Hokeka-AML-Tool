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

    Optional<PaymentBlacklistEntry> findByEntryTypeAndEntryValueAndActiveTrue(String entryType, String entryValue);

    boolean existsByEntryTypeAndEntryValueAndActiveTrue(String entryType, String entryValue);

    /**
     * True when an active block exists that has not expired. A null {@code expires_at} is a
     * permanent block; a non-null one only counts while it is still in the future. This is the
     * expiry-aware check the transaction decision path uses (fastest check, indexed on
     * entry_type+entry_value).
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM PaymentBlacklistEntry e "
            + "WHERE e.entryType = :type AND e.entryValue = :value AND e.active = true "
            + "AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    boolean existsActiveNonExpired(@Param("type") String type, @Param("value") String value,
                                   @Param("now") LocalDateTime now);
}
