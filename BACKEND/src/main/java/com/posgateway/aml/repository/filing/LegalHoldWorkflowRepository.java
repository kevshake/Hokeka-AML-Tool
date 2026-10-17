package com.posgateway.aml.repository.filing;

import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Workflow records that own legal holds (V269). */
public interface LegalHoldWorkflowRepository extends JpaRepository<LegalHoldWorkflow, Long> {

    /** The workflow row of one record. */
    Optional<LegalHoldWorkflow> findByWorkflowTypeAndWorkflowId(String workflowType, Long workflowId);

    /** The workflow row of one record, locked for the caller's transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM LegalHoldWorkflow w WHERE w.workflowType = :type AND w.workflowId = :id")
    Optional<LegalHoldWorkflow> lockByWorkflow(@Param("type") String workflowType, @Param("id") Long workflowId);

    /** Every workflow row of one type (reconciliation of deleted records). */
    List<LegalHoldWorkflow> findByWorkflowType(String workflowType);
}
