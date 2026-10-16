package com.posgateway.aml.repository.assessment;

import com.posgateway.aml.entity.assessment.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByAssessmentIdOrderByCreatedAtAsc(UUID assessmentId);

    long countByAssessmentId(UUID assessmentId);

    /** Returns true when the assessment already has findings recorded in the given phase (edge ingest idempotency). */
    boolean existsByAssessmentIdAndPhase(UUID assessmentId, com.posgateway.aml.entity.assessment.FindingPhase phase);

    /** Lists findings recorded for a transaction (assessment ledger). */
    List<Finding> findByTxnIdOrderByCreatedAtAsc(Long txnId);
}
