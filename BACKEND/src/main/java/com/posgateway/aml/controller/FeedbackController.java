package com.posgateway.aml.controller;

import com.posgateway.aml.dto.LabelTransactionRequestDTO;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.service.FeedbackLabelingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feedback Controller
 * Handles transaction labeling for model retraining
 */
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackLabelingService labelingService;

    @Autowired
    public FeedbackController(FeedbackLabelingService labelingService) {
        this.labelingService = labelingService;
    }

    /**
     * Label a transaction
     * POST /api/v1/feedback/label
     * 
     * @param request Label request
     * @return Updated transaction features
     */
    @PostMapping("/label")
    public ResponseEntity<TransactionFeatures> labelTransaction(
            @Valid @RequestBody LabelTransactionRequestDTO request) {
        
        logger.info("Received labeling request for transaction: {}", request.getTxnId());

        try {
            TransactionFeatures features = labelingService.labelTransaction(
                request.getTxnId(), 
                request.getLabel(), 
                request.getInvestigator(),
                request.getNotes());
            
            return ResponseEntity.ok(features);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid labeling request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error labeling transaction: {}", request.getTxnId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Batch label transactions
     * POST /api/v1/feedback/label/batch
     * 
     * @param request Map of transaction IDs to labels
     * @param investigator Investigator name
     * @return Number of transactions labeled
     */
    @PostMapping("/label/batch")
    public ResponseEntity<Map<String, Integer>> labelTransactionsBatch(
            @RequestBody Map<Long, Short> request,
            @RequestParam String investigator) {
        
        logger.info("Received batch labeling request for {} transactions", request.size());

        try {
            int count = labelingService.labelTransactionsBatch(request, investigator);
            return ResponseEntity.ok(Map.of("labeled", count));
        } catch (Exception e) {
            logger.error("Error batch labeling transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get labeled transactions
     * GET /api/v1/feedback/labeled
     * 
     * @return List of labeled transactions
     */
    @GetMapping("/labeled")
    public ResponseEntity<List<TransactionFeatures>> getLabeledTransactions() {
        List<TransactionFeatures> labeled = labelingService.getLabeledTransactions();
        return ResponseEntity.ok(labeled);
    }

    /**
     * Get unlabeled transactions
     * GET /api/v1/feedback/unlabeled
     * 
     * @param limit Maximum number to return
     * @return List of unlabeled transactions
     */
    @GetMapping("/unlabeled")
    public ResponseEntity<List<TransactionFeatures>> getUnlabeledTransactions(
            @RequestParam(defaultValue = "100") int limit) {
        List<TransactionFeatures> unlabeled = labelingService.getUnlabeledTransactions(limit);
        return ResponseEntity.ok(unlabeled);
    }

    /**
     * Get labeling statistics
     * GET /api/v1/feedback/statistics
     * 
     * @return Labeling statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<FeedbackLabelingService.LabelingStatistics> getStatistics() {
        FeedbackLabelingService.LabelingStatistics stats = labelingService.getLabelingStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint
     * GET /api/v1/feedback/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Feedback Service is running");
    }
}

