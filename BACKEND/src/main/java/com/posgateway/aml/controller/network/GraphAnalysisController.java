package com.posgateway.aml.controller.network;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console-facing status for Neo4j-backed graph analysis (off by default via {@code neo4j.enabled}).
 */
@RestController
@RequestMapping("/network/graph-analysis")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'CASE_MANAGER', 'ANALYST', 'PSP_ADMIN', 'PSP_USER')")
public class GraphAnalysisController {

    @Value("${neo4j.enabled:false}")
    private boolean neo4jEnabled;

    private final ObjectProvider<Driver> neo4jDriver;

    public GraphAnalysisController(ObjectProvider<Driver> neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * GET /api/v1/network/graph-analysis/status
     */
    @GetMapping("/status")
    public ResponseEntity<GraphAnalysisStatusResponse> getStatus() {
        boolean available = false;
        String reason = null;

        if (!neo4jEnabled) {
            reason = "Graph analysis is disabled on this Control Plane (neo4j.enabled=false).";
        } else {
            Driver driver = neo4jDriver.getIfAvailable();
            if (driver == null) {
                reason = "Neo4j driver is not available.";
            } else {
                try {
                    driver.verifyConnectivity();
                    available = true;
                } catch (Exception ex) {
                    reason = "Neo4j is enabled but not reachable.";
                }
            }
        }

        return ResponseEntity.ok(new GraphAnalysisStatusResponse(neo4jEnabled, available, reason));
    }

    public record GraphAnalysisStatusResponse(boolean enabled, boolean available, String reason) {}
}
