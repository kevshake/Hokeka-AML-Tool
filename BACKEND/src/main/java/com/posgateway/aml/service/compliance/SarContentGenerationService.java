package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SAR Content Generation Service
 * Automatically generates SAR narratives and populates data from cases
 */
@Service
public class SarContentGenerationService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(SarContentGenerationService.class);

    private final CaseTransactionRepository caseTransactionRepository;
    @SuppressWarnings("unused")
    private final SuspiciousActivityReportRepository sarRepository;

    @Autowired
    public SarContentGenerationService(CaseTransactionRepository caseTransactionRepository,
                                      SuspiciousActivityReportRepository sarRepository) {
        this.caseTransactionRepository = caseTransactionRepository;
        this.sarRepository = sarRepository;
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
     * Get SAR template by jurisdiction
     */
    public String getSarTemplate(String jurisdiction) {
        // TODO: Load from database or configuration
        return switch (jurisdiction.toUpperCase()) {
            case "US" -> getUsSarTemplate();
            case "UK" -> getUkSarTemplate();
            case "EU" -> getEuSarTemplate();
            default -> getDefaultSarTemplate();
        };
    }

    private String getUsSarTemplate() {
        return """
                SUSPICIOUS ACTIVITY REPORT (SAR)
                Financial Crimes Enforcement Network (FinCEN)
                
                Part I: Subject Information
                Part II: Suspicious Activity Information
                Part III: Filing Institution Information
                Part IV: Narrative
                """;
    }

    private String getUkSarTemplate() {
        return """
                SUSPICIOUS ACTIVITY REPORT (SAR)
                National Crime Agency (NCA)
                
                Subject Details
                Activity Details
                Supporting Information
                """;
    }

    private String getEuSarTemplate() {
        return """
                SUSPICIOUS TRANSACTION REPORT (STR)
                Financial Intelligence Unit (FIU)
                
                Subject Information
                Transaction Details
                Suspicious Activity Description
                """;
    }

    private String getDefaultSarTemplate() {
        return """
                SUSPICIOUS ACTIVITY REPORT
                
                Subject Information
                Activity Description
                Supporting Evidence
                """;
    }
}

