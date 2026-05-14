package com.posgateway.aml.controller.psp;

import com.posgateway.aml.dto.billing.BankDetailsResponse;
import com.posgateway.aml.dto.billing.PaymentInitiateRequest;
import com.posgateway.aml.dto.billing.PaymentInitiateResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.billing.PaymentAttempt;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.integration.mpesa.MpesaService;
import com.posgateway.aml.integration.mpesa.MpesaStkResponse;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles invoice payment flows:
 * <ul>
 *   <li>POST /billing/payments/initiate — M-Pesa STK Push or bank transfer</li>
 *   <li>POST /billing/payments/mpesa/callback — public Safaricom webhook</li>
 *   <li>GET  /billing/payments/{invoiceId} — list attempts for an invoice</li>
 *   <li>GET  /billing/bank-details — bank transfer account details</li>
 * </ul>
 */
@RestController
@RequestMapping("/billing")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private static final Set<String> PAYABLE_STATUSES = Set.of("SENT", "OVERDUE");

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final MpesaService mpesaService;

    @Value("${billing.bank.name:Equity Bank Kenya}")
    private String bankName;

    @Value("${billing.bank.account-name:Hokeka AML Platform Ltd}")
    private String bankAccountName;

    @Value("${billing.bank.account-number:}")
    private String bankAccountNumber;

    @Value("${billing.bank.branch:Westlands Branch, Nairobi}")
    private String bankBranch;

    @Value("${billing.bank.swift:EQBLKENA}")
    private String bankSwift;

    public PaymentController(InvoiceRepository invoiceRepository,
                             PaymentAttemptRepository paymentAttemptRepository,
                             MpesaService mpesaService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.mpesaService = mpesaService;
    }

    // ─── POST /billing/payments/initiate ─────────────────────────────────────

    /**
     * Initiates a payment for an invoice.
     * Requires the caller to be authenticated and own the invoice (PSP_ADMIN)
     * or be a platform admin.
     */
    @PostMapping("/payments/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @RequestBody PaymentInitiateRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (request.getInvoiceId() == null || request.getPaymentMethod() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Invoice> optInvoice = invoiceRepository.findById(request.getInvoiceId());
        if (optInvoice.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Invoice invoice = optInvoice.get();

        // PSP scope guard — only own-PSP invoices unless admin
        if (!hasAdminRole(currentUser)) {
            Long callerPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (callerPspId == null || !callerPspId.equals(invoice.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }

        // Invoice must be in a payable state
        if (!PAYABLE_STATUSES.contains(invoice.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(new PaymentInitiateResponse(null, null, "REJECTED",
                            "Invoice status '" + invoice.getStatus() + "' is not payable. Only SENT or OVERDUE invoices can be paid."));
        }

        String method = request.getPaymentMethod().toUpperCase();
        Long pspId = invoice.getPsp().getPspId();

        return switch (method) {
            case "MPESA" -> handleMpesaPayment(request, invoice, pspId);
            case "BANK_TRANSFER" -> handleBankTransferPayment(request, invoice, pspId);
            default -> ResponseEntity.badRequest()
                    .body(new PaymentInitiateResponse(null, null, "REJECTED",
                            "Unsupported paymentMethod: " + method));
        };
    }

    private ResponseEntity<PaymentInitiateResponse> handleMpesaPayment(
            PaymentInitiateRequest request, Invoice invoice, Long pspId) {

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new PaymentInitiateResponse(null, null, "REJECTED", "phoneNumber is required for M-Pesa payment"));
        }

        // Create attempt record
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setInvoiceId(invoice.getInvoiceId());
        attempt.setPspId(pspId);
        attempt.setPaymentMethod("MPESA");
        attempt.setAmount(invoice.getTotalAmount());
        attempt.setCurrency(invoice.getCurrency() != null ? invoice.getCurrency() : "KES");
        attempt.setPhoneNumber(request.getPhoneNumber());
        attempt.setStatus("PENDING");
        attempt = paymentAttemptRepository.save(attempt);

        try {
            MpesaStkResponse stkResponse = mpesaService.initiateSTKPush(
                    request.getPhoneNumber(),
                    invoice.getTotalAmount(),
                    invoice.getInvoiceId(),
                    invoice.getInvoiceNumber());

            if (stkResponse.isSuccess()) {
                attempt.setMpesaCheckoutRequestId(stkResponse.getCheckoutRequestId());
                attempt.setMpesaMerchantRequestId(stkResponse.getMerchantRequestId());
                attempt.setStatus("PENDING");
                paymentAttemptRepository.save(attempt);

                log.info("STK push sent for invoice={}, attempt={}, checkoutId={}",
                        invoice.getInvoiceId(), attempt.getId(), stkResponse.getCheckoutRequestId());

                return ResponseEntity.ok(new PaymentInitiateResponse(
                        attempt.getId(),
                        stkResponse.getCheckoutRequestId(),
                        "PENDING",
                        "M-Pesa prompt sent to your phone. Complete the payment within 60 seconds."));
            } else {
                attempt.setStatus("FAILED");
                attempt.setResultDescription(stkResponse.getResponseDescription());
                attempt.setCompletedAt(OffsetDateTime.now());
                paymentAttemptRepository.save(attempt);

                return ResponseEntity.badRequest()
                        .body(new PaymentInitiateResponse(attempt.getId(), null, "FAILED",
                                "STK push rejected by M-Pesa: " + stkResponse.getResponseDescription()));
            }

        } catch (Exception e) {
            log.error("M-Pesa STK push error for invoice={}", invoice.getInvoiceId(), e);
            attempt.setStatus("FAILED");
            attempt.setResultDescription(e.getMessage());
            attempt.setCompletedAt(OffsetDateTime.now());
            paymentAttemptRepository.save(attempt);
            return ResponseEntity.internalServerError()
                    .body(new PaymentInitiateResponse(attempt.getId(), null, "FAILED",
                            "Could not initiate M-Pesa payment. Please try again."));
        }
    }

    private ResponseEntity<PaymentInitiateResponse> handleBankTransferPayment(
            PaymentInitiateRequest request, Invoice invoice, Long pspId) {

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setInvoiceId(invoice.getInvoiceId());
        attempt.setPspId(pspId);
        attempt.setPaymentMethod("BANK_TRANSFER");
        attempt.setAmount(invoice.getTotalAmount());
        attempt.setCurrency(invoice.getCurrency() != null ? invoice.getCurrency() : "KES");
        attempt.setBankReference(request.getBankReference());
        attempt.setStatus("PROCESSING");
        attempt.setResultDescription("Bank transfer reference submitted; pending admin verification.");
        attempt = paymentAttemptRepository.save(attempt);

        // Mark invoice as pending verification
        invoice.setStatus("PENDING_PAYMENT_VERIFICATION");
        if (request.getBankReference() != null) {
            invoice.setPaymentReference(request.getBankReference());
        }
        invoice.setPaymentMethod("BANK_TRANSFER");
        invoiceRepository.save(invoice);

        log.info("Bank transfer reference recorded for invoice={}, attempt={}, ref={}",
                invoice.getInvoiceId(), attempt.getId(), request.getBankReference());

        return ResponseEntity.ok(new PaymentInitiateResponse(
                attempt.getId(),
                null,
                "PROCESSING",
                "Bank transfer reference submitted. We'll verify and update your invoice status within 1-2 business days."));
    }

    // ─── POST /billing/payments/mpesa/callback — public Safaricom webhook ────

    /**
     * Publicly accessible endpoint that Safaricom calls with the STK push result.
     * Security is handled at the SecurityConfig level (permitAll for this path).
     */
    @PostMapping("/payments/mpesa/callback")
    public ResponseEntity<Map<String, String>> mpesaCallback(@RequestBody Map<String, Object> callbackBody) {
        log.info("Received Daraja callback");
        try {
            mpesaService.processCallback(callbackBody);
        } catch (Exception e) {
            // Always return 200 to Safaricom; failures are logged internally
            log.error("Error in Daraja callback processing", e);
        }
        // Daraja expects { "ResultCode": "00", "ResultDesc": "..." }
        return ResponseEntity.ok(Map.of("ResultCode", "00", "ResultDesc", "Success"));
    }

    // ─── GET /billing/payments/{invoiceId} ───────────────────────────────────

    /**
     * Lists all payment attempts for a given invoice.
     * PSP_ADMIN may only query invoices belonging to their own PSP.
     */
    @GetMapping("/payments/{invoiceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaymentAttempt>> listPaymentAttempts(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal User currentUser) {

        Optional<Invoice> optInvoice = invoiceRepository.findById(invoiceId);
        if (optInvoice.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Invoice invoice = optInvoice.get();

        if (!hasAdminRole(currentUser)) {
            Long callerPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (callerPspId == null || !callerPspId.equals(invoice.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }

        return ResponseEntity.ok(paymentAttemptRepository.findByInvoiceId(invoiceId));
    }

    // ─── GET /billing/bank-details ────────────────────────────────────────────

    /**
     * Returns the platform's bank transfer details for use in the payment dialog.
     */
    @GetMapping("/bank-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BankDetailsResponse> getBankDetails() {
        return ResponseEntity.ok(new BankDetailsResponse(
                bankName,
                bankAccountName,
                bankAccountNumber,
                bankBranch,
                bankSwift));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean hasAdminRole(User user) {
        if (user == null || user.getRole() == null) return false;
        String roleName = user.getRole().getName();
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }
}
