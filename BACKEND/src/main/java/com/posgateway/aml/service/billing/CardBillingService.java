package com.posgateway.aml.service.billing;

import com.posgateway.aml.entity.billing.PaymentAttempt;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PaymentAttemptRepository;
import com.posgateway.aml.repository.PspPaymentMethodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Service
public class CardBillingService {
    private final PspPaymentMethodRepository paymentMethods;
    private final PaymentAttemptRepository attempts;
    private final InvoiceRepository invoices;
    private final CardPaymentGatewayClient gateway;

    public CardBillingService(PspPaymentMethodRepository paymentMethods,
            PaymentAttemptRepository attempts, InvoiceRepository invoices,
            CardPaymentGatewayClient gateway) {
        this.paymentMethods = paymentMethods;
        this.attempts = attempts;
        this.invoices = invoices;
        this.gateway = gateway;
    }

    @Transactional
    public PaymentAttempt charge(Invoice invoice) {
        Long pspId = invoice.getPsp().getPspId();
        var method = paymentMethods.findFirstByPspPspIdAndActiveTrueOrderByCreatedAtDesc(pspId)
                .orElseThrow(() -> new IllegalStateException("No active card payment method"));
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setInvoiceId(invoice.getInvoiceId());
        attempt.setPspId(pspId);
        attempt.setPaymentMethod("CARD");
        attempt.setAmount(invoice.getTotalAmount());
        attempt.setCurrency(invoice.getCurrency());
        attempt.setStatus("PENDING");
        attempt = attempts.save(attempt);

        CardPaymentGatewayClient.ChargeResult result = gateway.charge(method.getTokenVaultRef(),
                invoice.getTotalAmount(), invoice.getCurrency(), invoice.getInvoiceNumber());
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setResultDescription(result.message());
        if (result.approved()) {
            attempt.setStatus("COMPLETED");
            invoice.markAsPaid(result.transactionId(), invoice.getTotalAmount());
            invoice.setPaymentMethod("CARD");
        } else {
            attempt.setStatus("FAILED");
            // Card failures enter the existing invoice dunning pipeline immediately.
            invoice.setStatus("OVERDUE");
        }
        invoices.save(invoice);
        return attempts.save(attempt);
    }
}
