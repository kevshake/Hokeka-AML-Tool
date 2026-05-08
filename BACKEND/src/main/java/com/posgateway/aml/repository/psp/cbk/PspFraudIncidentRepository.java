package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspFraudIncident;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Daily window: incidents whose reportingDate falls within [start, end).
     * Used by the CBK orchestrator to report only yesterday's fraud incidents.
     */
    List<PspFraudIncident> findByPspIdAndReportingDateBetween(
            Long pspId, LocalDate start, LocalDate end);
}
