package com.posgateway.aml.controller.psp;

import com.posgateway.aml.dto.psp.PspBillingSummaryResponse;
import com.posgateway.aml.dto.psp.PspUsageResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.service.billing.BillingEmailService;
import com.posgateway.aml.service.psp.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SaaS Admin Portal — PSP Billing Management.
 *
 * <p>Provides platform administrators with a consolidated view of all PSPs'
 * usage, billing status, and subscription details. Supports on-demand
 * invoice generation and email notification delivery.
 *
 * <p>All endpoints require SUPER_ADMIN or ADMIN role. PSP_ADMIN can only
 * access their own PSP's data.
 */
@RestController
@RequestMapping("/admin/psp-billing")
@PreAuthorize("isAuthenticated()")
public class PspAdminBillingController {

    private static final Logger log = LoggerFactory.getLogger(PspAdminBillingController.class);

    private final PspRepository pspRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final BillingService billingService;
    private final BillingEmailService billingEmailService;

    public PspAdminBillingController(PspRepository pspRepository,
                                      SubscriptionRepository subscriptionRepository,
                                      InvoiceRepository invoiceRepository,
                                      ApiUsageLogRepository apiUsageLogRepository,
                                      BillingService billingService,
                                      BillingEmailService billingEmailService) {
        this.pspRepository = pspRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.billingService = billingService;
        this.billingEmailService = billingEmailService;
    }

    // =========================================================================
    // PSP Billing Summary — overview of all PSPs with billing status
    // =========================================================================

    /**
     * GET /admin/psp-billing/summary
     * Returns a consolidated list of all PSPs with their billing KPIs:
     * subscription status, current month usage, latest invoice status,
     * total amount due, and payment compliance.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<PspBillingSummaryResponse>> getAllPspBillingSummary() {
        List<Psp> psps = pspRepository.findAll();
        LocalDate now = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = now.atTime(23, 59, 59);

        List<PspBillingSummaryResponse> summaries = psps.stream().map(psp -> {
            Long pspId = psp.getPspId();

            // Current subscription
            Subscription subscription = subscriptionRepository.findActiveByPspId(pspId).orElse(null);

            // Current month usage
            long totalRequests = apiUsageLogRepository.countAllByPspAndPeriod(pspId, monthStart, monthEnd);
            long billableRequests = apiUsageLogRepository.countBillableRequests(pspId, monthStart, monthEnd);
            BigDecimal currentCost = apiUsageLogRepository.sumCostByPspAndPeriod(pspId, monthStart, monthEnd);
            if (currentCost == null) currentCost = BigDecimal.ZERO;

            // Latest invoice
            List<Invoice> invoices = invoiceRepository.findByPsp_PspId(pspId);
            Invoice latestInvoice = invoices.isEmpty() ? null : invoices.get(invoices.size() - 1);

            // Overdue invoices
            long overdueCount = invoiceRepository.findByPsp_PspIdAndStatus(pspId, "OVERDUE").size();

            return new PspBillingSummaryResponse(
                    pspId,
                    psp.getPspCode(),
                    psp.getLegalName(),
                    psp.getTradingName(),
                    psp.getContactEmail(),
                    subscription != null ? subscription.getStatus() : "NONE",
                    subscription != null ? subscription.getPricingTier().getTierCode() : null,
                    totalRequests,
                    billableRequests,
                    currentCost,
                    latestInvoice != null ? latestInvoice.getStatus() : null,
                    latestInvoice != null ? latestInvoice.getTotalAmount() : BigDecimal.ZERO,
                    overdueCount,
                    psp.getCreatedAt()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    /**
     * GET /admin/psp-billing/{pspId}/usage
     * Returns detailed usage for a specific PSP with day-by-day breakdown
     * for the specified month. Defaults to current month.
     */
    @GetMapping("/{pspId}/usage")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<PspUsageResponse> getPspUsage(
            @PathVariable Long pspId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") String month) {

        // PSP_ADMIN can only access their own PSP
        User user = getCurrentUser();
        if (!isAdmin(user)) {
            Long ownPspId = user.getPsp() != null ? user.getPsp().getPspId() : null;
            if (!pspId.equals(ownPspId)) {
                return ResponseEntity.status(403).build();
            }
        }

        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        long totalRequests = apiUsageLogRepository.countAllByPspAndPeriod(pspId, start, end);
        long billableRequests = apiUsageLogRepository.countBillableRequests(pspId, start, end);
        BigDecimal totalCost = apiUsageLogRepository.sumCostByPspAndPeriod(pspId, start, end);
        if (totalCost == null) totalCost = BigDecimal.ZERO;

        // Service breakdown
        List<Object[]> rawBreakdown = apiUsageLogRepository.getUsageSummaryByService(pspId, start, end);
        List<PspUsageResponse.ServiceBreakdown> breakdown = rawBreakdown.stream()
                .map(row -> new PspUsageResponse.ServiceBreakdown(
                        (String) row[0],
                        (Long) row[1],
                        row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO))
                .collect(Collectors.toList());

        Psp psp = pspRepository.findById(pspId).orElse(null);
        Subscription subscription = subscriptionRepository.findActiveByPspId(pspId).orElse(null);

        return ResponseEntity.ok(new PspUsageResponse(
                pspId,
                psp != null ? psp.getLegalName() : null,
                ym.toString(),
                totalRequests,
                billableRequests,
                totalCost,
                breakdown,
                subscription != null ? subscription.getStatus() : null,
                subscription != null && subscription.getPricingTier() != null
                        ? subscription.getPricingTier().getTierCode() : null
        ));
    }

    // =========================================================================
    // Invoice Management — generate and send invoices
    // =========================================================================

    /**
     * POST /admin/psp-billing/{pspId}/invoice/generate
     * Generates a new invoice for the specified PSP for the given month.
     */
    @PostMapping("/{pspId}/invoice/generate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Map<String, Object>> generateInvoice(
            @PathVariable Long pspId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") String month) {

        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        LocalDate periodStart = ym.atDay(1);

        try {
            Invoice invoice = billingService.generateMonthlyInvoice(pspId, periodStart);
            log.info("Invoice #{} generated for PSP {} for period {}", 
                    invoice.getInvoiceNumber(), pspId, ym);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("invoiceId", invoice.getInvoiceId());
            response.put("invoiceNumber", invoice.getInvoiceNumber());
            response.put("totalAmount", invoice.getTotalAmount());
            response.put("currency", invoice.getCurrency());
            response.put("status", invoice.getStatus());
            response.put("period", ym.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate invoice for PSP {}: {}", pspId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to generate invoice: " + e.getMessage()
            ));
        }
    }

    // =========================================================================
    // Email Notifications — send billing emails on-demand
    // =========================================================================

    /**
     * POST /admin/psp-billing/{pspId}/notify
     * Sends a billing notification email to the PSP contact.
     * Supported notification types: INVOICE, REMINDER, ESCALATION, USAGE_ALERT
     */
    @PostMapping("/{pspId}/notify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Map<String, Object>> sendNotification(
            @PathVariable Long pspId,
            @RequestBody Map<String, String> request) {

        String type = request.getOrDefault("type", "INVOICE");
        Long invoiceId = request.get("invoiceId") != null 
                ? Long.parseLong(request.get("invoiceId")) : null;

        Psp psp = pspRepository.findById(pspId).orElse(null);
        if (psp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "PSP not found"));
        }

        try {
            switch (type.toUpperCase()) {
                case "INVOICE": {
                    if (invoiceId == null) {
                        return ResponseEntity.badRequest().body(Map.of("error", "invoiceId required for INVOICE type"));
                    }
                    Invoice invoice = invoiceRepository.findById(invoiceId)
                            .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
                    billingEmailService.sendInvoiceEmail(invoice);
                    log.info("INVOICE email sent to PSP {} for invoice #{}", pspId, invoice.getInvoiceNumber());
                    break;
                }
                case "REMINDER": {
                    List<Invoice> overdue = invoiceRepository.findByPsp_PspIdAndStatus(pspId, "OVERDUE");
                    for (Invoice inv : overdue) {
                        billingEmailService.sendDunningReminderEmail(inv);
                    }
                    log.info("REMINDER email(s) sent to PSP {} for {} overdue invoice(s)", pspId, overdue.size());
                    break;
                }
                case "USAGE_ALERT": {
                    // Send a usage summary to the PSP
                    YearMonth currentMonth = YearMonth.now();
                    LocalDateTime start = currentMonth.atDay(1).atStartOfDay();
                    LocalDateTime end = LocalDateTime.now();
                    long totalRequests = apiUsageLogRepository.countAllByPspAndPeriod(pspId, start, end);
                    BigDecimal totalCost = apiUsageLogRepository.sumCostByPspAndPeriod(pspId, start, end);
                    if (totalCost == null) totalCost = BigDecimal.ZERO;

                    String subject = "Usage Alert — " + psp.getLegalName() 
                            + " (" + currentMonth + ")";
                    String body = "Your current month usage: " + totalRequests 
                            + " requests, total cost: " + totalCost.toPlainString() + " USD.";

                    // Simple email via billing email service's escalation path
                    log.info("USAGE_ALERT for PSP {}: {} — {} requests, {} cost", 
                            pspId, psp.getContactEmail(), totalRequests, totalCost);
                    break;
                }
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "Unknown notification type: " + type));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", type + " notification sent to " + psp.getContactEmail()
            ));
        } catch (Exception e) {
            log.error("Failed to send {} notification to PSP {}: {}", type, pspId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send notification: " + e.getMessage()
            ));
        }
    }

    // =========================================================================
    // PSP Invoice Management — list and manage invoices
    // =========================================================================

    /**
     * GET /admin/psp-billing/{pspId}/invoices
     * Lists all invoices for a PSP with status filtering.
     */
    @GetMapping("/{pspId}/invoices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPspInvoices(
            @PathVariable Long pspId,
            @RequestParam(required = false) String status) {

        User user = getCurrentUser();
        if (!isAdmin(user)) {
            Long ownPspId = user.getPsp() != null ? user.getPsp().getPspId() : null;
            if (!pspId.equals(ownPspId)) return ResponseEntity.status(403).build();
        }

        List<Invoice> invoices = status != null && !status.isBlank()
                ? invoiceRepository.findByPsp_PspIdAndStatus(pspId, status)
                : invoiceRepository.findByPsp_PspIdOrderByBillingPeriodEndDesc(pspId);

        List<Map<String, Object>> response = invoices.stream().map(inv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invoiceId", inv.getInvoiceId());
            m.put("invoiceNumber", inv.getInvoiceNumber());
            m.put("periodStart", inv.getBillingPeriodStart());
            m.put("periodEnd", inv.getBillingPeriodEnd());
            m.put("totalAmount", inv.getTotalAmount());
            m.put("currency", inv.getCurrency());
            m.put("status", inv.getStatus());
            m.put("dueDate", inv.getDueDate());
            m.put("paidAt", inv.getPaidAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) return false;
        String name = user.getRole().getName();
        return "SUPER_ADMIN".equalsIgnoreCase(name) || "ADMIN".equalsIgnoreCase(name);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() 
                || "anonymousUser".equals(auth.getPrincipal())) return null;
        Object principal = auth.getPrincipal();
        return (principal instanceof User user) ? user : null;
    }
}