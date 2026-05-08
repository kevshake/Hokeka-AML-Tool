package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.SarTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link SarTemplate}.
 *
 * <p>Templates are looked up by regulator + ISO 3166-1 alpha-3 jurisdiction.
 * Multiple versions can coexist; only the latest active row should be used
 * for new SAR generation.
 */
@Repository
public interface SarTemplateRepository extends JpaRepository<SarTemplate, Long> {

    /**
     * Returns the latest active template for a regulator + jurisdiction
     * (highest {@code version} string by lexical sort, which works because
     * the project versions templates as {@code YYYY.N}).
     */
    Optional<SarTemplate> findFirstByRegulatorAndJurisdictionAndActiveTrueOrderByVersionDesc(
            String regulator, String jurisdiction);

    /**
     * Convenience alias matching the spec naming.
     */
    default Optional<SarTemplate> findActiveByRegulatorAndJurisdiction(String regulator, String jurisdiction) {
        return findFirstByRegulatorAndJurisdictionAndActiveTrueOrderByVersionDesc(regulator, jurisdiction);
    }
}
