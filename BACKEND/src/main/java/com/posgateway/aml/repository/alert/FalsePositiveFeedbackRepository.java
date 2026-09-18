package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.FalsePositiveFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FalsePositiveFeedbackRepository extends JpaRepository<FalsePositiveFeedback, Long> {
    List<FalsePositiveFeedback> findByRuleName(String ruleName);

    List<FalsePositiveFeedback> findByAlertId(Long alertId);

    /**
     * Find feedback submitted after a date
     */
    @org.springframework.data.jpa.repository.Query("SELECT f FROM FalsePositiveFeedback f WHERE f.createdAt >= :afterDate")
    List<FalsePositiveFeedback> findByCreatedAtAfter(@Param("afterDate") java.time.LocalDateTime afterDate);
}
