package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeriodicKycRefreshServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private CustomerRiskProfilingService riskProfilingService;
    @Mock private KycCompletenessService completenessService;

    private PeriodicKycRefreshService service;

    @BeforeEach
    void setUp() {
        service = new PeriodicKycRefreshService(
                merchantRepository, riskProfilingService, completenessService);
        ReflectionTestUtils.setField(service, "refreshEnabled", true);
        ReflectionTestUtils.setField(service, "highRiskRefreshDays", 90);
        ReflectionTestUtils.setField(service, "mediumRiskRefreshDays", 180);
        ReflectionTestUtils.setField(service, "lowRiskRefreshDays", 365);
    }

    @Test
    void refreshPersistsLastCddReviewTimestamp() {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(7L);
        merchant.setStatus("ACTIVE");
        merchant.setRiskLevel("HIGH");
        merchant.setRegistrationDate(LocalDate.now().minusYears(2));
        when(merchantRepository.findByStatus("ACTIVE")).thenReturn(List.of(merchant));
        when(merchantRepository.save(merchant)).thenReturn(merchant);

        service.performPeriodicKycRefresh();

        verify(riskProfilingService).calculateRiskRating("7");
        verify(completenessService).calculateCompletenessScore(7L);
        verify(merchantRepository).save(merchant);
        assertNotNull(merchant.getLastCddReviewAt());
    }

    @Test
    void recentCddReviewPreventsDuplicateRefresh() {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(8L);
        merchant.setStatus("ACTIVE");
        merchant.setRiskLevel("HIGH");
        merchant.setRegistrationDate(LocalDate.now().minusYears(2));
        merchant.setLastCddReviewAt(LocalDateTime.now().minusDays(5));
        when(merchantRepository.findByStatus("ACTIVE")).thenReturn(List.of(merchant));

        service.performPeriodicKycRefresh();

        verifyNoInteractions(riskProfilingService, completenessService);
        verify(merchantRepository, never()).save(any());
    }
}
