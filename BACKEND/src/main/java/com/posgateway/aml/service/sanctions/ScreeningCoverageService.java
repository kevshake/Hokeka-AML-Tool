package com.posgateway.aml.service.sanctions;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Record;
import com.aerospike.client.policy.QueryPolicy;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.posgateway.aml.entity.sanctions.WatchlistUpdate;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.sanctions.WatchlistUpdateRepository;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screening Coverage Reports Service
 * Generates screening coverage statistics
 */
@Service
public class ScreeningCoverageService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningCoverageService.class);

    private final MerchantRepository merchantRepository;
    private final WatchlistUpdateRepository watchlistUpdateRepository;
    private final AerospikeConnectionService aerospikeConnectionService;

    @Value("${aerospike.namespace:sanctions}")
    private String aerospikeNamespace;

    @Autowired
    public ScreeningCoverageService(
            MerchantRepository merchantRepository,
            WatchlistUpdateRepository watchlistUpdateRepository,
            AerospikeConnectionService aerospikeConnectionService) {
        this.merchantRepository = merchantRepository;
        this.watchlistUpdateRepository = watchlistUpdateRepository;
        this.aerospikeConnectionService = aerospikeConnectionService;
    }

    /**
     * Get screening coverage statistics
     */
    public ScreeningCoverageReport getCoverageReport() {
        ScreeningCoverageReport report = new ScreeningCoverageReport();
        report.setGeneratedAt(LocalDateTime.now());

        // Total merchants
        long totalMerchants = merchantRepository.count();
        report.setTotalMerchants(totalMerchants);

        // Screened merchants (have been screened at least once)
        long screenedMerchants = merchantRepository.findAll().stream()
                .filter(m -> m.getLastScreenedAt() != null)
                .count();
        report.setScreenedMerchants(screenedMerchants);

        // Coverage percentage
        double coveragePercent = totalMerchants > 0 ? 
                (screenedMerchants / (double) totalMerchants) * 100 : 0.0;
        report.setCoveragePercentage(coveragePercent);

        // Coverage by list type - query Aerospike for actual sanctions list statistics
        Map<String, Long> coverageByList = getAerospikeSanctionsStatistics();
        report.setCoverageByListType(coverageByList);

        // Last screening dates
        LocalDate oldestScreening = merchantRepository.findAll().stream()
                .filter(m -> m.getLastScreenedAt() != null)
                .map(m -> m.getLastScreenedAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
        report.setOldestScreeningDate(oldestScreening);

        LocalDate newestScreening = merchantRepository.findAll().stream()
                .filter(m -> m.getLastScreenedAt() != null)
                .map(m -> m.getLastScreenedAt().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);
        report.setNewestScreeningDate(newestScreening);

        return report;
    }

    /**
     * Get sanctions list statistics from Aerospike
     * Queries the actual sanctions data stored in Aerospike
     */
    private Map<String, Long> getAerospikeSanctionsStatistics() {
        Map<String, Long> stats = new HashMap<>();
        
        AerospikeClient client = aerospikeConnectionService.getClient();
        if (client == null || !aerospikeConnectionService.isConnected()) {
            logger.warn("Aerospike not connected, falling back to metadata from PostgreSQL");
            // Fallback to metadata
            List<WatchlistUpdate> updates = watchlistUpdateRepository.findAll();
            for (WatchlistUpdate update : updates) {
                Long recordCount = update.getRecordCount();
                stats.merge(update.getListName(), recordCount != null ? recordCount : 0L, (a, b) -> a + b);
            }
            return stats;
        }

        try {
            // Query Aerospike to count entities by type
            Statement stmt = new Statement();
            stmt.setNamespace(aerospikeNamespace);
            stmt.setSetName("entities");
            
            // Scan all records to get statistics (in production, might use secondary index)
            QueryPolicy policy = new QueryPolicy();
            RecordSet recordSet = client.query(policy, stmt);
            
            long totalEntities = 0;
            Map<String, Long> byEntityType = new HashMap<>();
            
            try {
                while (recordSet.next()) {
                    Record record = recordSet.getRecord();
                    totalEntities++;
                    
                    String entityType = (String) record.bins.get("entity_type");
                    if (entityType != null) {
                        byEntityType.merge(entityType, 1L, (a, b) -> a + b);
                    }
                }
            } finally {
                recordSet.close();
            }
            
            stats.put("TOTAL_ENTITIES", totalEntities);
            stats.putAll(byEntityType);
            
            logger.debug("Retrieved Aerospike sanctions statistics: {} total entities", totalEntities);
            
        } catch (Exception e) {
            logger.error("Error querying Aerospike for sanctions statistics: {}", e.getMessage(), e);
            // Fallback to metadata
            List<WatchlistUpdate> updates = watchlistUpdateRepository.findAll();
            for (WatchlistUpdate update : updates) {
                Long recordCount = update.getRecordCount();
                stats.merge(update.getListName(), recordCount != null ? recordCount : 0L, (a, b) -> a + b);
            }
        }
        
        return stats;
    }

    /**
     * Get total count of sanctions entities in Aerospike
     */
    public long getTotalSanctionsEntitiesInAerospike() {
        AerospikeClient client = aerospikeConnectionService.getClient();
        if (client == null || !aerospikeConnectionService.isConnected()) {
            return 0;
        }

        try {
            Statement stmt = new Statement();
            stmt.setNamespace(aerospikeNamespace);
            stmt.setSetName("entities");
            
            QueryPolicy policy = new QueryPolicy();
            RecordSet recordSet = client.query(policy, stmt);
            
            long count = 0;
            try {
                while (recordSet.next()) {
                    count++;
                }
            } finally {
                recordSet.close();
            }
            
            return count;
        } catch (Exception e) {
            logger.error("Error counting Aerospike sanctions entities: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Screening Coverage Report DTO
     */
    public static class ScreeningCoverageReport {
        private LocalDateTime generatedAt;
        private long totalMerchants;
        private long screenedMerchants;
        private double coveragePercentage;
        private Map<String, Long> coverageByListType;
        private LocalDate oldestScreeningDate;
        private LocalDate newestScreeningDate;

        // Getters and Setters
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        public long getTotalMerchants() { return totalMerchants; }
        public void setTotalMerchants(long totalMerchants) { this.totalMerchants = totalMerchants; }
        public long getScreenedMerchants() { return screenedMerchants; }
        public void setScreenedMerchants(long screenedMerchants) { this.screenedMerchants = screenedMerchants; }
        public double getCoveragePercentage() { return coveragePercentage; }
        public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }
        public Map<String, Long> getCoverageByListType() { return coverageByListType; }
        public void setCoverageByListType(Map<String, Long> coverageByListType) { this.coverageByListType = coverageByListType; }
        public LocalDate getOldestScreeningDate() { return oldestScreeningDate; }
        public void setOldestScreeningDate(LocalDate oldestScreeningDate) { this.oldestScreeningDate = oldestScreeningDate; }
        public LocalDate getNewestScreeningDate() { return newestScreeningDate; }
        public void setNewestScreeningDate(LocalDate newestScreeningDate) { this.newestScreeningDate = newestScreeningDate; }
    }
}

