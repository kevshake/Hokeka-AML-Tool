package com.posgateway.aml.controller;

import com.posgateway.aml.service.BatchScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Batch Controller
 * Provides endpoints for batch processing operations
 */
@RestController
@RequestMapping("/batch")
public class BatchController {

    private static final Logger logger = LoggerFactory.getLogger(BatchController.class);

    private final BatchScoringService batchScoringService;

    @Autowired
    public BatchController(BatchScoringService batchScoringService) {
        this.batchScoringService = batchScoringService;
    }

    /**
     * Trigger batch scoring for yesterday's transactions
     * POST /api/v1/batch/score/yesterday
     * 
     * @return Success message
     */
    @PostMapping("/score/yesterday")
    public ResponseEntity<String> batchScoreYesterday() {
        logger.info("Manual batch scoring triggered");
        batchScoringService.batchScoreYesterdayTransactions();
        return ResponseEntity.ok("Batch scoring completed");
    }

    /**
     * Backfill features for transactions without features
     * POST /api/v1/batch/backfill/features
     * 
     * @param limit Maximum number of transactions to process
     * @return Number of transactions processed
     */
    @PostMapping("/backfill/features")
    public ResponseEntity<Map<String, Integer>> backfillFeatures(
            @RequestParam(defaultValue = "1000") int limit) {
        logger.info("Manual feature backfill triggered with limit: {}", limit);
        int processed = batchScoringService.backfillFeatures(limit);
        return ResponseEntity.ok(Map.of("processed", processed));
    }

    /**
     * Health check endpoint
     * GET /api/v1/batch/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Batch Service is running");
    }
}

