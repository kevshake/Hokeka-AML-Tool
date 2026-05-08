package com.posgateway.aml.repository.risk;

import com.posgateway.aml.entity.risk.CountryRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryRiskRepository extends JpaRepository<CountryRiskScore, Long> {

    Optional<CountryRiskScore> findByCountryCode(String countryCode);
}
