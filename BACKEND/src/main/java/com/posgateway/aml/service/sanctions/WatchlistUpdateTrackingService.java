package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.sanctions.WatchlistUpdate;
import com.posgateway.aml.repository.sanctions.WatchlistUpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Watchlist Update Tracking Service
 * Tracks watchlist update frequencies and dates
 */
@Service
public class WatchlistUpdateTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistUpdateTrackingService.class);

    private final WatchlistUpdateRepository updateRepository;

    @Autowired
    public WatchlistUpdateTrackingService(WatchlistUpdateRepository updateRepository) {
        this.updateRepository = updateRepository;
    }

    /**
     * Record watchlist update
     */
    @Transactional
    public WatchlistUpdate recordUpdate(String listName, String listType, LocalDate updateDate,
                                        Long recordCount, String sourceUrl, String checksum) {
        // Check if update already exists
        Optional<WatchlistUpdate> existing = updateRepository.findByListNameAndListTypeAndUpdateDate(
                listName, listType, updateDate);

        if (existing.isPresent()) {
            WatchlistUpdate update = existing.get();
            update.setRecordCount(recordCount);
            update.setSourceUrl(sourceUrl);
            update.setChecksum(checksum);
            update.setStatus("COMPLETED");
            update.setProcessedAt(java.time.LocalDateTime.now());
            logger.info("Updated existing watchlist update record: {} {} {}", listName, listType, updateDate);
            return updateRepository.save(update);
        }

        WatchlistUpdate update = new WatchlistUpdate();
        update.setListName(listName);
        update.setListType(listType);
        update.setUpdateDate(updateDate);
        update.setRecordCount(recordCount);
        update.setSourceUrl(sourceUrl);
        update.setChecksum(checksum);
        update.setStatus("COMPLETED");
        update.setProcessedAt(java.time.LocalDateTime.now());
        update.setCreatedAt(java.time.LocalDateTime.now());

        logger.info("Recorded new watchlist update: {} {} {}", listName, listType, updateDate);
        return updateRepository.save(update);
    }

    /**
     * Get latest update for a list
     */
    public Optional<WatchlistUpdate> getLatestUpdate(String listName, String listType) {
        List<WatchlistUpdate> updates = updateRepository.findByListNameAndListType(listName, listType);
        return updates.stream()
                .max((a, b) -> a.getUpdateDate().compareTo(b.getUpdateDate()));
    }

    /**
     * Get update frequency statistics
     */
    public java.util.Map<String, Object> getUpdateFrequencyStats(String listName, String listType) {
        List<WatchlistUpdate> updates = updateRepository.findByListNameAndListType(listName, listType);
        
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("listName", listName);
        stats.put("listType", listType);
        stats.put("totalUpdates", updates.size());
        
        if (!updates.isEmpty()) {
            LocalDate latest = updates.stream()
                    .map(WatchlistUpdate::getUpdateDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            stats.put("latestUpdateDate", latest);
            
            LocalDate earliest = updates.stream()
                    .map(WatchlistUpdate::getUpdateDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            stats.put("earliestUpdateDate", earliest);
            
            if (latest != null && earliest != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(earliest, latest);
                double avgDaysBetween = updates.size() > 1 ? daysBetween / (double) (updates.size() - 1) : 0;
                stats.put("averageDaysBetweenUpdates", avgDaysBetween);
            }
        }
        
        return stats;
    }
}

