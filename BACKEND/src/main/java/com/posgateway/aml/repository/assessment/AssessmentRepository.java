package com.posgateway.aml.repository.assessment;

import com.posgateway.aml.entity.assessment.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findFirstByTxnIdOrderByCreatedAtDesc(Long txnId);

    Optional<Assessment> findByIdAndPspId(UUID id, Long pspId);
}
