package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspTariffTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PspTariffTemplateRepository extends JpaRepository<PspTariffTemplate, Long> {
    List<PspTariffTemplate> findByPspId(Long pspId);

    /** Active templates: effectiveFrom <= today AND (effectiveTo IS NULL OR effectiveTo >= today). */
    @Query("SELECT t FROM PspTariffTemplate t WHERE t.pspId = :pspId " +
           "AND (t.effectiveFrom IS NULL OR t.effectiveFrom <= :today) " +
           "AND (t.effectiveTo IS NULL OR t.effectiveTo >= :today)")
    List<PspTariffTemplate> findActiveByPspId(@Param("pspId") Long pspId, @Param("today") LocalDate today);
}
