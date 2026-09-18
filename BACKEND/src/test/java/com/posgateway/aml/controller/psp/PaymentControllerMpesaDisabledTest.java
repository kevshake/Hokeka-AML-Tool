package com.posgateway.aml.controller.psp;

import com.posgateway.aml.dto.billing.PaymentInitiateRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.mpesa.MpesaService;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentControllerMpesaDisabledTest {
    @Test
    void mpesaInitiationIsUnavailableWhenDisabled() {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        Psp psp = new Psp();
        psp.setPspId(5L);
        Invoice invoice = Invoice.builder().psp(psp).invoiceNumber("I-1").status("SENT")
                .currency("KES").totalAmount(BigDecimal.TEN).build();
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        MpesaService mpesa = mock(MpesaService.class);
        PaymentController controller = new PaymentController(invoices,
                mock(PaymentAttemptRepository.class), mpesa, new MockEnvironment());
        ReflectionTestUtils.setField(controller, "mpesaEnabled", false);
        PaymentInitiateRequest request = new PaymentInitiateRequest();
        request.setInvoiceId(1L);
        request.setPaymentMethod("MPESA");
        User platformAdmin = new User();
        platformAdmin.setPsp(psp);

        var response = controller.initiatePayment(request, platformAdmin);

        assertEquals(503, response.getStatusCode().value());
        verifyNoInteractions(mpesa);
    }
}
