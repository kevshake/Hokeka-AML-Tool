package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.DataQualityIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, Long> {

    List<DataQualityIssue> findByEntityTypeAndEntityId(String entityType, String entityId);

    Page<DataQualityIssue> findByPspId(Long pspId, Pageable pageable);

    Page<DataQualityIssue> findByIssueType(String issueType, Pageable pageable);

    Page<DataQualityIssue> findBySeverity(String severity, Pageable pageable);

    Page<DataQualityIssue> findByStatus(String status, Pageable pageable);

    @Query("SELECT dqi FROM DataQualityIssue dqi WHERE " +
           "(:pspId IS NULL OR dqi.pspId = :pspId) AND " +
           "(:issueType IS NULL OR dqi.issueType = :issueType) AND " +
           "(:severity IS NULL OR dqi.severity = :severity) AND " +
           "(:status IS NULL OR dqi.status = :status) AND " +
           "(:entityType IS NULL OR dqi.entityType = :entityType)")
    Page<DataQualityIssue> findByFilters(@Param("pspId") Long pspId,
                                          @Param("issueType") String issueType,
                                          @Param("severity") String severity,
                                          @Param("status") String status,
                                          @Param("entityType") String entityType,
                                          Pageable pageable);

    @Query("SELECT dqi.issueType, COUNT(dqi) FROM DataQualityIssue dqi WHERE dqi.status = 'OPEN' GROUP BY dqi.issueType")
    List<Object[]> countOpenByIssueType();

    @Query("SELECT dqi.severity, COUNT(dqi) FROM DataQualityIssue dqi WHERE dqi.status = 'OPEN' GROUP BY dqi.severity")
    List<Object[]> countOpenBySeverity();

    long countByPspIdAndStatus(Long pspId, String status);

    long countByCreatedAtAfter(LocalDateTime date);
}
