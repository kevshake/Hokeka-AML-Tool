package com.posgateway.aml.repository.risk;

import com.posgateway.aml.entity.risk.MccRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MccRiskScoreRepository extends JpaRepository<MccRiskScore, Long> {

    @Query("""
            SELECT t FROM MccRiskScore t
            WHERE t.mcc = :mcc
              AND (t.effectiveFrom IS NULL OR t.effectiveFrom <= :asOf)
              AND (t.effectiveTo IS NULL OR t.effectiveTo > :asOf)
            ORDER BY t.effectiveFrom DESC NULLS LAST
            """)
    /** Returns every currently effective MCC score in deterministic preference order. */
    List<MccRiskScore> findEffectiveRows(@Param("mcc") String mcc, @Param("asOf") OffsetDateTime asOf);

    /** Returns the preferred effective score for the MCC, when configured. */
    default Optional<MccRiskScore> findEffective(String mcc, OffsetDateTime asOf) {
        return findEffectiveRows(mcc, asOf).stream().findFirst();
    }
}
