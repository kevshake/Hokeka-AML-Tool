package com.posgateway.aml.service.analytics;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Risk Analytics Service
 * Provides risk analysis and heatmap generation
 */
@Service
public class RiskAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAnalyticsService.class);

    private final ComplianceCaseRepository caseRepository;
    private final MerchantRepository merchantRepository;
    private final HighRiskCountryRepository highRiskCountryRepository;

    @Autowired
    public RiskAnalyticsService(ComplianceCaseRepository caseRepository,
                                MerchantRepository merchantRepository,
                                HighRiskCountryRepository highRiskCountryRepository) {
        this.caseRepository = caseRepository;
        this.merchantRepository = merchantRepository;
        this.highRiskCountryRepository = highRiskCountryRepository;
    }

    /**
     * Generate risk heatmap by customer
     */
    public Map<String, RiskHeatmapData> getCustomerRiskHeatmap(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceCase> cases = caseRepository.findByCreatedAtBetween(startDate, endDate);
        
        Map<String, RiskHeatmapData> heatmap = new HashMap<>();
        
        cases.forEach(complianceCase -> {
            Long merchantId = complianceCase.getMerchantId();
            if (merchantId != null) {
                String merchantIdStr = merchantId.toString();
                RiskHeatmapData data = heatmap.computeIfAbsent(merchantIdStr, 
                        k -> new RiskHeatmapData(merchantIdStr, "CUSTOMER"));
                
                data.incrementCaseCount();
                data.addRiskScore(getPriorityRiskScore(complianceCase.getPriority()));
            }
        });
        
        return heatmap;
    }

    /**
     * Generate risk heatmap by geography
     * Aggregates risk data by country based on merchants and cases
     */
    public Map<String, RiskHeatmapData> getGeographicRiskHeatmap(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, RiskHeatmapData> heatmap = new HashMap<>();
        
        // Get cases in date range
        List<ComplianceCase> cases = caseRepository.findByCreatedAtBetween(startDate, endDate);
        
        // Aggregate by merchant country
        Map<String, List<ComplianceCase>> casesByCountry = new HashMap<>();
        for (ComplianceCase complianceCase : cases) {
            Long merchantId = complianceCase.getMerchantId();
            if (merchantId != null) {
                Optional<Merchant> merchantOpt = merchantRepository.findById(merchantId);
                if (merchantOpt.isPresent()) {
                    Merchant merchant = merchantOpt.get();
                    String country = merchant.getCountry() != null ? merchant.getCountry() : "UNKNOWN";
                    casesByCountry.computeIfAbsent(country, k -> new ArrayList<>()).add(complianceCase);
                }
            }
        }
        
        // Build heatmap data
        for (Map.Entry<String, List<ComplianceCase>> entry : casesByCountry.entrySet()) {
            String country = entry.getKey();
            List<ComplianceCase> countryCases = entry.getValue();
            
            RiskHeatmapData data = new RiskHeatmapData(country, "GEOGRAPHY");
            data.caseCount = countryCases.size();
            
            // Calculate average risk score
            double totalRiskScore = countryCases.stream()
                    .mapToDouble(c -> getPriorityRiskScore(c.getPriority()))
                    .sum();
            data.averageRiskScore = countryCases.isEmpty() ? 0.0 : totalRiskScore / countryCases.size();
            
            // Add high-risk country bonus
            if (highRiskCountryRepository.existsByCountryCode(country)) {
                data.averageRiskScore = Math.min(1.0, data.averageRiskScore + 0.2);
            }
            
            heatmap.put(country, data);
        }
        
        // Also include countries with merchants but no cases (for completeness)
        List<Merchant> merchants = merchantRepository.findAll();
        for (Merchant merchant : merchants) {
            String country = merchant.getCountry();
            if (country != null && !heatmap.containsKey(country)) {
                RiskHeatmapData data = new RiskHeatmapData(country, "GEOGRAPHY");
                data.caseCount = 0;
                data.averageRiskScore = highRiskCountryRepository.existsByCountryCode(country) ? 0.3 : 0.1;
                heatmap.put(country, data);
            }
        }
        
        logger.debug("Generated geographic risk heatmap with {} countries", heatmap.size());
        return heatmap;
    }

    /**
     * Generate risk heatmap by merchant
     */
    public Map<String, RiskHeatmapData> getMerchantRiskHeatmap(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceCase> cases = caseRepository.findByCreatedAtBetween(startDate, endDate);
        
        Map<String, RiskHeatmapData> heatmap = new HashMap<>();
        
        cases.forEach(complianceCase -> {
            Long merchantId = complianceCase.getMerchantId();
            if (merchantId != null) {
                String merchantIdStr = merchantId.toString();
                RiskHeatmapData data = heatmap.computeIfAbsent(merchantIdStr, 
                        k -> new RiskHeatmapData(merchantIdStr, "MERCHANT"));
                
                data.incrementCaseCount();
                data.addRiskScore(getPriorityRiskScore(complianceCase.getPriority()));
            }
        });
        
        return heatmap;
    }

    /**
     * Analyze risk trends
     */
    public RiskTrendAnalysis analyzeRiskTrends(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceCase> cases = caseRepository.findByCreatedAtBetween(startDate, endDate);
        
        // Group by time period (weekly)
        Map<String, Long> weeklyTrends = new TreeMap<>();
        cases.forEach(complianceCase -> {
            String week = complianceCase.getCreatedAt().toLocalDate().toString().substring(0, 7); // YYYY-MM
            weeklyTrends.merge(week, 1L, Long::sum);
        });
        
        // Calculate trend direction
        String trendDirection = "STABLE";
        if (weeklyTrends.size() >= 2) {
            List<Long> values = new ArrayList<>(weeklyTrends.values());
            long firstHalf = values.subList(0, values.size() / 2).stream().mapToLong(Long::longValue).sum();
            long secondHalf = values.subList(values.size() / 2, values.size()).stream().mapToLong(Long::longValue).sum();
            
            if (secondHalf > firstHalf * 1.1) {
                trendDirection = "INCREASING";
            } else if (secondHalf < firstHalf * 0.9) {
                trendDirection = "DECREASING";
            }
        }
        
        return RiskTrendAnalysis.builder()
                .trendDirection(trendDirection)
                .weeklyTrends(weeklyTrends)
                .totalCases(cases.size())
                .highRiskCases(cases.stream()
                        .filter(c -> c.getPriority().ordinal() >= 2)
                        .count())
                .build();
    }

    /**
     * Calculate false positive rate
     */
    public double calculateFalsePositiveRate(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceCase> cases = caseRepository.findByCreatedAtBetween(startDate, endDate);
        
        long totalCases = cases.size();
        long falsePositives = cases.stream()
                .filter(c -> "CLEARED".equals(c.getResolution()))
                .count();
        
        return totalCases > 0 ? (double) falsePositives / totalCases * 100 : 0.0;
    }

    /**
     * Get priority risk score
     */
    private double getPriorityRiskScore(com.posgateway.aml.model.CasePriority priority) {
        return switch (priority) {
            case CRITICAL -> 1.0;
            case HIGH -> 0.75;
            case MEDIUM -> 0.5;
            case LOW -> 0.25;
        };
    }

    /**
     * Risk Heatmap Data
     */
    public static class RiskHeatmapData {
        private String id;
        private String type;
        private int caseCount;
        private double averageRiskScore;
        private List<Double> riskScores = new ArrayList<>();

        public RiskHeatmapData(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public void incrementCaseCount() {
            this.caseCount++;
        }

        public void addRiskScore(double score) {
            riskScores.add(score);
            this.averageRiskScore = riskScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        // Setters for direct assignment
        public void setCaseCount(int caseCount) {
            this.caseCount = caseCount;
        }

        public void setAverageRiskScore(double averageRiskScore) {
            this.averageRiskScore = averageRiskScore;
        }

        // Getters
        public String getId() { return id; }
        public String getType() { return type; }
        public int getCaseCount() { return caseCount; }
        public double getAverageRiskScore() { return averageRiskScore; }
    }

    /**
     * Risk Trend Analysis
     */
    public static class RiskTrendAnalysis {
        private String trendDirection;
        private Map<String, Long> weeklyTrends;
        private long totalCases;
        private long highRiskCases;

        public static RiskTrendAnalysisBuilder builder() {
            return new RiskTrendAnalysisBuilder();
        }

        // Getters and Setters
        public String getTrendDirection() { return trendDirection; }
        public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }
        public Map<String, Long> getWeeklyTrends() { return weeklyTrends; }
        public void setWeeklyTrends(Map<String, Long> weeklyTrends) { this.weeklyTrends = weeklyTrends; }
        public long getTotalCases() { return totalCases; }
        public void setTotalCases(long totalCases) { this.totalCases = totalCases; }
        public long getHighRiskCases() { return highRiskCases; }
        public void setHighRiskCases(long highRiskCases) { this.highRiskCases = highRiskCases; }

        public static class RiskTrendAnalysisBuilder {
            private String trendDirection;
            private Map<String, Long> weeklyTrends;
            private long totalCases;
            private long highRiskCases;

            public RiskTrendAnalysisBuilder trendDirection(String trendDirection) {
                this.trendDirection = trendDirection;
                return this;
            }

            public RiskTrendAnalysisBuilder weeklyTrends(Map<String, Long> weeklyTrends) {
                this.weeklyTrends = weeklyTrends;
                return this;
            }

            public RiskTrendAnalysisBuilder totalCases(long totalCases) {
                this.totalCases = totalCases;
                return this;
            }

            public RiskTrendAnalysisBuilder highRiskCases(long highRiskCases) {
                this.highRiskCases = highRiskCases;
                return this;
            }

            public RiskTrendAnalysis build() {
                RiskTrendAnalysis analysis = new RiskTrendAnalysis();
                analysis.trendDirection = this.trendDirection;
                analysis.weeklyTrends = this.weeklyTrends;
                analysis.totalCases = this.totalCases;
                analysis.highRiskCases = this.highRiskCases;
                return analysis;
            }
        }
    }
}

