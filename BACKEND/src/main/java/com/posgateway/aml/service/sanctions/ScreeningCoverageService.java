package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.client.aml.SanctionsCountClient;
import com.posgateway.aml.entity.sanctions.WatchlistUpdate;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.sanctions.WatchlistUpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screening Coverage Reports Service
 * Generates screening coverage statistics by combining merchant-screening
 * timestamps from Postgres with the live sanctions record count served by
 * the {@code aml-microservice} {@code /internal/v1/sanctions/count} endpoint.
 */
@Service
public class ScreeningCoverageService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningCoverageService.class);

    private final MerchantRepository merchantRepository;
    private final WatchlistUpdateRepository watchlistUpdateRepository;
    private final SanctionsCountClient sanctionsCountClient;

    @Autowired
    public ScreeningCoverageService(
            MerchantRepository merchantRepository,
            WatchlistUpdateRepository watchlistUpdateRepository,
            SanctionsCountClient sanctionsCountClient) {
        this.merchantRepository = merchantRepository;
        this.watchlistUpdateRepository = watchlistUpdateRepository;
        this.sanctionsCountClient = sanctionsCountClient;
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

        // Coverage by list type — sum WatchlistUpdate.recordCount per list name.
        Map<String, Long> coverageByList = getSanctionsListBreakdown();
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
     * Per-list breakdown of how many records each ingested watchlist contributed.
     * Sourced from {@link WatchlistUpdate#getRecordCount()} (Postgres) — the
     * aml-microservice owns the live Aerospike set but doesn't expose a
     * per-list breakdown today.
     */
    private Map<String, Long> getSanctionsListBreakdown() {
        Map<String, Long> stats = new HashMap<>();
        List<WatchlistUpdate> updates = watchlistUpdateRepository.findAll();
        for (WatchlistUpdate update : updates) {
            Long recordCount = update.getRecordCount();
            stats.merge(update.getListName(), recordCount != null ? recordCount : 0L, Long::sum);
        }
        return stats;
    }

    /**
     * Live count of sanctions records in the AML microservice's Aerospike set.
     * Returns -1 when the upstream is unavailable (the dashboard MUST surface
     * "unavailable" rather than a misleading "0").
     */
    public long getTotalSanctionsEntities() {
        long count = sanctionsCountClient.getCount();
        if (count < 0) {
            logger.debug("Sanctions count unavailable from aml-microservice");
        }
        return count;
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
