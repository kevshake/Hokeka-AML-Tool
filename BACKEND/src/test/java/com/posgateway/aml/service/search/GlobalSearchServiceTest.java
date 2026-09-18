package com.posgateway.aml.service.search;

import com.posgateway.aml.dto.search.GlobalSearchDtos.GlobalSearchResponse;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock private PspIsolationService pspIsolationService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private ComplianceCaseRepository complianceCaseRepository;
    @Mock private MerchantRepository merchantRepository;

    @InjectMocks
    private GlobalSearchService service;

    @Test
    void tenantScopedSearchUsesPspSpecificRepositoryMethods() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(7L);
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(100L);
        txn.setClientReference("INV-42");
        when(transactionRepository.searchGlobalForPsp(eq(7L), eq("INV"), any(Pageable.class)))
                .thenReturn(List.of(txn));
        when(alertRepository.searchGlobalForPsp(eq(7L), eq("INV"), any(Pageable.class))).thenReturn(List.of());
        when(complianceCaseRepository.searchGlobalForPsp(eq(7L), eq("INV"), any(Pageable.class))).thenReturn(List.of());
        when(merchantRepository.searchGlobalForPsp(eq(7L), eq("INV"), any(Pageable.class))).thenReturn(List.of());

        GlobalSearchResponse response = service.search("INV", 0, 8);

        assertThat(response.totalHits()).isEqualTo(1);
        assertThat(response.hits()).extracting("entityType").containsExactly("TRANSACTION");
        verify(transactionRepository, never()).searchGlobal(any(), any());
    }

    @Test
    void platformAdminUsesUnscopedRepositoryMethods() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(0L);
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(true);
        Alert alert = new Alert();
        alert.setAlertId(5L);
        alert.setReason("structuring");
        when(alertRepository.searchGlobal(eq("struct"), any(Pageable.class))).thenReturn(List.of(alert));
        when(transactionRepository.searchGlobal(eq("struct"), any(Pageable.class))).thenReturn(List.of());
        when(complianceCaseRepository.searchGlobal(eq("struct"), any(Pageable.class))).thenReturn(List.of());
        when(merchantRepository.searchGlobal(eq("struct"), any(Pageable.class))).thenReturn(List.of());

        GlobalSearchResponse response = service.search("struct", 0, 8);

        assertThat(response.hits()).extracting("entityType").containsExactly("ALERT");
        verify(alertRepository, never()).searchGlobalForPsp(any(), any(), any());
    }
}
