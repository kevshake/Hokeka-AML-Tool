package com.posgateway.aml.repository.assessment;

import com.posgateway.aml.entity.assessment.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByAssessmentIdOrderByCreatedAtAsc(UUID assessmentId);

    long countByAssessmentId(UUID assessmentId);
}
