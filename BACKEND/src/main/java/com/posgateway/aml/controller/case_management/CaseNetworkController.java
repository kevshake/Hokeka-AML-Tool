package com.posgateway.aml.controller.case_management;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.service.case_management.CaseNetworkService;
import com.posgateway.aml.service.edgedata.CaseTransactionDetailsEnricher;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Case Network Controller
 * Provides endpoints for case network graph visualization.
 *
 * <p>R6: the case must belong to the caller's PSP (platform administrators excepted); transaction
 * nodes get their details pulled live from the PSP's Edge per view, and {@code related=CARD,DEVICE…}
 * adds the Edge's {@code txn.related} links. Nothing pulled is persisted.
 */
@RestController
@RequestMapping("/cases/{caseId}/network")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'CASE_MANAGER')")
public class CaseNetworkController {

    private final CaseNetworkService caseNetworkService;
    private final ComplianceCaseRepository caseRepository;
    private final PspIsolationService pspIsolationService;
    private final CaseTransactionDetailsEnricher edgeDetails;

    /** Wires the graph endpoint. */
    @Autowired
    public CaseNetworkController(CaseNetworkService caseNetworkService, ComplianceCaseRepository caseRepository,
                                 PspIsolationService pspIsolationService, CaseTransactionDetailsEnricher edgeDetails) {
        this.caseNetworkService = caseNetworkService;
        this.caseRepository = caseRepository;
        this.pspIsolationService = pspIsolationService;
        this.edgeDetails = edgeDetails;
    }

    /** The case graph with live-pulled transaction details (404 for an unknown case). */
    @GetMapping
    public ResponseEntity<CaseNetworkService.NetworkGraphDTO> getNetworkGraph(
            @PathVariable Long caseId,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "true") boolean transactionDetails,
            @RequestParam(required = false) List<String> related,
            @RequestParam(required = false) Integer relatedDepth,
            @RequestParam(required = false) Integer relatedWindowDays,
            @RequestParam(required = false) String supportReason,
            @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        ComplianceCase complianceCase = caseRepository.findById(caseId).orElse(null);
        if (complianceCase == null) {
            return ResponseEntity.notFound().build();
        }
        pspIsolationService.validateCaseAccess(complianceCase);
        CaseNetworkService.NetworkGraphDTO graph = caseNetworkService.buildNetworkGraph(caseId, depth);
        if (complianceCase.getPspId() != null) {
            edgeDetails.enrichGraph(complianceCase.getPspId(), caseId, graph, transactionDetails, related, relatedDepth,
                    relatedWindowDays, supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader);
        } else {
            graph.setTransactionDetailsStatus(com.posgateway.aml.dto.edgedata.EdgePullStatus.none());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(graph);
    }
}
