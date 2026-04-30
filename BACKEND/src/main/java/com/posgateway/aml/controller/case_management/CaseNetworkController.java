package com.posgateway.aml.controller.case_management;

import com.posgateway.aml.service.case_management.CaseNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Case Network Controller
 * Provides endpoints for case network graph visualization
 */
@RestController
@RequestMapping("/cases/{caseId}/network")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'CASE_MANAGER')")
public class CaseNetworkController {

    private final CaseNetworkService caseNetworkService;

    @Autowired
    public CaseNetworkController(CaseNetworkService caseNetworkService) {
        this.caseNetworkService = caseNetworkService;
    }

    @GetMapping
    public ResponseEntity<CaseNetworkService.NetworkGraphDTO> getNetworkGraph(
            @PathVariable Long caseId,
            @RequestParam(defaultValue = "2") int depth) {
        return ResponseEntity.ok(caseNetworkService.buildNetworkGraph(caseId, depth));
    }
}

