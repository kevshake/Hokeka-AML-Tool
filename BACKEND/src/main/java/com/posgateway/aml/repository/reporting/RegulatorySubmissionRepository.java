package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import com.posgateway.aml.entity.reporting.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegulatorySubmissionRepository extends JpaRepository<RegulatorySubmission, Long> {

    Optional<RegulatorySubmission> findBySubmissionReference(String submissionReference);

    Page<RegulatorySubmission> findByPspId(Long pspId, Pageable pageable);

    Page<RegulatorySubmission> findByStatus(SubmissionStatus status, Pageable pageable);

    Page<RegulatorySubmission> findByRegulatorCode(String regulatorCode, Pageable pageable);

    @Query("SELECT rs FROM RegulatorySubmission rs WHERE " +
           "(:pspId IS NULL OR rs.pspId = :pspId) AND " +
           "(:status IS NULL OR rs.status = :status) AND " +
           "(:regulatorCode IS NULL OR rs.regulatorCode = :regulatorCode) AND " +
           "(:filingDeadlineFrom IS NULL OR rs.filingDeadline >= :filingDeadlineFrom) AND " +
           "(:filingDeadlineTo IS NULL OR rs.filingDeadline <= :filingDeadlineTo)")
    Page<RegulatorySubmission> findByFilters(@Param("pspId") Long pspId,
                                               @Param("status") SubmissionStatus status,
                                               @Param("regulatorCode") String regulatorCode,
                                               @Param("filingDeadlineFrom") LocalDate filingDeadlineFrom,
                                               @Param("filingDeadlineTo") LocalDate filingDeadlineTo,
                                               Pageable pageable);

    @Query("SELECT rs FROM RegulatorySubmission rs WHERE rs.filingDeadline < CURRENT_DATE AND rs.filedAt IS NULL AND rs.status NOT IN ('FILED', 'REJECTED')")
    List<RegulatorySubmission> findOverdueSubmissions();

    @Query("SELECT rs FROM RegulatorySubmission rs WHERE rs.filingDeadline BETWEEN CURRENT_DATE AND :thresholdDate AND rs.filedAt IS NULL AND rs.status NOT IN ('FILED', 'REJECTED')")
    List<RegulatorySubmission> findAtRiskSubmissions(@Param("thresholdDate") LocalDate thresholdDate);

    List<RegulatorySubmission> findByAmendedSubmissionId(Long amendedSubmissionId);

    long countByPspIdAndStatus(Long pspId, SubmissionStatus status);
}
