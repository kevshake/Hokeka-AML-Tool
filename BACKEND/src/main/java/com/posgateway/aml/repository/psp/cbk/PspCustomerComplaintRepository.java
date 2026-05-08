package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspCustomerComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PspCustomerComplaintRepository extends JpaRepository<PspCustomerComplaint, Long> {
    List<PspCustomerComplaint> findByPspId(Long pspId);
    Optional<PspCustomerComplaint> findByComplaintId(String complaintId);
    List<PspCustomerComplaint> findByPspIdAndRemedialStatus(Long pspId, String remedialStatus);

    /**
     * Complaints whose date of occurrence (or date reported, when occurrence is null)
     * falls inside [windowStart, windowEnd]. Used for the monthly CBK return.
     */
    @Query("SELECT c FROM PspCustomerComplaint c WHERE c.pspId = :pspId " +
           "AND ( " +
           "   (c.dateOfOccurrence IS NOT NULL AND c.dateOfOccurrence BETWEEN :windowStart AND :windowEnd) " +
           "OR (c.dateOfOccurrence IS NULL AND c.dateReportedToTheInstitution IS NOT NULL " +
           "    AND c.dateReportedToTheInstitution BETWEEN :windowStart AND :windowEnd) " +
           ")")
    List<PspCustomerComplaint> findActiveInWindow(@Param("pspId") Long pspId,
                                                  @Param("windowStart") LocalDate windowStart,
                                                  @Param("windowEnd") LocalDate windowEnd);
}
