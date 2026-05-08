package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PspCyberIncidentRepository extends JpaRepository<PspCyberIncident, Long> {
    List<PspCyberIncident> findByPspId(Long pspId);
    Optional<PspCyberIncident> findByIncidentNumber(String incidentNumber);

    /**
     * Daily window: incidents whose incidentDate falls within [start, end).
     * Used by the CBK orchestrator to report only yesterday's incidents.
     */
    List<PspCyberIncident> findByPspIdAndIncidentDateBetween(
            Long pspId, LocalDateTime start, LocalDateTime end);
}
