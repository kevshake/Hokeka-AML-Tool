package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.CountryComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryComplianceRuleRepository extends JpaRepository<CountryComplianceRule, Long> {
    Optional<CountryComplianceRule> findByCountryCode(String countryCode);
    List<CountryComplianceRule> findByStatus(String status);
}

