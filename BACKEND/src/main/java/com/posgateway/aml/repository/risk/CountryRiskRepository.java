package com.posgateway.aml.repository.risk;

import com.posgateway.aml.entity.risk.CountryRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link CountryRiskScore}. Primary key is the ISO 3166-1
 * alpha-2 country code (uppercase) — callers MUST normalise before lookup.
 */
@Repository
public interface CountryRiskRepository extends JpaRepository<CountryRiskScore, String> {

    Optional<CountryRiskScore> findByCountryCode(String countryCode);
}
