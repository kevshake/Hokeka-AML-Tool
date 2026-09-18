package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspDirector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspDirectorRepository extends JpaRepository<PspDirector, Long> {
    List<PspDirector> findByPspId(Long pspId);

    /**
     * Directors holding the seat during the [windowStart, windowEnd] period:
     * appointed on or before window end and not yet retired (or retired after window start).
     */
    @Query("SELECT d FROM PspDirector d WHERE d.pspId = :pspId " +
           "AND (d.dateOfAppointment IS NULL OR d.dateOfAppointment <= :windowEnd) " +
           "AND (d.dateOfRetirement IS NULL OR d.dateOfRetirement >= :windowStart)")
    List<PspDirector> findActiveInWindow(@Param("pspId") Long pspId,
                                         @Param("windowStart") LocalDate windowStart,
                                         @Param("windowEnd") LocalDate windowEnd);
}
