package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspSeniorManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspSeniorManagementRepository extends JpaRepository<PspSeniorManagement, Long> {
    List<PspSeniorManagement> findByPspId(Long pspId);

    /**
     * Officers active during the [windowStart, windowEnd] period for CBK annual return:
     * employed on or before the window end, and not yet retired (or retired after window start).
     */
    @Query("SELECT s FROM PspSeniorManagement s WHERE s.pspId = :pspId " +
           "AND (s.dateOfEmp IS NULL OR s.dateOfEmp <= :windowEnd) " +
           "AND (s.retirementDt IS NULL OR s.retirementDt >= :windowStart)")
    List<PspSeniorManagement> findActiveInWindow(@Param("pspId") Long pspId,
                                                  @Param("windowStart") LocalDate windowStart,
                                                  @Param("windowEnd") LocalDate windowEnd);
}
