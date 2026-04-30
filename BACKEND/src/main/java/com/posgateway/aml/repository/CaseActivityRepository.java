package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Case Activity entities
 */
@Repository
public interface CaseActivityRepository extends JpaRepository<CaseActivity, Long> {

    /**
     * Find activities for a case, ordered by most recent first
     */
    @Query("SELECT a FROM CaseActivity a WHERE a.complianceCase.id = :caseId ORDER BY a.performedAt DESC")
    Page<CaseActivity> findByComplianceCaseIdOrderByPerformedAtDesc(@Param("caseId") Long caseId, Pageable pageable);


    /**
     * Find activities by type
     */
    List<CaseActivity> findByActivityType(com.posgateway.aml.model.ActivityType activityType);
}

