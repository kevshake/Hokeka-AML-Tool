package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.DeadlineUnit;
import com.posgateway.aml.entity.compliance.RegulatoryDeadlinePolicy;
import com.posgateway.aml.repository.compliance.RegulatoryDeadlinePolicyRepository;
import com.posgateway.aml.service.case_management.BusinessDayCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegulatoryDeadlineServiceTest {

    private final RegulatoryDeadlinePolicyRepository repository = mock(RegulatoryDeadlinePolicyRepository.class);
    private final BusinessDayCalculator businessDayCalculator = mock(BusinessDayCalculator.class);
    private final RegulatoryDeadlineService service = new RegulatoryDeadlineService(repository, businessDayCalculator);

    @Test
    void appliesKenyaTwoCalendarDayDeadlineFromSuspicionTimestamp() {
        RegulatoryDeadlinePolicy fallback = policy(1L, "DEFAULT-SAR-30D", "DEFAULT", null, 30,
                LocalDate.of(2020, 1, 1));
        RegulatoryDeadlinePolicy kenya = policy(2L, "KE-POCAMLA-STR-2D", "KE", null, 2,
                LocalDate.of(2023, 11, 17));
        when(repository.findByReportTypeAndActiveTrue(RegulatoryDeadlineService.SAR_STR))
                .thenReturn(List.of(fallback, kenya));
        LocalDateTime suspicion = LocalDateTime.of(2026, 7, 15, 10, 30);

        RegulatoryDeadlineService.DeadlineCalculation result = service.calculate(
                RegulatoryDeadlineService.SAR_STR, "ke", 45L, suspicion);

        assertThat(result.policyCode()).isEqualTo("KE-POCAMLA-STR-2D");
        assertThat(result.deadline()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 30));
        assertThat(result.warningHours()).isEqualTo(24);
    }

    @Test
    void pspSpecificPolicyTakesPrecedenceOverJurisdictionDefault() {
        RegulatoryDeadlinePolicy kenya = policy(2L, "KE-POCAMLA-STR-2D", "KE", null, 2,
                LocalDate.of(2023, 11, 17));
        RegulatoryDeadlinePolicy pspPolicy = policy(3L, "PSP-45-KE-STR-1D", "KE", 45L, 1,
                LocalDate.of(2026, 1, 1));
        when(repository.findByReportTypeAndActiveTrue(RegulatoryDeadlineService.SAR_STR))
                .thenReturn(List.of(kenya, pspPolicy));
        LocalDateTime suspicion = LocalDateTime.of(2026, 7, 15, 10, 30);

        RegulatoryDeadlineService.DeadlineCalculation result = service.calculate(
                RegulatoryDeadlineService.SAR_STR, "KE", 45L, suspicion);

        assertThat(result.policyCode()).isEqualTo("PSP-45-KE-STR-1D");
        assertThat(result.deadline()).isEqualTo(suspicion.plusDays(1));
    }

    private RegulatoryDeadlinePolicy policy(Long id, String code, String jurisdiction, Long pspId,
            int days, LocalDate effectiveFrom) {
        RegulatoryDeadlinePolicy policy = mock(RegulatoryDeadlinePolicy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getPolicyCode()).thenReturn(code);
        when(policy.getJurisdiction()).thenReturn(jurisdiction);
        when(policy.getPspId()).thenReturn(pspId);
        when(policy.getDeadlineAmount()).thenReturn(days);
        when(policy.getDeadlineUnit()).thenReturn(DeadlineUnit.CALENDAR_DAYS);
        when(policy.getWarningHours()).thenReturn(24);
        when(policy.getEffectiveFrom()).thenReturn(effectiveFrom);
        when(policy.getEffectiveTo()).thenReturn(null);
        when(policy.getLegalReference()).thenReturn("Test legal reference");
        return policy;
    }
}
