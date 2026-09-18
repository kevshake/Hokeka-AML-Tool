package com.posgateway.aml.repository.risk;

import com.posgateway.aml.entity.risk.HighRiskCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HighRiskCountryRepository extends JpaRepository<HighRiskCountry, Long> {
    Optional<HighRiskCountry> findByCountryCode(String countryCode);

    boolean existsByCountryCode(String countryCode);
}
