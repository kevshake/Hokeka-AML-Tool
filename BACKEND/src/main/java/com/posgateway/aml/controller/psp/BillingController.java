package com.posgateway.aml.controller.psp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.posgateway.aml.dto.billing.InvoiceStatusUpdateRequest;
import com.posgateway.aml.dto.billing.RevenueSummaryResponse;
import com.posgateway.aml.dto.billing.UsageSummaryResponse;
import com.posgateway.aml.dto.billing.UsageSummaryResponse.ServiceBreakdown;
import com.posgateway.aml.dto.psp.InvoiceGenerationRequest;
import com.posgateway.aml.dto.psp.InvoiceResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.mapper.InvoiceMapper;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.service.billing.InvoicePdfService;
import com.posgateway.aml.service.psp.BillingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final InvoicePdfService invoicePdfService;

    public BillingController(BillingService billingService,
                             InvoiceMapper invoiceMapper,
                             InvoiceRepository invoiceRepository,
                             SubscriptionRepository subscriptionRepository,
                             ApiUsageLogRepository apiUsageLogRepository,
                             InvoicePdfService invoicePdfService) {
        this.billingService = billingService;
        this.invoiceMapper = invoiceMapper;
        this.invoiceRepository = invoiceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.invoicePdfService = invoicePdfService;
    }

    // =========================================================================
    // Existing endpoints (unchanged)
    // =========================================================================

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

    /**
     * GET /billing/invoices
     * PSP_ADMIN sees only their own PSP; ADMIN/SUPER_ADMIN see all (or filter by pspId param).
     */
    @GetMapping("/invoices")
    public ResponseEntity<List<Invoice>> listInvoices(
            @RequestParam(required = false) Long pspId,
            @AuthenticationPrincipal User currentUser) {
        boolean isAdmin = hasAdminRole(currentUser);
        if (isAdmin && pspId != null) {
            return ResponseEntity.ok(billingService.getInvoicesByPsp(pspId));
        }
        if (isAdmin) {
            return ResponseEntity.ok(invoiceRepository.findAll());
        }
        // PSP_ADMIN — scope to own PSP
        Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
        if (ownPspId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(billingService.getInvoicesByPsp(ownPspId));
    }

    // =========================================================================
    // Invoice management
    // =========================================================================

    /**
     * GET /billing/invoices/overdue — admin only.
     * Mapped before /{invoiceId} to avoid path ambiguity with Spring MVC.
     */
    @GetMapping("/invoices/overdue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<Invoice>> listOverdueInvoices() {
        List<Invoice> sentPastDue = invoiceRepository.findOverdueInvoices("SENT", LocalDate.now());
        List<Invoice> markedOverdue = invoiceRepository.findByStatus("OVERDUE");
        List<Invoice> combined = new ArrayList<>(sentPastDue);
        markedOverdue.stream()
                .filter(i -> combined.stream().noneMatch(e -> e.getInvoiceId().equals(i.getInvoiceId())))
                .forEach(combined::add);
        return ResponseEntity.ok(combined);
    }

    /**
     * GET /billing/invoices/{invoiceId}
     * PSP_ADMIN can only read their own PSP's invoice; ADMIN sees all.
     */
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<Invoice> getInvoice(@PathVariable Long invoiceId,
                                              @AuthenticationPrincipal User currentUser) {
        Optional<Invoice> opt = invoiceRepository.findById(invoiceId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Invoice invoice = opt.get();
        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (ownPspId == null || !ownPspId.equals(invoice.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(invoice);
    }

    /**
     * PUT /billing/invoices/{invoiceId}/status
     * Accepts { status, paymentReference, paymentMethod, paymentAmount }.
     * SUPER_ADMIN and ADMIN only.
     */
    @PutMapping("/invoices/{invoiceId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Invoice> updateInvoiceStatus(@PathVariable Long invoiceId,
                                                       @RequestBody InvoiceStatusUpdateRequest request) {
        Optional<Invoice> opt = invoiceRepository.findById(invoiceId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Invoice invoice = opt.get();
        String status = request.getStatus();
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        switch (status.toUpperCase()) {
            case "PAID":
                invoice.markAsPaid(request.getPaymentReference(), request.getPaymentAmount());
                if (request.getPaymentMethod() != null) {
                    invoice.setPaymentMethod(request.getPaymentMethod());
                }
                break;
            case "SENT":
                invoice.markAsSent();
                break;
            case "CANCELLED":
            case "VOID":
            case "OVERDUE":
                invoice.setStatus(status.toUpperCase());
                break;
            default:
                log.warn("Unrecognised status transition requested: {}", status);
                return ResponseEntity.badRequest().build();
        }
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice {} status updated to {}", invoiceId, saved.getStatus());
        return ResponseEntity.ok(saved);
    }

    /**
     * GET /billing/invoices/{invoiceId}/download
     * Returns full invoice JSON for PDF rendering on the frontend.
     * PSP_ADMIN scoped to own PSP.
     */
    @GetMapping("/invoices/{invoiceId}/download")
    public ResponseEntity<Invoice> downloadInvoice(@PathVariable Long invoiceId,
                                                   @AuthenticationPrincipal User currentUser) {
        Optional<Invoice> opt = invoiceRepository.findById(invoiceId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Invoice invoice = opt.get();
        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (ownPspId == null || !ownPspId.equals(invoice.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + invoice.getInvoiceNumber() + ".json\"")
                .body(invoice);
    }

    /**
     * GET /billing/invoices/{invoiceId}/pdf
     * Download invoice as a PDF binary attachment.
     * SUPER_ADMIN/ADMIN can download any invoice; PSP_ADMIN only their own PSP's invoices.
     */
    @GetMapping("/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal User currentUser) {

        Optional<Invoice> opt = invoiceRepository.findById(invoiceId);
        if (opt.isEmpty()) {
            log.warn("Invoice {} not found — PDF download denied", invoiceId);
            return ResponseEntity.notFound().build();
        }

        Invoice invoice = opt.get();

        // PSP_ADMIN scope guard: may only access their own PSP's invoices
        if (!hasAdminRole(currentUser)) {
            Long callerPspId  = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            Long invoicePspId = invoice.getPsp() != null ? invoice.getPsp().getPspId() : null;
            if (callerPspId == null || !callerPspId.equals(invoicePspId)) {
                log.warn("PSP_ADMIN {} attempted to download invoice {} belonging to a different PSP",
                        currentUser.getUsername(), invoiceId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        log.info("Generating PDF for invoice {} — requested by {}", invoice.getInvoiceNumber(), currentUser.getUsername());
        byte[] pdf = invoicePdfService.generateInvoicePdf(invoice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice-" + invoice.getInvoiceNumber() + ".pdf");
        headers.setContentLength(pdf.length);

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    // =========================================================================
    // Usage reporting
    // =========================================================================

    /**
     * GET /billing/usage/{pspId}?month=YYYY-MM
     * Returns usage summary grouped by service type for a specific month.
     * PSP_ADMIN can only query their own pspId.
     */
    @GetMapping("/usage/{pspId}")
    public ResponseEntity<UsageSummaryResponse> getUsageSummary(
            @PathVariable Long pspId,
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal User currentUser) {

        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (!pspId.equals(ownPspId)) {
                return ResponseEntity.status(403).build();
            }
        }

        YearMonth ym;
        if (month != null && !month.isBlank()) {
            try {
                ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            ym = YearMonth.now();
        }

        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        List<Object[]> rows = apiUsageLogRepository.getUsageSummaryByService(pspId, start, end);
        long totalRequests = apiUsageLogRepository.countAllByPspAndPeriod(pspId, start, end);
        long billableRequests = apiUsageLogRepository.countBillableRequests(pspId, start, end);
        BigDecimal totalCost = apiUsageLogRepository.sumCostByPspAndPeriod(pspId, start, end);
        if (totalCost == null) {
            totalCost = BigDecimal.ZERO;
        }

        List<ServiceBreakdown> breakdown = rows.stream()
                .map(row -> new ServiceBreakdown(
                        (String) row[0],
                        (Long) row[1],
                        row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                new UsageSummaryResponse(pspId, ym.toString(), totalRequests, billableRequests, totalCost, breakdown));
    }

    /**
     * GET /billing/usage/{pspId}/current
     * Live usage count for the current month (up to this instant).
     * PSP_ADMIN scoped to own pspId.
     */
    @GetMapping("/usage/{pspId}/current")
    public ResponseEntity<UsageSummaryResponse> getCurrentMonthUsage(
            @PathVariable Long pspId,
            @AuthenticationPrincipal User currentUser) {

        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (!pspId.equals(ownPspId)) {
                return ResponseEntity.status(403).build();
            }
        }

        YearMonth current = YearMonth.now();
        LocalDateTime start = current.atDay(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now(); // live — up to this instant

        List<Object[]> rows = apiUsageLogRepository.getUsageSummaryByService(pspId, start, end);
        long billableRequests = apiUsageLogRepository.countBillableRequests(pspId, start, end);
        BigDecimal totalCost = apiUsageLogRepository.sumCostByPspAndPeriod(pspId, start, end);
        if (totalCost == null) {
            totalCost = BigDecimal.ZERO;
        }

        long totalRequests = apiUsageLogRepository.countAllByPspAndPeriod(pspId, start, end);

        List<ServiceBreakdown> breakdown = rows.stream()
                .map(row -> new ServiceBreakdown(
                        (String) row[0],
                        (Long) row[1],
                        row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                new UsageSummaryResponse(pspId, current + " (live)", totalRequests, billableRequests, totalCost, breakdown));
    }

    // =========================================================================
    // Revenue dashboard — admin only
    // =========================================================================

    /**
     * GET /billing/revenue/summary
     * Platform-level revenue KPIs for the current calendar month.
     */
    @GetMapping("/revenue/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<RevenueSummaryResponse> getRevenueSummary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        BigDecimal paidThisMonth = invoiceRepository.sumPaidAmountForPeriod(monthStart, monthEnd);
        BigDecimal expectedThisMonth = invoiceRepository.sumExpectedAmountForPeriod(monthStart, monthEnd);
        BigDecimal overdueAmount = invoiceRepository.sumOverdueAmount(today);

        long activeSubscriptions = subscriptionRepository.findAllActive().size();
        long paidInvoicesCount = invoiceRepository.countByStatus("PAID");
        List<Invoice> sentPastDue = invoiceRepository.findOverdueInvoices("SENT", today);
        long overdueCount = sentPastDue.size() + invoiceRepository.countByStatus("OVERDUE");

        RevenueSummaryResponse summary = new RevenueSummaryResponse(
                paidThisMonth != null ? paidThisMonth : BigDecimal.ZERO,
                expectedThisMonth != null ? expectedThisMonth : BigDecimal.ZERO,
                overdueAmount != null ? overdueAmount : BigDecimal.ZERO,
                activeSubscriptions,
                paidInvoicesCount,
                overdueCount,
                "USD");

        return ResponseEntity.ok(summary);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Returns true when the user holds the ADMIN or SUPER_ADMIN role. */
    private boolean hasAdminRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }
}
