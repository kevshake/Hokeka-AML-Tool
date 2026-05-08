package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.RegulatorySubmissionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link RegulatorySubmissionAttempt}.
 */
@Repository
public interface RegulatorySubmissionAttemptRepository
        extends JpaRepository<RegulatorySubmissionAttempt, Long> {

    /** Highest existing attempt_no for a submission/regulator pair (or empty). */
    Optional<RegulatorySubmissionAttempt>
        findTopBySubmissionIdAndRegulatorOrderByAttemptNoDesc(Long submissionId, String regulator);

    /** All attempts sharing an idempotency key — used for replay detection. */
    List<RegulatorySubmissionAttempt> findByIdempotencyKey(String idempotencyKey);
}
