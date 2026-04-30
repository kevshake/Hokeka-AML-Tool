package com.posgateway.aml.controller.psp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.posgateway.aml.dto.psp.InvoiceGenerationRequest;
import com.posgateway.aml.dto.psp.InvoiceResponse;
import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.service.psp.BillingService;
import com.posgateway.aml.mapper.InvoiceMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// @Slf4j removed
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;
    private final InvoiceMapper invoiceMapper;

    public BillingController(BillingService billingService, InvoiceMapper invoiceMapper) {
        this.billingService = billingService;
        this.invoiceMapper = invoiceMapper;
    }

    @GetMapping("/rates")
    public ResponseEntity<BillingRate> getRate(@RequestParam Long pspId, @RequestParam String serviceType) {
        Optional<BillingRate> rate = billingService.getEffectiveRate(pspId, serviceType);
        return rate.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<InvoiceResponse> generateInvoice(@RequestBody InvoiceGenerationRequest request) {
        log.info("Manual invoice generation trigger for PSP {}", request.getPspId());
        LocalDate periodStart = LocalDate.of(request.getYear(), request.getMonth(), 1);
        Invoice invoice = billingService.generateMonthlyInvoice(request.getPspId(), periodStart);
        return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
    }

    @GetMapping("/invoices")
    public org.springframework.http.ResponseEntity<List<Invoice>> listInvoices(
            @org.springframework.web.bind.annotation.RequestParam Long pspId) {
        return org.springframework.http.ResponseEntity.ok(billingService.getInvoicesByPsp(pspId));
    }
}
