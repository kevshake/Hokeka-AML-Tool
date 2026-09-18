package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.DeadlineUnit;
import com.posgateway.aml.entity.compliance.RegulatoryDeadlinePolicy;
import com.posgateway.aml.repository.compliance.RegulatoryDeadlinePolicyRepository;
import com.posgateway.aml.service.case_management.BusinessDayCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
public class RegulatoryDeadlineService {

    public static final String SAR_STR = "SAR_STR";

    private final RegulatoryDeadlinePolicyRepository policyRepository;
    private final BusinessDayCalculator businessDayCalculator;

    public RegulatoryDeadlineService(RegulatoryDeadlinePolicyRepository policyRepository,
            BusinessDayCalculator businessDayCalculator) {
        this.policyRepository = policyRepository;
        this.businessDayCalculator = businessDayCalculator;
    }

    @Transactional(readOnly = true)
    public DeadlineCalculation calculate(String reportType, String jurisdiction, Long pspId,
            LocalDateTime triggeringEventAt) {
        if (triggeringEventAt == null) {
            throw new IllegalArgumentException("A triggering event timestamp is required");
        }
        String normalizedJurisdiction = normalizeJurisdiction(jurisdiction);
        LocalDate eventDate = triggeringEventAt.toLocalDate();
        RegulatoryDeadlinePolicy policy = policyRepository.findByReportTypeAndActiveTrue(reportType).stream()
                .filter(candidate -> appliesOn(candidate, eventDate))
                .filter(candidate -> candidate.getPspId() == null || candidate.getPspId().equals(pspId))
                .filter(candidate -> candidate.getJurisdiction().equals(normalizedJurisdiction)
                        || candidate.getJurisdiction().equals("DEFAULT"))
                .max(Comparator
                        .comparingInt((RegulatoryDeadlinePolicy candidate) ->
                                candidate.getPspId() != null && candidate.getPspId().equals(pspId) ? 1 : 0)
                        .thenComparingInt(candidate -> candidate.getJurisdiction().equals(normalizedJurisdiction) ? 1 : 0)
                        .thenComparing(RegulatoryDeadlinePolicy::getEffectiveFrom))
                .orElseThrow(() -> new IllegalStateException(
                        "No active deadline policy for " + reportType + " in " + normalizedJurisdiction));

        LocalDateTime deadline = switch (policy.getDeadlineUnit()) {
            case HOURS -> triggeringEventAt.plusHours(policy.getDeadlineAmount());
            case CALENDAR_DAYS -> triggeringEventAt.plusDays(policy.getDeadlineAmount());
            case BUSINESS_DAYS -> businessDayCalculator.addBusinessDays(triggeringEventAt,
                    policy.getDeadlineAmount());
        };
        return new DeadlineCalculation(policy.getId(), policy.getPolicyCode(), deadline,
                policy.getWarningHours(), policy.getLegalReference());
    }

    private boolean appliesOn(RegulatoryDeadlinePolicy policy, LocalDate date) {
        return !date.isBefore(policy.getEffectiveFrom())
                && (policy.getEffectiveTo() == null || !date.isAfter(policy.getEffectiveTo()));
    }

    private String normalizeJurisdiction(String jurisdiction) {
        return jurisdiction == null || jurisdiction.isBlank()
                ? "DEFAULT"
                : jurisdiction.trim().toUpperCase();
    }

    public record DeadlineCalculation(Long policyId, String policyCode, LocalDateTime deadline,
            int warningHours, String legalReference) {}
}
