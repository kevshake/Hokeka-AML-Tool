package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.CustomWatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomWatchlistEntryRepository extends JpaRepository<CustomWatchlistEntry, Long> {
    List<CustomWatchlistEntry> findByWatchlistId(Long watchlistId);
    List<CustomWatchlistEntry> findByEntityNameContainingIgnoreCase(String entityName);
    List<CustomWatchlistEntry> findByEntityType(String entityType);
    List<CustomWatchlistEntry> findByWatchlistIdAndEntityName(Long watchlistId, String entityName);
}
