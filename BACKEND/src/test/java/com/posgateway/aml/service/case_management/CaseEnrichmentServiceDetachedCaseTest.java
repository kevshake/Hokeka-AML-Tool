package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.CaseNote;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.CaseEntityRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4: @Async enrichment receives a detached case; notes must be written to a freshly
 * re-loaded managed instance, never to the detached one.
 */
class CaseEnrichmentServiceDetachedCaseTest {

    private ComplianceCaseRepository caseRepository;
    private CaseEnrichmentService service;

    @BeforeEach
    void setUp() {
        caseRepository = mock(ComplianceCaseRepository.class);
        service = new CaseEnrichmentService(mock(CaseTransactionRepository.class),
                mock(CaseEntityRepository.class), caseRepository, mock(MerchantRepository.class),
                mock(AmlScreeningOrchestrator.class), null);
    }

    @Test
    void riskNoteIsAddedToReloadedManagedCase() {
        ComplianceCase detached = new ComplianceCase();
        detached.setId(42L);
        ComplianceCase managed = new ComplianceCase();
        managed.setId(42L);
        managed.setNotes(new ArrayList<>());
        when(caseRepository.findById(42L)).thenReturn(Optional.of(managed));

        service.enrichWithRiskDetails(detached, Map.of("score", 91));

        assertNull(detached.getNotes(), "detached instance must not be mutated");
        List<CaseNote> notes = managed.getNotes();
        assertEquals(1, notes.size());
        assertSame(managed, notes.get(0).getComplianceCase());
        verify(caseRepository).save(managed);
    }

    @Test
    void caseDeletedBeforeAsyncRunIsSkippedNotWrittenDetached() {
        ComplianceCase detached = new ComplianceCase();
        detached.setId(7L);
        when(caseRepository.findById(7L)).thenReturn(Optional.empty());

        service.enrichWithRiskDetails(detached, Map.of("score", 10));

        assertNull(detached.getNotes());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void reloadedCaseWithNullCollectionGetsInitialised() {
        ComplianceCase managed = new ComplianceCase();
        managed.setId(3L);
        when(caseRepository.findById(3L)).thenReturn(Optional.of(managed));

        ComplianceCase detached = new ComplianceCase();
        detached.setId(3L);
        service.enrichWithRiskDetails(detached, Map.of("k", "v"));

        assertEquals(1, managed.getNotes().size());
    }
}
