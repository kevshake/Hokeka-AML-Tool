package com.posgateway.aml.service.cbk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkGdiClient;
import com.posgateway.aml.integration.cbk.PspCbkContext;
import com.posgateway.aml.integration.cbk.records.FailedTransactionRecord;
import com.posgateway.aml.integration.cbk.records.MerchantTransactionRecord;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.compliance.CbkSubmissionRepository;
import com.posgateway.aml.repository.psp.cbk.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbkSubmissionOrchestratorTest {

    @Mock private PspRepository pspRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private CbkSubmissionRepository submissionRepository;
    @Mock private PspCbkConfigResolver configResolver;
    @Mock private CbkGdiClient cbkGdiClient;
    @Mock private PspSeniorManagementRepository seniorManagementRepository;
    @Mock private PspDirectorRepository directorRepository;
    @Mock private PspTrusteeRepository trusteeRepository;
    @Mock private PspShareholderRepository shareholderRepository;
    @Mock private PspCustomerComplaintRepository customerComplaintRepository;
    @Mock private PspProductRepository productRepository;
    @Mock private PspTariffTemplateRepository tariffTemplateRepository;
    @Mock private PspCyberIncidentRepository cyberIncidentRepository;
    @Mock private PspFraudIncidentRepository fraudIncidentRepository;
    @Mock private PspSystemInterruptionRepository systemInterruptionRepository;
    @Mock private PspTrustAccountRepository trustAccountRepository;
    @Mock private TransactionRepository transactionRepository;

    private CbkSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CbkSubmissionOrchestrator(
                pspRepository,
                submissionRepository,
                configResolver,
                cbkGdiClient,
                new ObjectMapper(),
                merchantRepository,
                seniorManagementRepository,
                directorRepository,
                trusteeRepository,
                shareholderRepository,
                customerComplaintRepository,
                productRepository,
                tariffTemplateRepository,
                cyberIncidentRepository,
                fraudIncidentRepository,
                systemInterruptionRepository,
                trustAccountRepository,
                transactionRepository);
        lenient().when(transactionRepository.findCardBrandSummaryForPsp(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(submissionRepository.save(any(CbkSubmission.class))).thenAnswer(invocation -> {
            CbkSubmission saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });
    }

    @Test
    void returnedClientFailureIsPersistedAndReturnedAsFailure() {
        PspCbkContext context = new PspCbkContext(7L, "BANK7", "client", "secret", false);
        when(cbkGdiClient.submitCardBrands(eq(context), anyList()))
                .thenReturn(com.posgateway.aml.integration.cbk.CbkSubmissionResult.failure(
                        "CBK unavailable", 503, "{}", 25, 0));

        CbkSubmissionResult result =
                orchestrator.executeEndpoint(context, CbkEndpointType.CARD_BRANDS);

        assertEquals(CbkSubmissionResult.Outcome.FAILURE, result.getOutcome());
        assertEquals(503, result.getHttpStatus());
        assertEquals("CBK unavailable", result.getErrorMessage());
        verify(submissionRepository).save(argThat(row ->
                row.getStatus() == CbkSubmission.Status.REJECTED
                        && row.getReferenceNumber() == null
                        && Integer.valueOf(0).equals(row.getSourceRecordCount())
                        && row.getErrorMessage().contains("CBK unavailable")));
    }

    @Test
    void acceptedClientResponseUsesRealCbkRequestNumber() {
        PspCbkContext context = new PspCbkContext(7L, "BANK7", "client", "secret", false);
        when(cbkGdiClient.submitCardBrands(eq(context), anyList()))
                .thenReturn(com.posgateway.aml.integration.cbk.CbkSubmissionResult.ok(
                        "CBK-REQ-991", 200, "{\"RequestNo\":\"CBK-REQ-991\"}", 18, 1));

        CbkSubmissionResult result =
                orchestrator.executeEndpoint(context, CbkEndpointType.CARD_BRANDS);

        assertEquals(CbkSubmissionResult.Outcome.SUCCESS, result.getOutcome());
        assertEquals(200, result.getHttpStatus());
        assertEquals("CBK-REQ-991", result.getReferenceNumber());
        verify(submissionRepository).save(argThat(row ->
                row.getStatus() == CbkSubmission.Status.ACCEPTED
                        && "CBK-REQ-991".equals(row.getReferenceNumber())
                        && Integer.valueOf(1).equals(row.getSourceRecordCount())));
    }

    @Test
    void merchantTransactionsUsePersistedSettlementFieldsAndActualChannel() {
        Psp psp = new Psp();
        psp.setPspId(7L);
        Merchant merchant = new Merchant();
        merchant.setMerchantId(44L);
        merchant.setPsp(psp);
        merchant.setCbkSettlementAccountNumber("SETTLEMENT-001");
        merchant.setContactEmail("merchant@example.com");
        merchant.setCbkEconomicSectorCode("G47");
        when(merchantRepository.findById(44L)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findSuccessfulYesterdayByPspId(eq(7L), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"44", "KEN", "MOBILE", 3L, 15_000L}));
        when(cbkGdiClient.submitMerchantTransactions(any(), anyList()))
                .thenReturn(com.posgateway.aml.integration.cbk.CbkSubmissionResult.ok(
                        "CBK-M-1", 200, "{\"RequestNo\":\"CBK-M-1\"}", 10, 1));

        CbkSubmissionResult result = orchestrator.executeEndpoint(
                new PspCbkContext(7L, "BANK7", "client", "secret", false),
                CbkEndpointType.MERCHANT_TRANSACTIONS);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<MerchantTransactionRecord>> records =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(cbkGdiClient).submitMerchantTransactions(any(), records.capture());
        MerchantTransactionRecord record = records.getValue().get(0);
        assertEquals("SETTLEMENT-001", record.getMerchantAccountNumber());
        assertEquals("MOBILE", record.getChannelOfSettlement());
        assertEquals("merchant@example.com", record.getEmailAddress());
        assertEquals("G47", record.getEconomicSectors());
        assertEquals("3", record.getNumberOfTransactions());
        assertEquals("150.00", record.getValueOfTransactions());
        verify(submissionRepository).save(argThat(row ->
                "CBK-M-1".equals(row.getReferenceNumber())
                        && Integer.valueOf(1).equals(row.getSourceRecordCount())));
    }

    @Test
    void failedTransactionsUseCustomerEvidenceAndRealRejectionReason() {
        TransactionEntity first = failedTransaction(1L, 5_000L);
        TransactionEntity second = failedTransaction(2L, 7_500L);
        when(transactionRepository.findFailedRejectedTransactionsForPspByDay(eq(7L), any(), any()))
                .thenReturn(List.of(first, second));
        when(cbkGdiClient.submitFailedTransactions(any(), anyList()))
                .thenReturn(com.posgateway.aml.integration.cbk.CbkSubmissionResult.ok(
                        "CBK-F-1", 200, "{\"RequestNo\":\"CBK-F-1\"}", 10, 1));

        CbkSubmissionResult result = orchestrator.executeEndpoint(
                new PspCbkContext(7L, "BANK7", "client", "secret", false),
                CbkEndpointType.FAILED_TRANSACTIONS);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<FailedTransactionRecord>> records =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(cbkGdiClient).submitFailedTransactions(any(), records.capture());
        FailedTransactionRecord record = records.getValue().get(0);
        assertEquals("CUSTOMER-TOKEN-7", record.getCustomerAccountNumber());
        assertEquals("ECOMMERCE", record.getChannelOfSettlement());
        assertEquals("customer@example.com", record.getEmail());
        assertEquals("05", record.getRejectionFailureReason());
        assertEquals("2", record.getNumberOfTransactions());
        assertEquals("125.00", record.getValueOfTransactions());
    }

    @Test
    void incompleteFailedTransactionIsRejectedBeforeCallingCbk() {
        TransactionEntity transaction = failedTransaction(1L, 5_000L);
        transaction.setCustomerAccountReference(null);
        when(transactionRepository.findFailedRejectedTransactionsForPspByDay(eq(7L), any(), any()))
                .thenReturn(List.of(transaction));

        CbkSubmissionResult result = orchestrator.executeEndpoint(
                new PspCbkContext(7L, "BANK7", "client", "secret", false),
                CbkEndpointType.FAILED_TRANSACTIONS);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("customerAccountReference"));
        verify(cbkGdiClient, never()).submitFailedTransactions(any(), anyList());
        verify(submissionRepository).save(argThat(row ->
                row.getStatus() == CbkSubmission.Status.REJECTED
                        && row.getReferenceNumber() == null
                        && row.getErrorMessage().contains("customerAccountReference")));
    }

    private TransactionEntity failedTransaction(Long id, long amountCents) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(id);
        transaction.setPspId(7L);
        transaction.setMerchantId("44");
        transaction.setAmountCents(amountCents);
        transaction.setDecision("DECLINED");
        transaction.setAcquirerResponse("05");
        transaction.setChannelType("ECOMMERCE");
        transaction.setCustomerAccountReference("CUSTOMER-TOKEN-7");
        transaction.setCustomerEmail("customer@example.com");
        return transaction;
    }
}
