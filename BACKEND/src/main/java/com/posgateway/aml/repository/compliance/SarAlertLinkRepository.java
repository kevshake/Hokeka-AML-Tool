package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.SarAlertLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for SAR↔alert many-to-many links.
 */
@Repository
public interface SarAlertLinkRepository extends JpaRepository<SarAlertLink, Long> {

    /** Lists alert ids filed on a SAR. */
    List<SarAlertLink> findBySarId(Long sarId);

    /** Lists SAR ids that reference an alert. */
    List<SarAlertLink> findByAlertId(Long alertId);
}
