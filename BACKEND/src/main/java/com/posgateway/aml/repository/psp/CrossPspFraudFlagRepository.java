package com.posgateway.aml.repository.psp;

import com.posgateway.aml.entity.psp.CrossPspFraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for cross-PSP fraud intelligence sharing.
 */
@Repository
public interface CrossPspFraudFlagRepository extends JpaRepository<CrossPspFraudFlag, Long> {

    Optional<CrossPspFraudFlag> findByEntityValueAndEntityType(String entityValue, String entityType);

    List<CrossPspFraudFlag> findByEntityValueInAndRiskLevel(List<String> entityValues, String riskLevel);

    List<CrossPspFraudFlag> findByEntityTypeAndLastFlaggedAfter(String entityType, LocalDateTime since);

    long countByRiskLevel(String riskLevel);

    @Modifying
    @Query("UPDATE CrossPspFraudFlag f SET f.flagCount = f.flagCount + 1, " +
           "f.lastFlagged = :now WHERE f.entityValue = :value AND f.entityType = :type")
    int incrementFlagCount(@Param("value") String entityValue,
                           @Param("type") String entityType,
                           @Param("now") LocalDateTime now);
}