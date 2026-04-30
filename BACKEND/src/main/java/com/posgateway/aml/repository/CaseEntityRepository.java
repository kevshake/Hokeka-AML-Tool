package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseEntityRepository extends JpaRepository<CaseEntity, Long> {
    List<CaseEntity> findByComplianceCase_Id(Long caseId);

    List<CaseEntity> findByEntityTypeAndEntityReference(String entityType, String entityReference);
}
