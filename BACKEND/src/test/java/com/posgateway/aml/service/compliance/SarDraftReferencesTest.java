package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.reporting.SarTemplateRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Plan conflict 7: SAR drafts persist transaction references only, no transaction data. */
class SarDraftReferencesTest {

    @Test
    void draftFromCaseStoresReferencesOnlyAndTypesFromDecisionRecords() {
        ComplianceCase c = new ComplianceCase();
        c.setId(5L);
        c.setPspId(3L);
        c.setCaseReference("CASE-1");
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        CaseTransactionRepository links = mock(CaseTransactionRepository.class);
        when(links.findByComplianceCase(c)).thenReturn(List.of(
                new CaseTransaction(c, TransactionReference.ofDecisionRecord(e1, "AUTH-1", "s"), "PRIMARY", null),
                new CaseTransaction(c, TransactionReference.ofDecisionRecord(e2, "AUTH-2", "s"), "RELATED", null),
                new CaseTransaction(c, TransactionReference.ofDecisionRecord(e1, "AUTH-1", "s"), "RELATED", null)));
        Assessment r1 = new Assessment();
        r1.setEdgeAssessmentId(e1);
        r1.setTypologyCodes(List.of("STRUCTURING"));
        r1.setEvaluatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        Assessment r2 = new Assessment();
        r2.setEdgeAssessmentId(e2);
        r2.setTypologyCodes(List.of("STRUCTURING", "RAPID_MOVEMENT"));
        TransactionReferenceResolver resolver = mock(TransactionReferenceResolver.class);
        when(resolver.decisionRecords(eq(3L), any())).thenReturn(Map.of(e1, r1, e2, r2));

        SarContentGenerationService service = new SarContentGenerationService(links,
                mock(SuspiciousActivityReportRepository.class), mock(SarTemplateRepository.class),
                mock(MerchantRepository.class), resolver);
        SuspiciousActivityReport sar = service.populateSarFromCase(c, new SuspiciousActivityReport());

        List<TransactionReference> refs = sar.getTransactionReferences();
        assertEquals(2, refs.size(), "duplicate links collapse to one reference");
        assertEquals(e1, refs.get(0).edgeAssessmentId());
        assertEquals("AUTH-2", refs.get(1).txnRef());
        assertNull(sar.getTotalSuspiciousAmount(), "no amount is derived in the cloud");
        assertEquals("STRUCTURING", sar.getSuspiciousActivityType(), "typology codes of the decision records");
        assertTrue(sar.getNarrative().contains("Transaction AUTH-1"), sar.getNarrative());
        assertFalse(sar.getNarrative().contains("Amount"), sar.getNarrative());
        sar.getTransactionRefs().forEach(row -> assertEquals(sar, row.getSar()));
    }
}
