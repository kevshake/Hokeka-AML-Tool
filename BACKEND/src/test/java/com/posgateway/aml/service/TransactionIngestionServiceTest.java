package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.enrichment.BinLookupService;
import com.posgateway.aml.service.enrichment.IpGeoService;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.risk.RiskScoringService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionIngestionServiceTest {

    @Test
    void persistsHashedPanAndQueuesRawEventAtomically() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionStatisticsService statisticsService = mock(TransactionStatisticsService.class);
        RiskScoringService riskScoringService = mock(RiskScoringService.class);
        IpGeoService ipGeoService = mock(IpGeoService.class);
        BinLookupService binLookupService = mock(BinLookupService.class);

        Merchant merchant = new Merchant();
        merchant.setMerchantId(4L);
        merchant.setCountry("KEN");
        when(merchantRepository.findById(4L)).thenReturn(Optional.of(merchant));
        when(ipGeoService.lookupCountry("196.1.1.1")).thenReturn(Optional.of("KEN"));
        when(riskScoringService.calculateKrs(merchant)).thenReturn(40.0);
        when(riskScoringService.calculateTrs(any(), any(), any())).thenReturn(null);
        when(riskScoringService.updateCra(null, null)).thenReturn(null);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setTxnId(88L);
            return transaction;
        });

        TransactionIngestionService service = new TransactionIngestionService(
                transactionRepository, merchantRepository, new ObjectMapper(), outboxService,
                statisticsService, riskScoringService, ipGeoService, binLookupService,
                new com.posgateway.aml.service.security.PiiLookupHasher("0123456789abcdef0123456789abcdef"));
        TransactionIngestionService.TransactionRequest request = new TransactionIngestionService.TransactionRequest();
        request.setMerchantId("4");
        request.setPan("4111111111111111");
        request.setAmountCents(125000L);
        request.setCurrency("kes");
        request.setIpAddress("196.1.1.1");
        request.setChannelType("MOBILE");
        request.setCashTransaction(true);
        request.setCustomerAccountReference("CUSTOMER-TOKEN-7");
        request.setCustomerEmail("customer@example.com");

        TransactionEntity saved = service.ingestTransaction(request);

        assertEquals(64, saved.getPanHash().length());
        assertNotEquals(request.getPan(), saved.getPanHash());
        assertEquals("KES", saved.getCurrency());
        assertEquals("CRITICAL", saved.getRiskLevel());
        assertEquals("BLOCK", saved.getDecision());
        assertEquals(true, saved.isCashTransaction());
        assertEquals("CUSTOMER-TOKEN-7", saved.getCustomerAccountReference());
        assertEquals("customer@example.com", saved.getCustomerEmail());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxService).enqueue(
                org.mockito.ArgumentMatchers.eq("transaction.raw:88"),
                org.mockito.ArgumentMatchers.eq("transactions.raw"),
                org.mockito.ArgumentMatchers.eq("0"),
                payload.capture());
        assertEquals(false, payload.getValue().contains("4111111111111111"));
        assertEquals(true, payload.getValue().contains("\"cashTransaction\":true"));
    }

    @Test
    void rejectsInvalidTransactionBeforePersistence() {
        TransactionIngestionService service = new TransactionIngestionService(
                mock(TransactionRepository.class), mock(MerchantRepository.class), new ObjectMapper(),
                mock(KafkaOutboxService.class), mock(TransactionStatisticsService.class),
                mock(RiskScoringService.class), mock(IpGeoService.class), mock(BinLookupService.class),
                new com.posgateway.aml.service.security.PiiLookupHasher("0123456789abcdef0123456789abcdef"));
        TransactionIngestionService.TransactionRequest request = new TransactionIngestionService.TransactionRequest();
        request.setMerchantId("merchant-four");
        request.setAmountCents(0L);
        request.setCurrency("KES");

        assertThrows(IllegalArgumentException.class, () -> service.ingestTransaction(request));
    }
}
