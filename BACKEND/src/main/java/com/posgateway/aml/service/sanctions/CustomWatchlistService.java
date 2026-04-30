package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.sanctions.CustomWatchlist;
import com.posgateway.aml.entity.sanctions.CustomWatchlistEntry;
import com.posgateway.aml.repository.sanctions.CustomWatchlistEntryRepository;
import com.posgateway.aml.repository.sanctions.CustomWatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomWatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(CustomWatchlistService.class);

    private final CustomWatchlistRepository watchlistRepository;
    private final CustomWatchlistEntryRepository entryRepository;
    private final com.posgateway.aml.service.cache.ScreeningCacheService screeningCacheService; // Aerospike cache

    @Autowired
    public CustomWatchlistService(CustomWatchlistRepository watchlistRepository,
                                   CustomWatchlistEntryRepository entryRepository,
                                   com.posgateway.aml.service.cache.ScreeningCacheService screeningCacheService) {
        this.watchlistRepository = watchlistRepository;
        this.entryRepository = entryRepository;
        this.screeningCacheService = screeningCacheService;
    }

    /**
     * Create a new custom watchlist
     */
    @Transactional
    public CustomWatchlist createWatchlist(String watchlistName, String description, String listType, Long createdBy) {
        if (watchlistRepository.findByWatchlistName(watchlistName).isPresent()) {
            throw new IllegalArgumentException("Watchlist with name '" + watchlistName + "' already exists");
        }

        CustomWatchlist watchlist = new CustomWatchlist();
        watchlist.setWatchlistName(watchlistName);
        watchlist.setDescription(description);
        watchlist.setListType(listType);
        watchlist.setStatus("ACTIVE");
        watchlist.setCreatedBy(createdBy);
        watchlist.setCreatedAt(LocalDateTime.now());

        logger.info("Created custom watchlist: {} by user {}", watchlistName, createdBy);
        return watchlistRepository.save(watchlist);
    }

    /**
     * Get all watchlists
     */
    public List<CustomWatchlist> getAllWatchlists() {
        return watchlistRepository.findAll();
    }

    /**
     * Get active watchlists
     */
    public List<CustomWatchlist> getActiveWatchlists() {
        return watchlistRepository.findByStatus("ACTIVE");
    }

    /**
     * Get watchlist by ID
     */
    public Optional<CustomWatchlist> getWatchlist(Long id) {
        return watchlistRepository.findById(id);
    }

    /**
     * Update watchlist
     */
    @Transactional
    public CustomWatchlist updateWatchlist(Long id, String description, String status) {
        CustomWatchlist watchlist = watchlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with ID: " + id));

        if (description != null) {
            watchlist.setDescription(description);
        }
        if (status != null) {
            watchlist.setStatus(status);
        }
        watchlist.setUpdatedAt(LocalDateTime.now());

        logger.info("Updated custom watchlist: {}", watchlist.getWatchlistName());
        return watchlistRepository.save(watchlist);
    }

    /**
     * Add entry to watchlist
     */
    @Transactional
    public CustomWatchlistEntry addEntry(Long watchlistId, String entityName, String entityType,
                                         String matchReason, String riskLevel, Long addedBy) {
        CustomWatchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with ID: " + watchlistId));

        // Check if entry already exists
        List<CustomWatchlistEntry> existing = entryRepository.findByWatchlistIdAndEntityName(watchlistId, entityName);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("Entry with name '" + entityName + "' already exists in this watchlist");
        }

        CustomWatchlistEntry entry = new CustomWatchlistEntry();
        entry.setWatchlist(watchlist);
        entry.setEntityName(entityName);
        entry.setEntityType(entityType);
        entry.setMatchReason(matchReason);
        entry.setRiskLevel(riskLevel);
        entry.setAddedBy(addedBy);
        entry.setAddedAt(LocalDateTime.now());

        logger.info("Added entry '{}' to watchlist {}", entityName, watchlist.getWatchlistName());
        
        // Cache the entry in Aerospike for fast lookups
        screeningCacheService.cacheCustomWatchlistEntry(entityName, entityType, true);
        
        return entryRepository.save(entry);
    }

    /**
     * Get all entries for a watchlist
     */
    public List<CustomWatchlistEntry> getWatchlistEntries(Long watchlistId) {
        return entryRepository.findByWatchlistId(watchlistId);
    }

    /**
     * Remove entry from watchlist
     */
    @Transactional
    public void removeEntry(Long entryId) {
        CustomWatchlistEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found with ID: " + entryId));

        // Remove from Aerospike cache
        screeningCacheService.cacheCustomWatchlistEntry(entry.getEntityName(), entry.getEntityType(), false);
        
        entryRepository.delete(entry);
        logger.info("Removed entry ID {} from watchlist", entryId);
    }

    /**
     * Search entries by name
     */
    public List<CustomWatchlistEntry> searchEntries(String entityName) {
        return entryRepository.findByEntityNameContainingIgnoreCase(entityName);
    }

    /**
     * Check if an entity matches any custom watchlist entry
     * Uses Aerospike cache for fast lookups
     */
    public boolean isEntityOnCustomWatchlist(String entityName, String entityType) {
        // Fast Aerospike cache lookup first
        Boolean cached = screeningCacheService.isOnCustomWatchlist(entityName, entityType);
        if (cached != null) {
            logger.debug("Custom watchlist check from cache: {}:{} = {}", entityType, entityName, cached);
            return cached;
        }
        
        // Fallback to database lookup
        List<CustomWatchlistEntry> matches = entryRepository.findByEntityNameContainingIgnoreCase(entityName);
        boolean isOnWatchlist = matches.stream()
                .anyMatch(entry -> entry.getEntityType().equalsIgnoreCase(entityType) &&
                        entry.getWatchlist().getStatus().equals("ACTIVE"));
        
        // Cache the result in Aerospike for future fast lookups
        screeningCacheService.cacheCustomWatchlistEntry(entityName, entityType, isOnWatchlist);
        
        return isOnWatchlist;
    }
}
