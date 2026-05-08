package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspSystemInterruption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspSystemInterruptionRepository extends JpaRepository<PspSystemInterruption, Long> {
    List<PspSystemInterruption> findByPspId(Long pspId);
    List<PspSystemInterruption> findByPspIdAndReportingDate(Long pspId, LocalDate reportingDate);

    /**
     * Daily window: interruptions whose reportingDate falls within [start, end).
     * Used by the CBK orchestrator to report only yesterday's system stability events.
     */
    List<PspSystemInterruption> findByPspIdAndReportingDateBetween(
            Long pspId, LocalDate start, LocalDate end);
}
