package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspSystemInterruption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspSystemInterruptionRepository extends JpaRepository<PspSystemInterruption, Long> {
    List<PspSystemInterruption> findByPspId(Long pspId);
    List<PspSystemInterruption> findByPspIdAndReportingDate(Long pspId, LocalDate reportingDate);

    /**
     * System interruptions whose {@code reportingDate} falls inside [windowStart, windowEnd].
     */
    @Query("SELECT s FROM PspSystemInterruption s WHERE s.pspId = :pspId " +
           "AND s.reportingDate IS NOT NULL " +
           "AND s.reportingDate BETWEEN :windowStart AND :windowEnd")
    List<PspSystemInterruption> findActiveInWindow(@Param("pspId") Long pspId,
                                                   @Param("windowStart") LocalDate windowStart,
                                                   @Param("windowEnd") LocalDate windowEnd);
}
