package com.posgateway.aml.repository.residency;

import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Edge merchant blocklist rows (never deleted; removal sets removed_at). */
public interface EdgeMerchantBlocklistRepository extends JpaRepository<EdgeMerchantBlocklistEntry, Long> {

    /** Active blocks of a PSP in merchant order (stable document ordering). */
    List<EdgeMerchantBlocklistEntry> findByPspIdAndRemovedAtIsNullOrderByMerchantIdAsc(Long pspId);

    /** The active block of one merchant, if any (partial unique index in V261). */
    Optional<EdgeMerchantBlocklistEntry> findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(Long pspId, String merchantId);

    /**
     * Blocklist version: every insert and every removal counts once, so the value only grows and
     * changes whenever the active set changes.
     */
    @Query("SELECT COUNT(e) + COUNT(e.removedAt) FROM EdgeMerchantBlocklistEntry e WHERE e.pspId = :pspId")
    long version(@Param("pspId") Long pspId);
}
