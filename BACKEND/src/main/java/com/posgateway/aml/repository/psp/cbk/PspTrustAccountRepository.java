package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspTrustAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspTrustAccountRepository extends JpaRepository<PspTrustAccount, Long> {
    List<PspTrustAccount> findByPspId(Long pspId);
    List<PspTrustAccount> findByPspIdAndAsOfDate(Long pspId, LocalDate asOfDate);

    /**
     * Trust account snapshots whose {@code asOfDate} falls inside [windowStart, windowEnd].
     * For the daily CBK return windowStart and windowEnd are the same day.
     */
    @Query("SELECT a FROM PspTrustAccount a WHERE a.pspId = :pspId " +
           "AND a.asOfDate IS NOT NULL " +
           "AND a.asOfDate BETWEEN :windowStart AND :windowEnd")
    List<PspTrustAccount> findActiveInWindow(@Param("pspId") Long pspId,
                                             @Param("windowStart") LocalDate windowStart,
                                             @Param("windowEnd") LocalDate windowEnd);
}
