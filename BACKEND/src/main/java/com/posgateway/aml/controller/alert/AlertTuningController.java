package com.posgateway.aml.controller.alert;

import com.posgateway.aml.entity.alert.AlertTuningRecommendation;
import com.posgateway.aml.service.alert.AlertTuningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Alert Tuning Controller
 * Provides REST endpoints for alert tuning recommendations and rule effectiveness
 */
@RestController
@RequestMapping("/alerts/tuning")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST')")
@Tag(name = "Alert Tuning", description = "APIs for alert tuning recommendations and rule effectiveness analysis")
public class AlertTuningController {

    private final AlertTuningService alertTuningService;

    @Autowired
    public AlertTuningController(AlertTuningService alertTuningService) {
        this.alertTuningService = alertTuningService;
    }

    @Operation(
            summary = "Suggest tuning for a rule",
            description = "Generates ML-based tuning recommendations for an alert rule based on false positive rate"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tuning recommendation generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @PostMapping("/suggest")
    public ResponseEntity<AlertTuningRecommendation> suggestTuning(
            @Parameter(description = "Rule name", required = true, example = "HIGH_VALUE_TRANSACTION")
            @RequestParam String ruleName,
            @Parameter(description = "False positive rate (0.0 to 1.0)", required = true, example = "0.45")
            @RequestParam double falsePositiveRate) {
        AlertTuningRecommendation recommendation = alertTuningService.suggestTuning(ruleName, falsePositiveRate);
        return ResponseEntity.ok(recommendation);
    }

    @Operation(
            summary = "Get pending tuning recommendations",
            description = "Retrieves all pending alert tuning recommendations"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully")
    })
    @GetMapping("/pending")
    public ResponseEntity<List<AlertTuningRecommendation>> getPendingRecommendations() {
        List<AlertTuningRecommendation> recommendations = alertTuningService.getPendingRecommendations();
        return ResponseEntity.ok(recommendations);
    }

    @Operation(
            summary = "Apply tuning recommendation",
            description = "Marks a tuning recommendation as applied"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendation applied successfully"),
            @ApiResponse(responseCode = "404", description = "Recommendation not found")
    })
    @PostMapping("/{recommendationId}/apply")
    public ResponseEntity<Map<String, Object>> applyRecommendation(
            @Parameter(description = "Recommendation ID", required = true)
            @PathVariable Long recommendationId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername()); // Assuming username is user ID
        alertTuningService.applyRecommendation(recommendationId, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Recommendation applied successfully",
                "recommendationId", recommendationId
        ));
    }
}

