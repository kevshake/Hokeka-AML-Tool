package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.CbkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CbkSubmissionRepository extends JpaRepository<CbkSubmission, Long> {

    /**
     * Returns all CBK submissions for a PSP within a given period bucket
     * (e.g. "monthly", "2026-Q1"), most recent first.
     */
    List<CbkSubmission> findByPspIdAndPeriodOrderBySubmittedAtDesc(Long pspId, String period);

    /**
     * PSP-scoped lookup. Used by detail endpoints to enforce tenant isolation.
     */
    Optional<CbkSubmission> findByPspIdAndId(Long pspId, Long id);

    /**
     * All submissions for a PSP regardless of period — used when the FE omits
     * the period filter. Most recent first.
     */
    List<CbkSubmission> findByPspIdOrderBySubmittedAtDesc(Long pspId);
}
