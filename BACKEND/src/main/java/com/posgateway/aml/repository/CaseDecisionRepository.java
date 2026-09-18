package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseDecisionRepository extends JpaRepository<CaseDecision, Long> {
    List<CaseDecision> findByComplianceCaseId(Long caseId);

    List<CaseDecision> findByDecidedById(Long id);
}
