package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspTrustAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspTrustAccountRepository extends JpaRepository<PspTrustAccount, Long> {
    List<PspTrustAccount> findByPspId(Long pspId);
    List<PspTrustAccount> findByPspIdAndAsOfDate(Long pspId, LocalDate asOfDate);

    /**
     * Daily window: trust account balances recorded for [start, end).
     * Used by the CBK orchestrator to report only yesterday's balance snapshot.
     */
    List<PspTrustAccount> findByPspIdAndAsOfDateBetween(
            Long pspId, LocalDate start, LocalDate end);
}
