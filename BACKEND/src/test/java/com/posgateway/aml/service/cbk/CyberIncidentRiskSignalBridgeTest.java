package com.posgateway.aml.service.cbk;

import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.entity.multiasset.MultiAssetRiskSignal;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import com.posgateway.aml.entity.multiasset.ProductDomain;
import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import com.posgateway.aml.repository.multiasset.MultiAssetRiskSignalRepository;
import com.posgateway.aml.repository.multiasset.MultiAssetTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CyberIncidentRiskSignalBridgeTest {

    @Mock private MultiAssetTransactionRepository transactionRepository;
    @Mock private MultiAssetRiskSignalRepository signalRepository;

    @InjectMocks
    private CyberIncidentRiskSignalBridge bridge;

    @Test
    void emitsCyberSignalWhenMetadataLinksTransaction() {
        PspCyberIncident incident = PspCyberIncident.builder()
                .id(9L)
                .pspId(3L)
                .incidentNumber("CYB-001")
                .build();
        MultiAssetCustomer customer = new MultiAssetCustomer();
        customer.setId(11L);
        MultiAssetTransaction transaction = new MultiAssetTransaction();
        transaction.setId(22L);
        transaction.setCustomer(customer);
        transaction.setProductDomain(ProductDomain.E_MONEY_MOBILE_MONEY);

        when(transactionRepository.findLinkedToCyberIncident(3L, 9L, "CYB-001")).thenReturn(List.of(transaction));
        when(signalRepository.existsByTransactionIdAndSignalCode(22L, CyberIncidentRiskSignalBridge.SIGNAL_CODE))
                .thenReturn(false);

        int created = bridge.emitSignalsForIncident(incident);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<MultiAssetRiskSignal> captor = ArgumentCaptor.forClass(MultiAssetRiskSignal.class);
        verify(signalRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(FinancialCrimeSignalType.CYBER);
    }

    @Test
    void skipsWhenNoLinkedTransactions() {
        PspCyberIncident incident = PspCyberIncident.builder()
                .id(1L)
                .pspId(2L)
                .incidentNumber("CYB-EMPTY")
                .build();
        when(transactionRepository.findLinkedToCyberIncident(2L, 1L, "CYB-EMPTY")).thenReturn(List.of());

        assertThat(bridge.emitSignalsForIncident(incident)).isZero();
        verify(signalRepository, never()).save(any());
    }
}
