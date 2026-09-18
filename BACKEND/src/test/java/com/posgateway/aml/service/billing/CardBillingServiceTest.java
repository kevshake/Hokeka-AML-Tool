package com.posgateway.aml.service.billing;

import com.posgateway.aml.entity.billing.PspPaymentMethod;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CardBillingServiceTest {
    @Test
    void failedCardChargeMovesInvoiceIntoDunning() {
        PspPaymentMethodRepository methods = mock(PspPaymentMethodRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        CardPaymentGatewayClient gateway = mock(CardPaymentGatewayClient.class);
        Psp psp = new Psp();
        psp.setPspId(7L);
        PspPaymentMethod method = new PspPaymentMethod();
        method.setTokenVaultRef("vault-token");
        when(methods.findFirstByPspPspIdAndActiveTrueOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(method));
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.charge(any(), any(), any(), any())).thenReturn(
                new CardPaymentGatewayClient.ChargeResult(false, null, "declined"));
        Invoice invoice = Invoice.builder().psp(psp).invoiceNumber("INV-7")
                .currency("USD").totalAmount(new BigDecimal("120.00")).status("SENT").build();

        var attempt = new CardBillingService(methods, attempts, invoices, gateway).charge(invoice);

        assertEquals("FAILED", attempt.getStatus());
        assertEquals("OVERDUE", invoice.getStatus());
        verify(invoices).save(invoice);
    }
}
