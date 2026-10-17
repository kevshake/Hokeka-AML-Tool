package com.posgateway.aml.repository.filing;

import com.posgateway.aml.entity.filing.LegalHoldSiteAck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Per-site delivery and acknowledgement of legal holds (V269). */
public interface LegalHoldSiteAckRepository extends JpaRepository<LegalHoldSiteAck, Long> {

    /** The row of one hold at one site. */
    Optional<LegalHoldSiteAck> findByHoldIdAndSiteId(UUID holdId, String siteId);

    /** Rows of several holds. */
    List<LegalHoldSiteAck> findByHoldIdIn(Collection<UUID> holdIds);
}
