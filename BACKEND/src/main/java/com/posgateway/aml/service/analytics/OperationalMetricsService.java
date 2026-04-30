package com.posgateway.aml.service.analytics;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Operational Metrics Service
 * Tracks operational performance metrics
 */
@Service
public class OperationalMetricsService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(OperationalMetricsService.class);

    private final ComplianceCaseRepository caseRepository;
    private final SuspiciousActivityReportRepository sarRepository;

    @Autowired
    public OperationalMetricsService(ComplianceCaseRepository caseRepository,
                                    SuspiciousActivityReportRepository sarRepository) {
        this.caseRepository = caseRepository;
        this.sarRepository = sarRepository;
    }

    /**
     * Calculate average case investigation time
     */
    public double calculateAverageInvestigationTime(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceCase> closedCases = caseRepository.findByCreatedAtBetween(startDate, endDate).stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .toList();

        if (closedCases.isEmpty()) {
            return 0.0;
        }

        double totalHours = closedCases.stream()
                .mapToLong(c -> ChronoUnit.HOURS.between(c.getCreatedAt(), c.getResolvedAt()))
                .sum();

        return totalHours / closedCases.size();
    }

    /**
     * Get SAR filing rate
     */
    public SarFilingMetrics getSarFilingMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        List<SuspiciousActivityReport> sars = sarRepository.findByCreatedAtBetween(startDate, endDate);
        
        long totalSars = sars.size();
        long filedSars = sars.stream()
                .filter(sar -> sar.getStatus() == SarStatus.FILED)
                .count();
        
        long onTimeFilings = sars.stream()
                .filter(sar -> sar.getStatus() == SarStatus.FILED && 
                              sar.getFiledAt() != null && 
                              sar.getFilingDeadline() != null &&
                              sar.getFiledAt().isBefore(sar.getFilingDeadline()))
                .count();

        return SarFilingMetrics.builder()
                .totalSars(totalSars)
                .filedSars(filedSars)
                .onTimeFilings(onTimeFilings)
                .filingRate(totalSars > 0 ? (double) filedSars / totalSars * 100 : 0.0)
                .onTimeRate(filedSars > 0 ? (double) onTimeFilings / filedSars * 100 : 0.0)
                .build();
    }

    /**
     * Calculate alert-to-SAR conversion rate
     */
    public double calculateAlertToSarConversionRate(LocalDateTime startDate, LocalDateTime endDate) {
        // TODO: Implement based on your alert system
        // This would require linking alerts to cases to SARs
        return 0.0;
    }

    /**
     * Get investigator productivity metrics
     */
    public InvestigatorProductivityMetrics getInvestigatorProductivity(Long userId, 
                                                                      LocalDateTime startDate, 
                                                                      LocalDateTime endDate) {
        List<ComplianceCase> cases = caseRepository.findByAssignedTo_Id(userId).stream()
                .filter(c -> c.getCreatedAt() != null && 
                            c.getCreatedAt().isAfter(startDate) && 
                            c.getCreatedAt().isBefore(endDate))
                .toList();

        long closedCases = cases.stream()
                .filter(c -> c.getResolvedAt() != null)
                .count();

        double avgResolutionTime = cases.stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .mapToLong(c -> ChronoUnit.HOURS.between(c.getCreatedAt(), c.getResolvedAt()))
                .average()
                .orElse(0.0);

        return InvestigatorProductivityMetrics.builder()
                .totalCases(cases.size())
                .closedCases(closedCases)
                .averageResolutionTimeHours(avgResolutionTime)
                .build();
    }

    /**
     * SAR Filing Metrics DTO
     */
    public static class SarFilingMetrics {
        private long totalSars;
        private long filedSars;
        private long onTimeFilings;
        private double filingRate;
        private double onTimeRate;

        public static SarFilingMetricsBuilder builder() {
            return new SarFilingMetricsBuilder();
        }

        // Getters and Setters
        public long getTotalSars() { return totalSars; }
        public void setTotalSars(long totalSars) { this.totalSars = totalSars; }
        public long getFiledSars() { return filedSars; }
        public void setFiledSars(long filedSars) { this.filedSars = filedSars; }
        public long getOnTimeFilings() { return onTimeFilings; }
        public void setOnTimeFilings(long onTimeFilings) { this.onTimeFilings = onTimeFilings; }
        public double getFilingRate() { return filingRate; }
        public void setFilingRate(double filingRate) { this.filingRate = filingRate; }
        public double getOnTimeRate() { return onTimeRate; }
        public void setOnTimeRate(double onTimeRate) { this.onTimeRate = onTimeRate; }

        public static class SarFilingMetricsBuilder {
            private long totalSars;
            private long filedSars;
            private long onTimeFilings;
            private double filingRate;
            private double onTimeRate;

            public SarFilingMetricsBuilder totalSars(long totalSars) {
                this.totalSars = totalSars;
                return this;
            }

            public SarFilingMetricsBuilder filedSars(long filedSars) {
                this.filedSars = filedSars;
                return this;
            }

            public SarFilingMetricsBuilder onTimeFilings(long onTimeFilings) {
                this.onTimeFilings = onTimeFilings;
                return this;
            }

            public SarFilingMetricsBuilder filingRate(double filingRate) {
                this.filingRate = filingRate;
                return this;
            }

            public SarFilingMetricsBuilder onTimeRate(double onTimeRate) {
                this.onTimeRate = onTimeRate;
                return this;
            }

            public SarFilingMetrics build() {
                SarFilingMetrics metrics = new SarFilingMetrics();
                metrics.totalSars = this.totalSars;
                metrics.filedSars = this.filedSars;
                metrics.onTimeFilings = this.onTimeFilings;
                metrics.filingRate = this.filingRate;
                metrics.onTimeRate = this.onTimeRate;
                return metrics;
            }
        }
    }

    /**
     * Investigator Productivity Metrics DTO
     */
    public static class InvestigatorProductivityMetrics {
        private long totalCases;
        private long closedCases;
        private double averageResolutionTimeHours;

        public static InvestigatorProductivityMetricsBuilder builder() {
            return new InvestigatorProductivityMetricsBuilder();
        }

        // Getters and Setters
        public long getTotalCases() { return totalCases; }
        public void setTotalCases(long totalCases) { this.totalCases = totalCases; }
        public long getClosedCases() { return closedCases; }
        public void setClosedCases(long closedCases) { this.closedCases = closedCases; }
        public double getAverageResolutionTimeHours() { return averageResolutionTimeHours; }
        public void setAverageResolutionTimeHours(double averageResolutionTimeHours) {
            this.averageResolutionTimeHours = averageResolutionTimeHours;
        }

        public static class InvestigatorProductivityMetricsBuilder {
            private long totalCases;
            private long closedCases;
            private double averageResolutionTimeHours;

            public InvestigatorProductivityMetricsBuilder totalCases(long totalCases) {
                this.totalCases = totalCases;
                return this;
            }

            public InvestigatorProductivityMetricsBuilder closedCases(long closedCases) {
                this.closedCases = closedCases;
                return this;
            }

            public InvestigatorProductivityMetricsBuilder averageResolutionTimeHours(double averageResolutionTimeHours) {
                this.averageResolutionTimeHours = averageResolutionTimeHours;
                return this;
            }

            public InvestigatorProductivityMetrics build() {
                InvestigatorProductivityMetrics metrics = new InvestigatorProductivityMetrics();
                metrics.totalCases = this.totalCases;
                metrics.closedCases = this.closedCases;
                metrics.averageResolutionTimeHours = this.averageResolutionTimeHours;
                return metrics;
            }
        }
    }
}

