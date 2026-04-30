package com.posgateway.aml.service.analytics;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.service.case_management.CaseSlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compliance Dashboard Service
 * Provides real-time metrics for compliance officers
 */
@Service
public class ComplianceDashboardService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(ComplianceDashboardService.class);

    private final ComplianceCaseRepository caseRepository;
    private final CaseSlaService slaService;

    @Autowired
    public ComplianceDashboardService(ComplianceCaseRepository caseRepository,
                                     CaseSlaService slaService) {
        this.caseRepository = caseRepository;
        this.slaService = slaService;
    }

    /**
     * Get compliance dashboard metrics
     */
    public ComplianceDashboardDTO getDashboardMetrics() {
        ComplianceDashboardDTO dto = new ComplianceDashboardDTO();

        // Open cases by status
        dto.setOpenCasesByStatus(getOpenCasesByStatus());

        // Cases by priority
        dto.setCasesByPriority(getCasesByPriority());

        // Cases approaching SLA deadline
        dto.setCasesApproachingDeadline(slaService.getCasesApproachingDeadline(24));

        // Cases with breached SLA
        dto.setBreachedCases(slaService.getBreachedCases());

        // Unassigned cases
        dto.setUnassignedCases(caseRepository.findByStatus(CaseStatus.NEW));

        // Today's high-risk alerts
        dto.setTodayHighRiskCases(getTodayHighRiskCases());

        // Team workload distribution (placeholder - implement based on your user structure)
        dto.setTeamWorkload(getTeamWorkload());

        return dto;
    }

    /**
     * Get open cases grouped by status
     */
    private Map<String, Long> getOpenCasesByStatus() {
        Map<String, Long> statusCounts = new HashMap<>();
        
        List<CaseStatus> openStatuses = List.of(
                CaseStatus.NEW,
                CaseStatus.ASSIGNED,
                CaseStatus.IN_PROGRESS,
                CaseStatus.PENDING_REVIEW,
                CaseStatus.ESCALATED,
                CaseStatus.PENDING_INFO
        );

        for (CaseStatus status : openStatuses) {
            statusCounts.put(status.name(), caseRepository.countByStatus(status));
        }

        return statusCounts;
    }

    /**
     * Get cases grouped by priority
     */
    private Map<String, Long> getCasesByPriority() {
        Map<String, Long> priorityCounts = new HashMap<>();
        
        for (CasePriority priority : CasePriority.values()) {
            long count = caseRepository.countByPriority(priority);
            priorityCounts.put(priority.name(), count);
        }

        return priorityCounts;
    }

    /**
     * Get today's high-risk cases
     */
    private List<ComplianceCase> getTodayHighRiskCases() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        return caseRepository.findByCreatedAtBetween(todayStart, todayEnd).stream()
                .filter(c -> c.getPriority() == CasePriority.HIGH || c.getPriority() == CasePriority.CRITICAL)
                .toList();
    }

    /**
     * Get team workload distribution
     */
    private Map<String, Integer> getTeamWorkload() {
        // TODO: Implement based on your user/team structure
        return new HashMap<>();
    }

    /**
     * Compliance Dashboard DTO
     */
    public static class ComplianceDashboardDTO {
        private Map<String, Long> openCasesByStatus;
        private Map<String, Long> casesByPriority;
        private List<ComplianceCase> casesApproachingDeadline;
        private List<ComplianceCase> breachedCases;
        private List<ComplianceCase> unassignedCases;
        private List<ComplianceCase> todayHighRiskCases;
        private Map<String, Integer> teamWorkload;

        // Getters and Setters
        public Map<String, Long> getOpenCasesByStatus() {
            return openCasesByStatus;
        }

        public void setOpenCasesByStatus(Map<String, Long> openCasesByStatus) {
            this.openCasesByStatus = openCasesByStatus;
        }

        public Map<String, Long> getCasesByPriority() {
            return casesByPriority;
        }

        public void setCasesByPriority(Map<String, Long> casesByPriority) {
            this.casesByPriority = casesByPriority;
        }

        public List<ComplianceCase> getCasesApproachingDeadline() {
            return casesApproachingDeadline;
        }

        public void setCasesApproachingDeadline(List<ComplianceCase> casesApproachingDeadline) {
            this.casesApproachingDeadline = casesApproachingDeadline;
        }

        public List<ComplianceCase> getBreachedCases() {
            return breachedCases;
        }

        public void setBreachedCases(List<ComplianceCase> breachedCases) {
            this.breachedCases = breachedCases;
        }

        public List<ComplianceCase> getUnassignedCases() {
            return unassignedCases;
        }

        public void setUnassignedCases(List<ComplianceCase> unassignedCases) {
            this.unassignedCases = unassignedCases;
        }

        public List<ComplianceCase> getTodayHighRiskCases() {
            return todayHighRiskCases;
        }

        public void setTodayHighRiskCases(List<ComplianceCase> todayHighRiskCases) {
            this.todayHighRiskCases = todayHighRiskCases;
        }

        public Map<String, Integer> getTeamWorkload() {
            return teamWorkload;
        }

        public void setTeamWorkload(Map<String, Integer> teamWorkload) {
            this.teamWorkload = teamWorkload;
        }
    }
}

