package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.AlertTuningRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertTuningRecommendationRepository extends JpaRepository<AlertTuningRecommendation, Long> {
    List<AlertTuningRecommendation> findByStatus(String status);
    List<AlertTuningRecommendation> findByRuleName(String ruleName);
}

