package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.reporting.SarTemplate;
import com.posgateway.aml.exception.SarTemplateNotConfiguredException;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.reporting.SarTemplateRepository;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SAR Content Generation Service: generates SAR draft narratives and populates drafts from cases.
 *
 * <p>Data residency (plan conflict 7, contract 2.3): a draft persists transaction <b>references</b>
 * only (decision record id / txnRef / site). Amounts, dates of the transactions themselves and other
 * transaction data are not in the cloud: the draft's transaction table is rendered from the Edge when
 * viewed, and stored only inside the filed document (work package R9). What this service may use
 * besides workflow data are the decision records' class-D fields (evaluation time to the second,
 * typology codes).
 */
@Service
public class SarContentGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SarContentGenerationService.class);

    /** Mustache-style {{key}} matcher used to render template bodies. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    /** Default jurisdiction fallback when the merchant has no country. Kenya context for this platform. */
    private static final String DEFAULT_COUNTRY = "KE";

    private final CaseTransactionRepository caseTransactionRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final SarTemplateRepository sarTemplateRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionReferenceResolver transactionReferences;

    @Autowired
    public SarContentGenerationService(CaseTransactionRepository caseTransactionRepository,
                                      SuspiciousActivityReportRepository sarRepository,
                                      SarTemplateRepository sarTemplateRepository,
                                      MerchantRepository merchantRepository,
                                      TransactionReferenceResolver transactionReferences) {
        this.caseTransactionRepository = caseTransactionRepository;
        this.sarRepository = sarRepository;
        this.sarTemplateRepository = sarTemplateRepository;
        this.merchantRepository = merchantRepository;
        this.transactionReferences = transactionReferences;
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

        // Transaction references only: the transaction table is rendered from the Edge (R9)
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        if (!transactions.isEmpty()) {
            narrative.append("TRANSACTION SUMMARY:\n");
            narrative.append("Total Number of Transactions: ").append(transactions.size()).append("\n\n");
            narrative.append("TRANSACTION REFERENCES:\n");
            transactions.forEach(ct -> narrative.append("- Transaction ")
                    .append(ct.getTransactionReference().label()).append("\n"));
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

        // Link case
        sar.setComplianceCase(complianceCase);

        // Link transactions by ID reference only; the total amount is the analyst's / filing's (R9)
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        sar.setTransactionReferences(transactions.stream()
                .map(CaseTransaction::getTransactionReference)
                .collect(Collectors.toList()));

        // Determine suspicious activity type
        sar.setSuspiciousActivityType(determineActivityType(complianceCase, transactions));

        return sar;
    }

    /**
     * Determines the suspicious activity type from the decision records' typology codes (class D):
     * the most frequent code wins; with none, a burst of more than ten transactions is
     * RAPID_MOVEMENT, otherwise SUSPICIOUS_PATTERN. Amount-based typing (structuring, high value) is
     * done on the Edge and arrives as a typology code.
     */
    private String determineActivityType(ComplianceCase complianceCase, List<CaseTransaction> transactions) {
        if (transactions.isEmpty()) {
            return "OTHER";
        }
        Map<String, Long> typologies = decisionRecords(complianceCase.getPspId(),
                transactions.stream().map(CaseTransaction::getTransactionReference).toList())
                .values().stream()
                .filter(record -> record.getTypologyCodes() != null)
                .flatMap(record -> record.getTypologyCodes().stream())
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.groupingBy(code -> code, Collectors.counting()));
        Optional<String> dominant = typologies.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey);
        if (dominant.isPresent()) {
            return dominant.get();
        }
        return transactions.size() > 10 ? "RAPID_MOVEMENT" : "SUSPICIOUS_PATTERN";
    }

    /** The PSP's decision records named by {@code references} (PSP-scoped). */
    private Map<java.util.UUID, Assessment> decisionRecords(Long pspId, Collection<TransactionReference> references) {
        return transactionReferences.decisionRecords(pspId, references);
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
        String customerName = "";
        if (sar != null && sar.getFiledBy() != null) {
            String fn = sar.getFiledBy().getFirstName() != null ? sar.getFiledBy().getFirstName() : "";
            String ln = sar.getFiledBy().getLastName() != null ? sar.getFiledBy().getLastName() : "";
            customerName = (fn + " " + ln).trim();
        }
        v.put("customer_name",        customerName);
        v.put("customer_country",     resolveCustomerCountry(sar, c));
        v.put("filing_institution",   c != null && c.getPspId() != null ? "PSP-" + c.getPspId() : "");
        String filedByName = "";
        if (sar != null && sar.getFiledBy() != null) {
            String fn = sar.getFiledBy().getFirstName() != null ? sar.getFiledBy().getFirstName() : "";
            String ln = sar.getFiledBy().getLastName() != null ? sar.getFiledBy().getLastName() : "";
            filedByName = (fn + " " + ln).trim();
        }
        v.put("filed_by_name",        filedByName);

        v.put("suspicious_activity_type",
                sar != null && sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "");
        v.put("total_suspicious_amount",
                sar != null && sar.getTotalSuspiciousAmount() != null ? sar.getTotalSuspiciousAmount().toPlainString() : "0");
        // No "currency" value: the transactions' currency is transaction data, rendered at filing from
        // the Edge (R9); an unfilled placeholder renders as [NOT PROVIDED].
        List<TransactionReference> txns = sar != null ? sar.getTransactionReferences() : List.of();
        v.put("transaction_count", String.valueOf(txns.size()));
        v.put("transaction_references", txns.stream().map(TransactionReference::label)
                .collect(Collectors.joining(", ")));

        // Activity period from the decision records' evaluation times (class D, second precision)
        String activityStart = "";
        String activityEnd   = "";
        Long pspId = sar != null ? sar.getPspId() : null;
        LocalDateTime min = null, max = null;
        for (Assessment record : decisionRecords(pspId, txns).values()) {
            if (record.getEvaluatedAt() != null) {
                LocalDateTime at = LocalDateTime.ofInstant(record.getEvaluatedAt(), ZoneId.systemDefault());
                if (min == null || at.isBefore(min)) min = at;
                if (max == null || at.isAfter(max))  max = at;
            }
        }
        if (min != null) activityStart = min.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (max != null) activityEnd   = max.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        v.put("activity_start_date", activityStart);
        v.put("transaction_date",    activityEnd);

        StringBuilder findings = new StringBuilder();
        if (c != null && c.getNotes() != null) {
            c.getNotes().stream()
                    .filter(n -> !n.isInternal())
                    .forEach(n -> findings.append("- ").append(n.getContent()).append("\n"));
        }
        v.put("investigation_findings", findings.toString());

        // SAR-level fields exposed so templates can reference them directly
        v.put("sar_reference",        sar != null && sar.getSarReference() != null ? sar.getSarReference() : "");
        v.put("jurisdiction",         sar != null && sar.getJurisdiction() != null ? sar.getJurisdiction() : "");
        v.put("sar_type",             sar != null && sar.getSarType() != null ? sar.getSarType().name() : "INITIAL");
        v.put("sar_status",           sar != null && sar.getStatus() != null ? sar.getStatus().name() : "DRAFT");
        v.put("amendment_reason",     sar != null && sar.getAmendmentReason() != null ? sar.getAmendmentReason() : "");
        v.put("filing_deadline",      sar != null && sar.getFilingDeadline() != null
                ? sar.getFilingDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        v.put("filing_reference",     sar != null && sar.getFilingReferenceNumber() != null ? sar.getFilingReferenceNumber() : "");
        v.put("psp_id",               sar != null && sar.getPspId() != null ? sar.getPspId().toString() : "");

        // Related-SAR enrichment: how many other SARs already exist for the same
        // case. Useful for narrative context ("this is the 3rd SAR filed against
        // the case"), and for FATF-style amendment tracking.
        long relatedSarCount = 0L;
        if (c != null && c.getId() != null) {
            try {
                long total = sarRepository.countByComplianceCase_Id(c.getId());
                // Subtract self if the SAR is already persisted, so the value
                // represents *other* SARs on the case.
                relatedSarCount = (sar != null && sar.getId() != null && total > 0) ? total - 1 : total;
                if (relatedSarCount < 0) relatedSarCount = 0;
            } catch (Exception ex) {
                logger.debug("Failed to load related SAR count for case {}: {}", c.getId(), ex.getMessage());
            }
        }
        v.put("related_sar_count",    String.valueOf(relatedSarCount));

        return v;
    }

    /**
     * Resolve the customer / merchant country for the SAR: the merchant's persisted {@code country}
     * when the case has a merchantId (cloud master data), else {@link #DEFAULT_COUNTRY}. The
     * transaction's country is transaction data and is not read in the cloud.
     */
    private String resolveCustomerCountry(SuspiciousActivityReport sar, ComplianceCase c) {
        if (c != null && c.getMerchantId() != null) {
            Optional<String> merchantCountry = merchantRepository.findById(c.getMerchantId())
                    .map(Merchant::getCountry)
                    .filter(s -> s != null && !s.isBlank());
            if (merchantCountry.isPresent()) {
                return merchantCountry.get();
            }
        }
        return DEFAULT_COUNTRY;
    }

    /** Substitutes {{key}} placeholders; unknown keys render as [NOT PROVIDED]. */
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

        // Post-substitution: warn on any remaining unfilled tokens and replace
        // them with a safe sentinel so the rendered output contains no raw {{…}}.
        String result = out.toString();
        if (result.contains("{{")) {
            Matcher remaining = PLACEHOLDER.matcher(result);
            if (remaining.find()) {
                logger.warn("SAR template has unfilled placeholder(s); first unfilled: {{{{{}}}}}", remaining.group(1));
            }
            result = PLACEHOLDER.matcher(result)
                    .replaceAll(mr -> Matcher.quoteReplacement("[NOT PROVIDED]"));
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Rendered SAR template ({} chars)", result.length());
        }
        return result;
    }
}
