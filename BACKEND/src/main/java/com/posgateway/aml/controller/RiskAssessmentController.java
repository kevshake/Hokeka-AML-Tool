package com.posgateway.aml.controller;

import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.service.RiskAssessmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Risk Assessment API
 * Provides endpoints for AML and Fraud risk assessment
 */
@RestController
@RequestMapping("/risk-assessment")
public class RiskAssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentController.class);

    private final RiskAssessmentService riskAssessmentService;

    @Autowired
    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    /**
     * Assess risk for a transaction
     * POST /api/v1/risk-assessment/assess
     * 
     * @param transaction The transaction to assess
     * @return RiskAssessment result
     */
    @PostMapping("/assess")
    public ResponseEntity<RiskAssessment> assessRisk(@Valid @RequestBody Transaction transaction) {
        logger.info("Received risk assessment request for transaction: {}", 
            transaction.getTransactionId());

        try {
            RiskAssessment assessment = riskAssessmentService.assessRisk(transaction);
            return ResponseEntity.ok(assessment);
        } catch (Exception e) {
            logger.error("Error assessing risk for transaction: {}", 
                transaction.getTransactionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/risk-assessment/health
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Risk Assessment Service is running");
    }
}

