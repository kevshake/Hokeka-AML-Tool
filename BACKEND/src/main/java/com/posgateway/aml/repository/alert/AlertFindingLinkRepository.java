package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.AlertFindingLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for alert ↔ finding provenance links.
 */
public interface AlertFindingLinkRepository extends JpaRepository<AlertFindingLink, AlertFindingLink.AlertFindingId> {

    /**
     * Lists findings attached to an alert for investigation detail.
     */
    List<AlertFindingLink> findByAlertIdOrderByLinkedAtAsc(Long alertId);

    /**
     * Lists alerts referencing a given finding id.
     */
    List<AlertFindingLink> findByFindingId(UUID findingId);
}
