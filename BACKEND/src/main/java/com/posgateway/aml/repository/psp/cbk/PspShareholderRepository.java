package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspShareholder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspShareholderRepository extends JpaRepository<PspShareholder, Long> {
    List<PspShareholder> findByPspId(Long pspId);

    /**
     * Shareholders of record during [windowStart, windowEnd]: onboarded on or
     * before the window end. The entity does not record share disposal so
     * holdings are treated as continuing once onboarded.
     */
    @Query("SELECT s FROM PspShareholder s WHERE s.pspId = :pspId " +
           "AND (s.onboardingDate IS NULL OR s.onboardingDate <= :windowEnd)")
    List<PspShareholder> findActiveInWindow(@Param("pspId") Long pspId,
                                            @Param("windowStart") LocalDate windowStart,
                                            @Param("windowEnd") LocalDate windowEnd);
}
