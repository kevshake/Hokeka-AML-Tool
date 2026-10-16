package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.AlertTransactionLink;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.compliance.SarAlertLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceEntityLinkServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private AlertTransactionLinkRepository alertTransactionLinkRepository;
    @Mock private SarAlertLinkRepository sarAlertLinkRepository;
    @Mock private CaseTransactionRepository caseTransactionRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private ComplianceEntityLinkService service;

    @Test
    void registerNewFraudAlertStoresPanHashAndTxnLink() {
        Alert alert = new Alert();
        alert.setAlertId(10L);
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(99L);
        txn.setPanHash("hash-abc");

        when(alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(10L)).thenReturn(List.of());
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.registerNewFraudAlert(alert, txn);

        assertEquals("hash-abc", alert.getPanHash());
        ArgumentCaptor<AlertTransactionLink> captor = ArgumentCaptor.forClass(AlertTransactionLink.class);
        verify(alertTransactionLinkRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getAlertId());
        assertEquals(99L, captor.getValue().getTxnId());
        assertTrue(captor.getValue().isPrimaryLink());
    }
}
