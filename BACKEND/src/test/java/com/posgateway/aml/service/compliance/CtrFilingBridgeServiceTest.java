package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CtrFilingBridgeServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ComplianceDeadlineRepository deadlineRepository;

    @InjectMocks
    private CtrFilingBridgeService service;

    @Test
    void createsDeadlineForReportableCtrTransaction() {
        TransactionEntity tx = new TransactionEntity();
        tx.setTxnId(99L);
        tx.setPspId(7L);
        tx.setMerchantId("m1");
        tx.setCtrRequired(true);
        tx.setCtrEvaluationStatus("REPORTABLE");
        tx.setCtrUsdEquivalent(new BigDecimal("16000"));
        tx.setTxnTs(LocalDateTime.parse("2026-03-10T12:00:00"));

        when(transactionRepository.findReportableCtrWithoutFilingDeadline(any(Pageable.class)))
                .thenReturn(List.of(tx));
        when(deadlineRepository.findBySourceTypeAndSourceId("TRANSACTION", 99L))
                .thenReturn(Optional.empty());

        service.bridgeDetectedCtrToFilingCalendar();

        ArgumentCaptor<com.posgateway.aml.entity.compliance.ComplianceDeadline> captor =
                ArgumentCaptor.forClass(com.posgateway.aml.entity.compliance.ComplianceDeadline.class);
        verify(deadlineRepository).save(captor.capture());
        assertEquals("CTR_FILING", captor.getValue().getDeadlineType());
        assertEquals(99L, captor.getValue().getSourceId());
    }
}
