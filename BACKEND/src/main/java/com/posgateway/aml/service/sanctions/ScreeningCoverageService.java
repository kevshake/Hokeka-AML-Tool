package com.posgateway.aml.service.sanctions;

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

    @SuppressWarnings("unused") // TODO(aerospike-removal): drop when sanctions-stats endpoint is wired in.
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
     * TODO(aerospike-removal): originally counted live entity rows from Aerospike.
     * Aerospike now lives in aml-microservice; until that service exposes a
     * sanctions-stats endpoint we fall back to the {@link WatchlistUpdate}
     * metadata stored in PostgreSQL.
     */
    private Map<String, Long> getAerospikeSanctionsStatistics() {
        Map<String, Long> stats = new HashMap<>();
        if (aerospikeConnectionService != null) {
            // Reference retained so the autowired field isn't flagged unused;
            // the stub always reports !isConnected() so we go straight to fallback.
            logger.debug("aerospike connected={} — using WatchlistUpdate fallback for stats",
                    aerospikeConnectionService.isConnected());
        }
        List<WatchlistUpdate> updates = watchlistUpdateRepository.findAll();
        for (WatchlistUpdate update : updates) {
            Long recordCount = update.getRecordCount();
            stats.merge(update.getListName(), recordCount != null ? recordCount : 0L, Long::sum);
        }
        return stats;
    }

    /**
     * TODO(aerospike-removal): always returns 0 now — the live sanctions count
     * lives behind the aml-microservice. Wire to a new endpoint when one exists.
     */
    public long getTotalSanctionsEntitiesInAerospike() {
        return 0L;
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

