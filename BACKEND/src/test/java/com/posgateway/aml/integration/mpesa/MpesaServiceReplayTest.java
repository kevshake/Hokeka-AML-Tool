package com.posgateway.aml.integration.mpesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.billing.PaymentAttempt;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PaymentAttemptRepository;
import com.posgateway.aml.service.psp.PspService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A replayed or duplicated Daraja callback must be a safe no-op: an attempt already in a terminal
 * state is never re-processed, so no invoice is re-marked PAID and no tenant is re-activated.
 */
@ExtendWith(MockitoExtension.class)
class MpesaServiceReplayTest {

    @Mock private MpesaProperties props;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private PspService pspService;

    private MpesaService service;

    @BeforeEach
    void setUp() {
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        service = new MpesaService(props, paymentAttemptRepository, invoiceRepository,
                new ObjectMapper(), webClientBuilder, pspService);
    }

    private Map<String, Object> successCallback(String checkoutId) {
        Map<String, Object> receipt = Map.of("Name", "MpesaReceiptNumber", "Value", "R123");
        Map<String, Object> amount = Map.of("Name", "Amount", "Value", 100);
        Map<String, Object> meta = Map.of("Item", List.of(receipt, amount));
        Map<String, Object> stk = new HashMap<>();
        stk.put("CheckoutRequestID", checkoutId);
        stk.put("ResultCode", 0);
        stk.put("ResultDesc", "The service request is processed successfully.");
        stk.put("CallbackMetadata", meta);
        return Map.of("Body", Map.of("stkCallback", stk));
    }

    @Test
    void replayedCallbackForSettledAttemptIsIgnored() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setStatus("SUCCESS"); // already settled
        attempt.setInvoiceId(9L);
        when(paymentAttemptRepository.findByMpesaCheckoutRequestId("abc")).thenReturn(Optional.of(attempt));

        service.processCallback(successCallback("abc"));

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
        verify(paymentAttemptRepository, never()).save(any());
        verify(pspService, never()).reactivateIfDuesCleared(any());
    }

    @Test
    void firstCallbackForProcessingAttemptSettlesAndReactivates() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setStatus("PROCESSING");
        attempt.setInvoiceId(9L);
        attempt.setPspId(3L);
        attempt.setAmount(new BigDecimal("100"));
        when(paymentAttemptRepository.findByMpesaCheckoutRequestId("abc")).thenReturn(Optional.of(attempt));
        Invoice invoice = new Invoice();
        when(invoiceRepository.findById(9L)).thenReturn(Optional.of(invoice));

        service.processCallback(successCallback("abc"));

        verify(invoiceRepository).save(invoice);
        verify(pspService).reactivateIfDuesCleared(eq(3L));
    }
}
