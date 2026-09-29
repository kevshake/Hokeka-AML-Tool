package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.graph.Neo4jGdsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseNetworkServiceNeo4jTest {

    @Test
    void attachesTrailCycleAndMuleMetricsWhenNeo4jPresent() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        CaseTransactionRepository caseTransactions = mock(CaseTransactionRepository.class);
        SuspiciousActivityReportRepository sars = mock(SuspiciousActivityReportRepository.class);
        Neo4jGdsService gds = mock(Neo4jGdsService.class);
        ComplianceCase root = sampleCase(11L, 88L);
        when(cases.findById(11L)).thenReturn(Optional.of(root));
        when(caseTransactions.findByComplianceCase(root)).thenReturn(List.of());
        when(sars.findByComplianceCase_Id(11L)).thenReturn(List.of());
        when(gds.findMoneyTrail("88", "88")).thenReturn(Map.of("pathLength", 2L));
        when(gds.detectCycles("88")).thenReturn(Map.of("cycle", List.of("88")));
        when(gds.detectMuleProximity("88")).thenReturn(Map.of("hops", 2));

        CaseNetworkService service = new CaseNetworkService(cases, caseTransactions,
                mock(TransactionRepository.class), sars, mock(UserRepository.class), gds);

        CaseNetworkService.NetworkGraphDTO graph = service.buildNetworkGraph(11L, 1);

        assertNotNull(graph.getNeo4jMetrics());
        assertEquals(2L, graph.getNeo4jMetrics().get("pathLength"));
        assertEquals(2, graph.getNeo4jMetrics().get("hops"));
        assertTrue(graph.getNodes().stream().anyMatch(n -> "CASE".equals(n.getType())));
    }

    @Test
    void nullNeo4jLeavesGraphUnchangedAndDoesNotFail() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        CaseTransactionRepository caseTransactions = mock(CaseTransactionRepository.class);
        SuspiciousActivityReportRepository sars = mock(SuspiciousActivityReportRepository.class);
        ComplianceCase root = sampleCase(12L, 90L);
        when(cases.findById(12L)).thenReturn(Optional.of(root));
        when(caseTransactions.findByComplianceCase(root)).thenReturn(List.of());
        when(sars.findByComplianceCase_Id(12L)).thenReturn(List.of());

        CaseNetworkService service = new CaseNetworkService(cases, caseTransactions,
                mock(TransactionRepository.class), sars, mock(UserRepository.class), null);

        CaseNetworkService.NetworkGraphDTO graph = service.buildNetworkGraph(12L, 1);

        assertNull(graph.getNeo4jMetrics());
        assertTrue(graph.getNodes().stream().anyMatch(n -> "CASE_12".equals(n.getId())));
    }

    @Test
    void neo4jFailureIsFailSoftAndDoesNotFailTheRequest() {
        ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
        CaseTransactionRepository caseTransactions = mock(CaseTransactionRepository.class);
        SuspiciousActivityReportRepository sars = mock(SuspiciousActivityReportRepository.class);
        Neo4jGdsService gds = mock(Neo4jGdsService.class);
        ComplianceCase root = sampleCase(13L, 91L);
        when(cases.findById(13L)).thenReturn(Optional.of(root));
        when(caseTransactions.findByComplianceCase(root)).thenReturn(List.of());
        when(sars.findByComplianceCase_Id(13L)).thenReturn(List.of());
        when(gds.detectCycles(anyString())).thenThrow(new RuntimeException("neo4j down"));

        CaseNetworkService service = new CaseNetworkService(cases, caseTransactions,
                mock(TransactionRepository.class), sars, mock(UserRepository.class), gds);

        CaseNetworkService.NetworkGraphDTO graph = service.buildNetworkGraph(13L, 1);

        assertNotNull(graph);
        assertTrue(graph.getNodes().stream().anyMatch(n -> "CASE".equals(n.getType())));
    }

    private static ComplianceCase sampleCase(Long id, Long merchantId) {
        ComplianceCase c = new ComplianceCase();
        c.setId(id);
        c.setCaseReference("CASE-" + id);
        c.setMerchantId(merchantId);
        return c;
    }
}
