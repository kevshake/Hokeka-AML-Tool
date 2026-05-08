package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.reporting.SarTemplate;
import com.posgateway.aml.exception.SarTemplateNotConfiguredException;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.reporting.SarTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SAR Content Generation Service
 * Automatically generates SAR narratives and populates data from cases
 */
@Service
public class SarContentGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SarContentGenerationService.class);

    /** Mustache-style {{key}} matcher used to render template bodies. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final CaseTransactionRepository caseTransactionRepository;
    @SuppressWarnings("unused")
    private final SuspiciousActivityReportRepository sarRepository;
    private final SarTemplateRepository sarTemplateRepository;

    @Autowired
    public SarContentGenerationService(CaseTransactionRepository caseTransactionRepository,
                                      SuspiciousActivityReportRepository sarRepository,
                                      SarTemplateRepository sarTemplateRepository) {
        this.caseTransactionRepository = caseTransactionRepository;
        this.sarRepository = sarRepository;
        this.sarTemplateRepository = sarTemplateRepository;
    }

    /**
     * Generate SAR narrative from case
     */
    public String generateNarrative(ComplianceCase complianceCase) {
        StringBuilder narrative = new StringBuilder();

        // Header
        narrative.append("SUSPICIOUS ACTIVITY REPORT\n");
        narrative.append("Case Reference: ").append(complianceCase.getCaseReference()).append("\n\n");

        // Case description
        if (complianceCase.getDescription() != null) {
            narrative.append("DESCRIPTION OF SUSPICIOUS ACTIVITY:\n");
            narrative.append(complianceCase.getDescription()).append("\n\n");
        }

        // Transaction summary
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        if (!transactions.isEmpty()) {
            narrative.append("TRANSACTION SUMMARY:\n");
            BigDecimal totalAmount = BigDecimal.ZERO;
            int transactionCount = transactions.size();

            for (CaseTransaction ct : transactions) {
                TransactionEntity tx = ct.getTransaction();
                if (tx.getAmountCents() != null) {
                    totalAmount = totalAmount.add(
                            BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100"))
                    );
                }
            }

            narrative.append("Total Number of Transactions: ").append(transactionCount).append("\n");
            narrative.append("Total Amount: ").append(totalAmount).append("\n\n");

            // Transaction details
            narrative.append("TRANSACTION DETAILS:\n");
            transactions.forEach(ct -> {
                TransactionEntity tx = ct.getTransaction();
                BigDecimal amount = tx.getAmountCents() != null ?
                        BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100")) :
                        BigDecimal.ZERO;
                narrative.append("- Transaction ID: ").append(tx.getTxnId())
                        .append(", Amount: ").append(amount)
                        .append(", Date: ").append(tx.getTxnTs() != null ?
                                tx.getTxnTs().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "N/A")
                        .append("\n");
            });
            narrative.append("\n");
        }

        // Investigation findings
        if (complianceCase.getNotes() != null && !complianceCase.getNotes().isEmpty()) {
            narrative.append("INVESTIGATION FINDINGS:\n");
            complianceCase.getNotes().forEach(note -> {
                if (!note.isInternal()) {
                    narrative.append("- ").append(note.getContent()).append("\n");
                }
            });
            narrative.append("\n");
        }

        // Resolution
        if (complianceCase.getResolution() != null) {
            narrative.append("RESOLUTION:\n");
            narrative.append(complianceCase.getResolution()).append("\n");
            if (complianceCase.getResolutionNotes() != null) {
                narrative.append(complianceCase.getResolutionNotes()).append("\n");
            }
        }

        return narrative.toString();
    }

    /**
     * Populate SAR from case
     */
    public SuspiciousActivityReport populateSarFromCase(ComplianceCase complianceCase,
                                                         SuspiciousActivityReport sar) {
        // Set narrative
        sar.setNarrative(generateNarrative(complianceCase));

        // Calculate total suspicious amount
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        BigDecimal totalAmount = transactions.stream()
                .map(ct -> {
                    TransactionEntity tx = ct.getTransaction();
                    return tx.getAmountCents() != null ?
                            BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100")) :
                            BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sar.setTotalSuspiciousAmount(totalAmount);

        // Link case
        sar.setComplianceCase(complianceCase);

        // Link transactions
        List<TransactionEntity> suspiciousTransactions = transactions.stream()
                .map(CaseTransaction::getTransaction)
                .collect(Collectors.toList());
        sar.setSuspiciousTransactions(suspiciousTransactions);

        // Determine suspicious activity type
        sar.setSuspiciousActivityType(determineActivityType(complianceCase, transactions));

        return sar;
    }

    /**
     * Determine suspicious activity type based on patterns
     */
    private String determineActivityType(ComplianceCase complianceCase, List<CaseTransaction> transactions) {
        if (transactions.isEmpty()) {
            return "OTHER";
        }

        // Check for structuring (multiple transactions just below threshold)
        long count = transactions.size();
        BigDecimal totalAmount = transactions.stream()
                .map(ct -> BigDecimal.valueOf(ct.getTransaction().getAmountCents()).divide(BigDecimal.valueOf(100)))
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (count > 5 && totalAmount.compareTo(new BigDecimal("10000")) > 0) {
            return "STRUCTURING";
        }

        // Check for rapid movement
        if (count > 10) {
            return "RAPID_MOVEMENT";
        }

        // Check for high value
        if (totalAmount.compareTo(new BigDecimal("50000")) > 0) {
            return "HIGH_VALUE_TRANSACTION";
        }

        return "SUSPICIOUS_PATTERN";
    }

    /**
     * Render the SAR template body for a regulator + jurisdiction (alpha-3)
     * with placeholders substituted from the SAR's actual data.
     *
     * @throws SarTemplateNotConfiguredException when no active row exists.
     */
    public String renderTemplate(String regulator, String jurisdiction, SuspiciousActivityReport sar) {
        if (regulator == null || jurisdiction == null) {
            throw new IllegalArgumentException("regulator and jurisdiction are required");
        }
        SarTemplate template = sarTemplateRepository
                .findActiveByRegulatorAndJurisdiction(regulator.toUpperCase(), jurisdiction.toUpperCase())
                .orElseThrow(() -> new SarTemplateNotConfiguredException(regulator, jurisdiction));

        Map<String, String> values = buildPlaceholderValues(sar);
        return substitute(template.getBodyTemplate(), values);
    }

    /**
     * Backwards-compatible accessor used by existing callers. Returns the raw
     * {@code body_template} for the default version 1.0 (active=true) row.
     *
     * <p>Jurisdiction here is the alpha-3 jurisdiction code (USA / GBR / KEN).
     */
    public String getSarTemplate(String jurisdiction) {
        if (jurisdiction == null || jurisdiction.isBlank()) {
            throw new IllegalArgumentException("jurisdiction is required");
        }
        String juris = jurisdiction.toUpperCase();
        String regulator = switch (juris) {
            case "USA", "US" -> "FINCEN";
            case "GBR", "GB", "UK" -> "FCA";
            case "KEN", "KE" -> "CBK";
            default -> "OTHER";
        };
        String alpha3 = switch (juris) {
            case "US" -> "USA";
            case "GB", "UK" -> "GBR";
            case "KE" -> "KEN";
            default -> juris;
        };
        SarTemplate template = sarTemplateRepository
                .findActiveByRegulatorAndJurisdiction(regulator, alpha3)
                .orElseThrow(() -> new SarTemplateNotConfiguredException(regulator, alpha3));
        return template.getBodyTemplate();
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private Map<String, String> buildPlaceholderValues(SuspiciousActivityReport sar) {
        Map<String, String> v = new HashMap<>();
        ComplianceCase c = sar != null ? sar.getComplianceCase() : null;

        v.put("case_reference",       c != null && c.getCaseReference() != null ? c.getCaseReference() : "");
        v.put("case_description",     c != null && c.getDescription() != null ? c.getDescription() : "");
        v.put("merchant_id",          c != null && c.getMerchantId() != null ? c.getMerchantId().toString() : "");
        v.put("customer_name",        sar != null && sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "");
        v.put("customer_country",     "");
        v.put("filing_institution",   c != null && c.getPspId() != null ? "PSP-" + c.getPspId() : "");
        v.put("filed_by_name",        sar != null && sar.getFiledBy() != null ? sar.getFiledBy().getFullName() : "");

        v.put("suspicious_activity_type",
                sar != null && sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "");
        v.put("total_suspicious_amount",
                sar != null && sar.getTotalSuspiciousAmount() != null ? sar.getTotalSuspiciousAmount().toPlainString() : "0");
        v.put("currency", "USD");

        List<TransactionEntity> txns = sar != null ? sar.getSuspiciousTransactions() : null;
        v.put("transaction_count", txns == null ? "0" : String.valueOf(txns.size()));

        String activityStart = "";
        String activityEnd   = "";
        if (txns != null && !txns.isEmpty()) {
            java.time.LocalDateTime min = null, max = null;
            for (TransactionEntity t : txns) {
                if (t.getTxnTs() != null) {
                    if (min == null || t.getTxnTs().isBefore(min)) min = t.getTxnTs();
                    if (max == null || t.getTxnTs().isAfter(max))  max = t.getTxnTs();
                }
            }
            if (min != null) activityStart = min.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (max != null) activityEnd   = max.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        v.put("activity_start_date", activityStart);
        v.put("transaction_date",    activityEnd);

        StringBuilder findings = new StringBuilder();
        if (c != null && c.getNotes() != null) {
            c.getNotes().stream()
                    .filter(n -> !n.isInternal())
                    .forEach(n -> findings.append("- ").append(n.getContent()).append("\n"));
        }
        v.put("investigation_findings", findings.toString());

        return v;
    }

    private static String substitute(String template, Map<String, String> values) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = values.getOrDefault(key, "");
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        if (logger.isTraceEnabled()) {
            logger.trace("Rendered SAR template ({} chars)", out.length());
        }
        return out.toString();
    }
}
