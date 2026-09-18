package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScreeningOverrideRepository extends JpaRepository<ScreeningOverride, Long> {
    Optional<ScreeningOverride> findByEntityIdAndEntityTypeAndStatus(String entityId, String entityType, String status);
    List<ScreeningOverride> findByEntityIdAndEntityType(String entityId, String entityType);
    List<ScreeningOverride> findByStatus(String status);
}

