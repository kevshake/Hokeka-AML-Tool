package com.posgateway.aml.repository.risk;

import com.posgateway.aml.entity.risk.SchemeProgramThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeProgramThresholdRepository extends JpaRepository<SchemeProgramThreshold, Long> {

    @Query("""
            SELECT t FROM SchemeProgramThreshold t
            WHERE t.program = :program
              AND (t.effectiveFrom IS NULL OR t.effectiveFrom <= :asOf)
              AND (t.effectiveTo IS NULL OR t.effectiveTo > :asOf)
            ORDER BY t.effectiveFrom DESC NULLS LAST
            """)
    List<SchemeProgramThreshold> findEffectiveRows(@Param("program") String program,
            @Param("asOf") OffsetDateTime asOf);

    default Optional<SchemeProgramThreshold> findEffective(String program, OffsetDateTime asOf) {
        return findEffectiveRows(program, asOf).stream().findFirst();
    }
}
