package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspFraudIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspFraudIncidentRepository extends JpaRepository<PspFraudIncident, Long> {
    List<PspFraudIncident> findByPspId(Long pspId);
    List<PspFraudIncident> findByPspIdAndReportingDate(Long pspId, LocalDate reportingDate);
    List<PspFraudIncident> findByAlertIdLink(Long alertId);
    List<PspFraudIncident> findByCaseIdLink(Long caseId);

    /**
     * Fraud incidents reported (or, when reportingDate is null, occurred) inside
     * [windowStart, windowEnd]. Used for the daily CBK return.
     */
    @Query("SELECT f FROM PspFraudIncident f WHERE f.pspId = :pspId AND ( " +
           "   (f.reportingDate IS NOT NULL AND f.reportingDate BETWEEN :windowStart AND :windowEnd) " +
           "OR (f.reportingDate IS NULL AND f.dateOfOccurrence IS NOT NULL " +
           "    AND f.dateOfOccurrence BETWEEN :windowStart AND :windowEnd) " +
           ")")
    List<PspFraudIncident> findActiveInWindow(@Param("pspId") Long pspId,
                                              @Param("windowStart") LocalDate windowStart,
                                              @Param("windowEnd") LocalDate windowEnd);
}
