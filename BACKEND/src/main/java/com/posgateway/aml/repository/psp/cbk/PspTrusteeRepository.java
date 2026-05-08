package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspTrustee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PspTrusteeRepository extends JpaRepository<PspTrustee, Long> {
    List<PspTrustee> findByPspId(Long pspId);

    /**
     * Trustees on record during [windowStart, windowEnd]. The PspTrustee entity
     * does not carry appointment/exit dates today, so registry-active rows are
     * gated by createdAt &lt;= windowEndAtEod to avoid leaking rows added strictly
     * after the reporting window. CBK accepts the most-recent register snapshot
     * for the annual return window.
     */
    @Query("SELECT t FROM PspTrustee t WHERE t.pspId = :pspId " +
           "AND (t.createdAt IS NULL OR t.createdAt <= :windowEndAtEod)")
    List<PspTrustee> findActiveInWindowInternal(@Param("pspId") Long pspId,
                                                @Param("windowEndAtEod") LocalDateTime windowEndAtEod);

    /** Convenience overload that uses end-of-day for windowEnd. {@code windowStart} retained for API symmetry. */
    default List<PspTrustee> findActiveInWindow(Long pspId, LocalDate windowStart, LocalDate windowEnd) {
        return findActiveInWindowInternal(pspId, windowEnd.atTime(23, 59, 59));
    }
}
