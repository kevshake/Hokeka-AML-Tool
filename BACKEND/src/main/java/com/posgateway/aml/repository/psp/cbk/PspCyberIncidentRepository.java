package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PspCyberIncidentRepository extends JpaRepository<PspCyberIncident, Long> {
    List<PspCyberIncident> findByPspId(Long pspId);
    Optional<PspCyberIncident> findByIncidentNumber(String incidentNumber);

    /**
     * Cyber incidents whose {@code incidentDate} falls inside [windowStart, windowEnd].
     * Used for the daily CBK return.
     */
    @Query("SELECT i FROM PspCyberIncident i WHERE i.pspId = :pspId " +
           "AND i.incidentDate IS NOT NULL " +
           "AND i.incidentDate >= :windowStartAtStart " +
           "AND i.incidentDate <= :windowEndAtEod")
    List<PspCyberIncident> findActiveInWindowInternal(@Param("pspId") Long pspId,
                                                      @Param("windowStartAtStart") LocalDateTime windowStartAtStart,
                                                      @Param("windowEndAtEod") LocalDateTime windowEndAtEod);

    default List<PspCyberIncident> findActiveInWindow(Long pspId, LocalDate windowStart, LocalDate windowEnd) {
        return findActiveInWindowInternal(pspId,
                windowStart.atStartOfDay(),
                windowEnd.atTime(23, 59, 59));
    }
}
