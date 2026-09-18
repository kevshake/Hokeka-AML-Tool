package com.posgateway.aml.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.TransactionResultRequest;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionResultControllerTest {

    @Test
    void persistsChargebackAgainstSourceTransactionAndQueuesAudit() {
        TransactionRepository repository = mock(TransactionRepository.class);
        PspIsolationService isolationService = mock(PspIsolationService.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionEntity transaction = transaction("ALLOW");
        when(repository.findById(10L)).thenReturn(Optional.of(transaction));
        when(repository.save(transaction)).thenReturn(transaction);

        TransactionResultController controller = new TransactionResultController(
                repository, isolationService, outboxService, new ObjectMapper());
        ResponseEntity<Map<String, Object>> response = controller.ingestResult(
                new TransactionResultRequest(10L, "4", null, true, 5000L, null));

        assertEquals("BLOCK", transaction.getDecision());
        assertEquals("CRITICAL", transaction.getRiskLevel());
        assertEquals("CHARGEBACK", transaction.getBillClassificationCode());
        assertEquals("BLOCK", response.getBody().get("decision"));
        verify(isolationService).validateTransactionAccess(transaction);
        verify(repository).save(transaction);
        verify(outboxService).enqueue(
                eq("transaction.result:10:NO_RESPONSE:true"),
                eq("transactions.audit"),
                eq("2"),
                contains("TRANSACTION_RESULT_RECORDED"));
    }

    @Test
    void approvedCallbackDoesNotWeakenExistingBlock() {
        TransactionRepository repository = mock(TransactionRepository.class);
        TransactionEntity transaction = transaction("BLOCK");
        when(repository.findById(10L)).thenReturn(Optional.of(transaction));
        when(repository.save(transaction)).thenReturn(transaction);

        TransactionResultController controller = new TransactionResultController(
                repository, mock(PspIsolationService.class), mock(KafkaOutboxService.class), new ObjectMapper());
        controller.ingestResult(new TransactionResultRequest(10L, "4", "00", false, 5000L, null));

        assertEquals("BLOCK", transaction.getDecision());
        assertEquals("CRITICAL", transaction.getRiskLevel());
        assertEquals("00", transaction.getAcquirerResponse());
    }

    private TransactionEntity transaction(String decision) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(10L);
        transaction.setPspId(2L);
        transaction.setMerchantId("4");
        transaction.setAmountCents(5000L);
        transaction.setRiskLevel("LOW");
        transaction.setDecision(decision);
        return transaction;
    }
}
