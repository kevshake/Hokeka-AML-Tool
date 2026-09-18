package com.posgateway.aml.service.graph;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.graph.MerchantNode;
import com.posgateway.aml.entity.graph.TransactionNode;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.graph.MerchantNodeRepository;
import com.posgateway.aml.repository.graph.TransactionNodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class Neo4jGraphIngestionServiceTest {

    @Test
    void projectsRealMerchantAndTransactionValues() {
        MerchantNodeRepository merchantNodeRepository = mock(MerchantNodeRepository.class);
        TransactionNodeRepository transactionNodeRepository = mock(TransactionNodeRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);

        Merchant merchant = new Merchant();
        merchant.setMerchantId(12L);
        merchant.setLegalName("Acme Payments Limited");
        merchant.setTradingName("Acme Pay");
        merchant.setMcc("6012");
        merchant.setCountry("KEN");
        merchant.setRiskLevel("HIGH");
        when(merchantRepository.findById(12L)).thenReturn(Optional.of(merchant));
        when(merchantNodeRepository.findByMerchantId("12")).thenReturn(Optional.empty());
        when(merchantNodeRepository.save(org.mockito.ArgumentMatchers.any(MerchantNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionNodeRepository.findById("99")).thenReturn(Optional.empty());
        when(transactionRepository.findByPanHash(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(99L);
        transaction.setMerchantId("12");
        transaction.setAmountCents(125050L);
        transaction.setCurrency("KES");
        transaction.setChannelType("MOBILE");
        transaction.setTrs(87.0);
        transaction.setDecision("HOLD");

        Neo4jGraphIngestionService service = new Neo4jGraphIngestionService(
                merchantNodeRepository, transactionNodeRepository, merchantRepository, transactionRepository);
        service.ingestTransaction(transaction);

        ArgumentCaptor<MerchantNode> merchantCaptor = ArgumentCaptor.forClass(MerchantNode.class);
        verify(merchantNodeRepository).save(merchantCaptor.capture());
        assertEquals("Acme Payments Limited", merchantCaptor.getValue().getLegalName());
        assertEquals("6012", merchantCaptor.getValue().getMcc());
        assertEquals("KEN", merchantCaptor.getValue().getCountry());

        ArgumentCaptor<TransactionNode> transactionCaptor = ArgumentCaptor.forClass(TransactionNode.class);
        verify(transactionNodeRepository).save(transactionCaptor.capture());
        assertEquals("MOBILE", transactionCaptor.getValue().getChannel());
        assertEquals("1250.50", transactionCaptor.getValue().getAmount().toPlainString());
        assertEquals("HOLD", transactionCaptor.getValue().getDecision());
        assertSame(merchantCaptor.getValue(), transactionCaptor.getValue().getFromMerchant());
    }

    @Test
    void linksOnlyMerchantsFromTheSamePspForSharedInstrument() {
        MerchantNodeRepository merchantNodeRepository = mock(MerchantNodeRepository.class);
        TransactionNodeRepository transactionNodeRepository = mock(TransactionNodeRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);

        Psp psp = new Psp();
        psp.setPspId(5L);
        Merchant source = merchant(12L, "Source Merchant", psp);
        Merchant related = merchant(13L, "Related Merchant", psp);
        when(merchantRepository.findById(12L)).thenReturn(Optional.of(source));
        when(merchantRepository.findById(13L)).thenReturn(Optional.of(related));
        when(merchantNodeRepository.findByMerchantId(anyString())).thenReturn(Optional.empty());
        when(merchantNodeRepository.save(org.mockito.ArgumentMatchers.any(MerchantNode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionNodeRepository.findById("99")).thenReturn(Optional.empty());

        TransactionEntity previous = new TransactionEntity();
        previous.setMerchantId("13");
        when(transactionRepository.findByPanHash("hash-1")).thenReturn(java.util.List.of(previous));

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(99L);
        transaction.setMerchantId("12");
        transaction.setPanHash("hash-1");
        transaction.setAmountCents(50000L);
        transaction.setCurrency("KES");

        Neo4jGraphIngestionService service = new Neo4jGraphIngestionService(
                merchantNodeRepository, transactionNodeRepository, merchantRepository, transactionRepository);
        service.ingestTransaction(transaction);

        ArgumentCaptor<MerchantNode> nodes = ArgumentCaptor.forClass(MerchantNode.class);
        verify(merchantNodeRepository, org.mockito.Mockito.atLeastOnce()).save(nodes.capture());
        MerchantNode sourceNode = nodes.getAllValues().stream()
                .filter(node -> "12".equals(node.getMerchantId()))
                .findFirst()
                .orElseThrow();
        assertTrue(sourceNode.getTransactsWith().stream()
                .anyMatch(link -> "13".equals(link.getTarget().getMerchantId())));
    }

    private Merchant merchant(Long id, String legalName, Psp psp) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setLegalName(legalName);
        merchant.setMcc("6012");
        merchant.setCountry("KEN");
        merchant.setPsp(psp);
        return merchant;
    }
}
