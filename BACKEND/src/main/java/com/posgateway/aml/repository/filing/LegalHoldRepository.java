package com.posgateway.aml.repository.filing;

import com.posgateway.aml.entity.filing.LegalHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Legal holds (V269). */
public interface LegalHoldRepository extends JpaRepository<LegalHold, UUID> {

    /** Holds of one workflow row. */
    List<LegalHold> findByWorkflowRefId(Long workflowRefId);

    /** Holds of a PSP. */
    List<LegalHold> findByPspId(Long pspId);

    /** Active holds whose workflow has ended (candidates for release once retention elapses). */
    List<LegalHold> findByStateAndReleaseBasisAtIsNotNull(String state);

    /** PSPs that have holds (reconciliation sweep of unconfirmed deliveries). */
    @Query("SELECT DISTINCT h.pspId FROM LegalHold h")
    List<Long> pspsWithHolds();

    /** Active hold ids of a PSP, for the activeHoldDigest the Edge reports. */
    @Query("SELECT h.holdId FROM LegalHold h WHERE h.pspId = :pspId AND h.state = 'ACTIVE'")
    List<UUID> activeHoldIds(@Param("pspId") Long pspId);

    /** Holds among the given ids. */
    List<LegalHold> findByHoldIdIn(Collection<UUID> holdIds);
}
