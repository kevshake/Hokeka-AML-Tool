package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.TravelRuleJurisdictionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TravelRulePolicyRepository extends JpaRepository<TravelRuleJurisdictionPolicy, Long> {
    List<TravelRuleJurisdictionPolicy> findByPspIdOrderByJurisdictionAscEffectiveFromDesc(Long pspId);
    Optional<TravelRuleJurisdictionPolicy> findByIdAndPspId(Long id, Long pspId);
    @Query("select p from TravelRuleJurisdictionPolicy p where p.pspId = :pspId and p.jurisdiction = :jurisdiction "
            + "and p.enabled = true and p.effectiveFrom <= :at and (p.effectiveUntil is null or p.effectiveUntil > :at) "
            + "order by p.effectiveFrom desc")
    List<TravelRuleJurisdictionPolicy> findActive(@Param("pspId") Long pspId,
            @Param("jurisdiction") String jurisdiction, @Param("at") LocalDateTime at);
}
