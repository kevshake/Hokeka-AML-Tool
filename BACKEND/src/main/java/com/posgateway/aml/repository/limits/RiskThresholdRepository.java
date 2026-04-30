package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.RiskThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskThresholdRepository extends JpaRepository<RiskThreshold, Long> {
    Optional<RiskThreshold> findByRiskLevel(String riskLevel);
    List<RiskThreshold> findByStatus(String status);
}

