package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.CaseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseEvidenceRepository extends JpaRepository<CaseEvidence, Long> {
    List<CaseEvidence> findByComplianceCase_Id(Long caseId);
}

