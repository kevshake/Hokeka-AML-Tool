package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseAuditLogRepository extends JpaRepository<CaseAuditLog, Long> {

    // Find logs for a specific case, ordered by time
    List<CaseAuditLog> findByCaseIdOrderByTimestampAsc(Long caseId);

    // Find actions by a specific user
    List<CaseAuditLog> findByUser_IdOrderByTimestampDesc(Long userId);
}
