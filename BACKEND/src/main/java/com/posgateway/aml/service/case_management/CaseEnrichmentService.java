package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.CaseEntity;
import com.posgateway.aml.entity.compliance.CaseNote;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.CaseEntityRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service responsible for enriching cases with related context.
 * - Links Triggering Transactions
 * - Links Customer/Merchant Entities
 * - Attaches Risk Score Details
 */
@Service
public class CaseEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(CaseEnrichmentService.class);

    private final CaseTransactionRepository caseTransactionRepository;
    private final CaseEntityRepository caseEntityRepository;
    private final ComplianceCaseRepository caseRepository;
    private final MerchantRepository merchantRepository;
    private final com.posgateway.aml.service.aml.AmlScreeningOrchestrator screeningOrchestrator;
    private final com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService;

    @Autowired
    public CaseEnrichmentService(CaseTransactionRepository caseTransactionRepository,
            CaseEntityRepository caseEntityRepository,
            ComplianceCaseRepository caseRepository,
            MerchantRepository merchantRepository,
            com.posgateway.aml.service.aml.AmlScreeningOrchestrator screeningOrchestrator,
            @Autowired(required = false) com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService) {
        this.caseTransactionRepository = caseTransactionRepository;
        this.caseEntityRepository = caseEntityRepository;
        this.caseRepository = caseRepository;
        this.merchantRepository = merchantRepository;
        this.screeningOrchestrator = screeningOrchestrator;
        this.neo4jGdsService = neo4jGdsService;
    }

    /**
     * Link triggering or related transaction to the case
     */
    @Async
    @Transactional
    public void enrichWithTransaction(ComplianceCase cCase, TransactionEntity tx, String relationshipType) {
        if (tx == null)
            return;

        // Check if already linked
        boolean exists = caseTransactionRepository.findByComplianceCase_Id(cCase.getId()).stream()
                .anyMatch(ct -> ct.getTransaction().getTxnId().equals(tx.getTxnId()));

        if (!exists) {
            CaseTransaction link = new CaseTransaction();
            link.setComplianceCase(cCase);
            link.setTransaction(tx);
            link.setRelationshipType(relationshipType);
            link.setAddedAt(LocalDateTime.now());
            // AddedBy is null (System)

            caseTransactionRepository.save(link);
            logger.debug("Linked transaction {} to case {}", tx.getTxnId(), cCase.getCaseReference());
        }
    }

    /**
     * Link Merchant/Customer Entity to the case
     */
    @Async
    @Transactional
    public void enrichWithMerchantProfile(ComplianceCase cCase, Long merchantId) {
        if (merchantId == null)
            return;
        String ref = String.valueOf(merchantId);

        boolean exists = caseEntityRepository.findByComplianceCase_Id(cCase.getId()).stream()
                .anyMatch(ce -> CE_TYPE_MERCHANT.equals(ce.getEntityType()) && ref.equals(ce.getEntityReference()));

        if (!exists) {
            CaseEntity customEntity = new CaseEntity(cCase, CE_TYPE_MERCHANT, ref, "Merchant Profile (Auto-linked)",
                    null);
            caseEntityRepository.save(customEntity);

            // 2. Fetch merchant and perform real-time AML re-screening through the
            //    platform's own independent sanctions engine (persists result + audit).
            try {
                Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
                if (merchant == null) {
                    addSystemNote(cCase, "AML re-screen skipped — merchant " + ref + " not found");
                } else {
                    String merchantName = merchant.getLegalName() != null
                            ? merchant.getLegalName()
                            : merchant.getTradingName();
                    String riskTier = merchant.getRiskLevel() != null ? merchant.getRiskLevel() : "UNKNOWN";
                    String kycStatus = merchant.getKycStatus() != null ? merchant.getKycStatus() : "PENDING";

                    addSystemNote(cCase,
                            "Merchant enrichment: name=" + merchantName
                                    + ", riskTier=" + riskTier
                                    + ", kycStatus=" + kycStatus);

                    com.posgateway.aml.model.ScreeningResult screen =
                            screeningOrchestrator.screenMerchant(merchant);
                    addSystemNote(cCase, "Triggered AML re-screen for merchant "
                            + ref + " (" + merchantName + "): status=" + screen.getStatus()
                            + ", matches=" + screen.getMatchCount());
                }
            } catch (Exception e) {
                logger.error("AML re-screen trigger failed for merchant {}", merchantId, e);
            }

            // 3. Update Graph Context (Integration: Neo4j)
            if (neo4jGdsService != null) {
                try {
                    neo4jGdsService.updateMerchantRiskStatus(ref, null, true);
                    addSystemNote(cCase, "Graph Context Updated: Merchant flagged as Under Investigation");
                } catch (Exception e) {
                    logger.error("Graph update failed", e);
                }
            }
        }
    }

    private void addSystemNote(ComplianceCase cCase, String text) {
        // These enrichment methods are @Async, so the passed case is detached from the
        // caller's persistence context — touching its lazy `notes` collection directly would
        // throw LazyInitializationException. Re-load a managed instance inside this thread's
        // transaction before mutating the collection.
        ComplianceCase managed = (cCase != null && cCase.getId() != null)
                ? caseRepository.findById(cCase.getId()).orElse(cCase) : cCase;
        if (managed == null) {
            return;
        }
        CaseNote note = new CaseNote();
        note.setComplianceCase(managed);
        note.setContent(text);
        note.setCreatedAt(LocalDateTime.now());
        note.setInternal(true);
        managed.getNotes().add(note);
        caseRepository.save(managed);
    }

    /**
     * Attach Risk Details (Scores, Reasons) as a structured Note
     */
    @Async
    @Transactional
    public void enrichWithRiskDetails(ComplianceCase cCase, Map<String, Object> riskDetails) {
        if (riskDetails == null || riskDetails.isEmpty())
            return;

        StringBuilder sb = new StringBuilder("Auto-Generated Risk Assessment:\n");
        riskDetails.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));

        // Persist via the shared helper, which re-loads a managed case (this method is @Async,
        // so the passed entity is detached and its lazy notes collection cannot be touched here).
        addSystemNote(cCase, sb.toString());
    }

    private static final String CE_TYPE_MERCHANT = "MERCHANT";
}
