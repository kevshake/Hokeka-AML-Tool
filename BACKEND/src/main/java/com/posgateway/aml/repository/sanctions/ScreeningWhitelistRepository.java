package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScreeningWhitelistRepository extends JpaRepository<ScreeningWhitelist, Long> {
    Optional<ScreeningWhitelist> findByEntityIdAndEntityType(String entityId, String entityType);
    Optional<ScreeningWhitelist> findByEntityIdAndEntityTypeAndActive(String entityId, String entityType, boolean active);
    List<ScreeningWhitelist> findByActive(boolean active);
    List<ScreeningWhitelist> findByEntityTypeAndActive(String entityType, boolean active);
}

