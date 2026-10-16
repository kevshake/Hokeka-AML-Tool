package com.posgateway.aml.controller;

import com.posgateway.aml.dto.FraudDetectionResponseDTO;
import com.posgateway.aml.dto.alert.TransactionAlertsResponseDTO;
import com.posgateway.aml.dto.assessment.AssessmentDetailResponseDTO;
import com.posgateway.aml.dto.TransactionRequestDTO;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.AsyncFraudDetectionOrchestrator;
import com.posgateway.aml.service.BatchTransactionIngestionService;
import com.posgateway.aml.service.FraudDetectionOrchestrator;
import com.posgateway.aml.service.ConnectionCleanupService;
import com.posgateway.aml.service.HighConcurrencyFraudOrchestrator;
import com.posgateway.aml.service.RequestBufferingService;
import com.posgateway.aml.service.RequestRateLimiter;
import com.posgateway.aml.service.TransactionIngestionService;
import com.posgateway.aml.service.FraudDetectionOrchestrator.FraudDetectionResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.edge.EdgeAssessmentConflictException;
import com.posgateway.aml.service.edge.EdgeAssessmentService;

import com.posgateway.aml.mapper.FraudDetectionMapper;
import com.posgateway.aml.repository.TransactionRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Transaction Controller
 * Receives transactions from all merchants and processes them through fraud
 * detection
 * Optimized for high throughput with async processing
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionIngestionService ingestionService;
    private final BatchTransactionIngestionService batchIngestionService;
    private final FraudDetectionOrchestrator fraudOrchestrator;
    private final AsyncFraudDetectionOrchestrator asyncFraudOrchestrator;
    private final HighConcurrencyFraudOrchestrator highConcurrencyOrchestrator;
    private final TransactionRepository transactionRepository;
    private final FraudDetectionMapper fraudMapper;
    private final RequestBufferingService requestBufferingService;
    private final RequestRateLimiter rateLimiter;
    private final ConnectionCleanupService cleanupService;
    private final PspIsolationService pspIsolationService;
    private final EdgeAssessmentService edgeAssessments;
    private final com.posgateway.aml.service.assessment.AssessmentQueryService assessmentQueryService;
    private final com.posgateway.aml.service.alert.TransactionAlertQueryService transactionAlertQueryService;
    private final com.posgateway.aml.service.compliance.ComplianceLinksQueryService complianceLinksQueryService;
    private final com.posgateway.aml.service.pagination.KeysetSliceService keysetSliceService;
    /** Merchant ownership / edge id validation, run before anything is persisted. */
    private final com.posgateway.aml.service.security.TransactionIngestGuard ingestGuard;

    private final AtomicInteger currentConcurrentRequests = new AtomicInteger(0);

    @Value("${app.transaction.maxContextRequests:1000}")
    private int maxConcurrentRequests;

    @Value("${app.transaction.ultraThroughput:false}")
    private boolean ultraThroughputEnabled;

    @Value("${app.transaction.asyncEnabled:true}")
    private boolean asyncEnabled;

    @Autowired
    public TransactionController(TransactionIngestionService ingestionService,
            BatchTransactionIngestionService batchIngestionService,
            FraudDetectionOrchestrator fraudOrchestrator,
            AsyncFraudDetectionOrchestrator asyncFraudOrchestrator,
            HighConcurrencyFraudOrchestrator highConcurrencyOrchestrator,
            RequestBufferingService requestBufferingService,
            RequestRateLimiter rateLimiter,
            ConnectionCleanupService cleanupService,
            TransactionRepository transactionRepository,
            FraudDetectionMapper fraudMapper,
            PspIsolationService pspIsolationService,
            EdgeAssessmentService edgeAssessments,
            com.posgateway.aml.service.assessment.AssessmentQueryService assessmentQueryService,
            com.posgateway.aml.service.alert.TransactionAlertQueryService transactionAlertQueryService,
            com.posgateway.aml.service.compliance.ComplianceLinksQueryService complianceLinksQueryService,
            com.posgateway.aml.service.pagination.KeysetSliceService keysetSliceService,
            com.posgateway.aml.service.security.TransactionIngestGuard ingestGuard) {
        this.ingestGuard = ingestGuard;
        this.ingestionService = ingestionService;
        this.batchIngestionService = batchIngestionService;
        this.fraudOrchestrator = fraudOrchestrator;
        this.asyncFraudOrchestrator = asyncFraudOrchestrator;
        this.highConcurrencyOrchestrator = highConcurrencyOrchestrator;
        this.requestBufferingService = requestBufferingService;
        this.rateLimiter = rateLimiter;
        this.cleanupService = cleanupService;
        this.transactionRepository = transactionRepository;
        this.fraudMapper = fraudMapper;
        this.pspIsolationService = pspIsolationService;
        this.edgeAssessments = edgeAssessments;
        this.assessmentQueryService = assessmentQueryService;
        this.transactionAlertQueryService = transactionAlertQueryService;
        this.complianceLinksQueryService = complianceLinksQueryService;
        this.keysetSliceService = keysetSliceService;
    }

    /**
     * Receive and process transaction from merchant (High Throughput)
     * POST /api/v1/transactions/ingest
     * 
     * @param requestDTO Transaction request DTO from merchant
     * @return Fraud detection result
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN','PSP_USER','PSP_INTEGRATION')")
    public CompletableFuture<ResponseEntity<FraudDetectionResponseDTO>> ingestTransaction(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequestDTO requestDTO) {

        logger.debug("Received transaction ingestion request from merchant: {}",
                requestDTO.getMerchantId());

        // Client errors first, before anything is persisted: unknown / foreign merchant -> 404,
        // malformed merchant id or edgeAssessmentId -> 400 (JSON via GlobalExceptionHandler).
        ingestGuard.requireIngestableMerchant(requestDTO.getMerchantId());
        ingestGuard.requireValidEdgeAssessmentId(requestDTO.getEdgeAssessmentId());

        try {
            // Rate limiting check
            if (!rateLimiter.canProcess()) {
                logger.warn("Rate limit exceeded, rejecting request");
                return CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(createErrorResponse("Rate limit exceeded")));
            }

            // Convert DTO to service request
            TransactionIngestionService.TransactionRequest request =
                    convertToServiceRequest(requestDTO, idempotencyKey);

            // Step 1: Ingest transaction (store in database)
            TransactionEntity transaction = ingestionService.ingestTransaction(request);

            if (requestDTO.getEdgeAssessmentId() != null && !requestDTO.getEdgeAssessmentId().isBlank()) {
                try {
                    FraudDetectionResult adopted = edgeAssessments
                            .adopt(transaction, requestDTO.getEdgeAssessmentId())
                            .orElseThrow(() -> new IllegalStateException("edge assessment id was present"));
                    return CompletableFuture.completedFuture(
                            ResponseEntity.ok(fraudMapper.toResponse(adopted)));
                } catch (EdgeAssessmentConflictException conflict) {
                    return CompletableFuture.completedFuture(
                            ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(createErrorResponse(conflict.getMessage())));
                }
            }

            // Check concurrent request limit
            int current = currentConcurrentRequests.incrementAndGet();
            try {
                if (current > maxConcurrentRequests) {
                    logger.warn("Max concurrent requests ({}) exceeded, current: {}",
                            maxConcurrentRequests, current);
                    // Use buffering service if enabled
                    if (ultraThroughputEnabled && requestBufferingService.hasCapacity()) {
                        requestBufferingService.addRequest(requestDTO);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.accepted().build()); // 202 Accepted
                    }
                    return CompletableFuture.completedFuture(
                            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                    .body(createErrorResponse(
                                            "Service temporarily unavailable, too many concurrent requests")));
                }

                // Step 2: Process through fraud detection pipeline (ultra-high throughput if
                // enabled)
                // Optimize orchestrator selection with early returns and cached flags
                CompletableFuture<ResponseEntity<FraudDetectionResponseDTO>> future;

                // Use switch-like optimization: check ultra throughput first (highest priority)
                if (ultraThroughputEnabled) {
                    future = highConcurrencyOrchestrator.processTransactionUltra(transaction)
                            .thenApply(result -> {
                                FraudDetectionResponseDTO responseDTO = fraudMapper.toResponse(result);
                                return ResponseEntity.ok(responseDTO);
                            })
                            .exceptionally(ex -> {
                                logger.error("Error in ultra-high throughput processing", ex);
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                            });
                } else if (asyncEnabled) {
                    // Fallback to async processing if ultra throughput disabled
                    future = asyncFraudOrchestrator.processTransactionAsync(transaction)
                            .thenApply(result -> {
                                FraudDetectionResponseDTO responseDTO = fraudMapper.toResponse(result);
                                return ResponseEntity.ok(responseDTO);
                            })
                            .exceptionally(ex -> {
                                logger.error("Error in async processing", ex);
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                            });
                } else {
                    // Synchronous fallback (lowest priority)
                    FraudDetectionResult result = fraudOrchestrator.processTransaction(transaction);
                    FraudDetectionResponseDTO responseDTO = fraudMapper.toResponse(result);
                    future = CompletableFuture.completedFuture(ResponseEntity.ok(responseDTO));
                }

                // Decrement counter and cleanup resources when done
                return future.whenComplete((response, ex) -> {
                    currentConcurrentRequests.decrementAndGet();
                    // Cleanup resources after transaction completion
                    if (response != null && response.getBody() != null) {
                        try {
                            FraudDetectionResponseDTO body = response.getBody();
                            if (body != null && body.getTxnId() != null) {
                                cleanupService.cleanupAfterTransaction(body.getTxnId());
                            }
                        } catch (Exception cleanupEx) {
                            logger.debug("Error during cleanup: {}", cleanupEx.getMessage());
                        }
                    }
                });

            } catch (Exception e) {
                currentConcurrentRequests.decrementAndGet();
                throw e;
            }

        } catch (IllegalArgumentException | SecurityException | AccessDeniedException clientError) {
            // Validation / tenancy failures raised during ingest are the caller's fault: let
            // GlobalExceptionHandler render 400/404/403 JSON instead of an empty 500.
            throw clientError;
        } catch (Exception e) {
            logger.error("Error processing transaction from merchant: {}",
                    requestDTO.getMerchantId(), e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(createErrorResponse("Internal error while processing the transaction")));
        }
    }

    /**
     * Batch ingest transactions (High Throughput)
     * POST /api/v1/transactions/ingest/batch
     * 
     * @param requestDTOs List of transaction request DTOs
     * @return List of fraud detection results
     */
    @PostMapping("/ingest/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN','PSP_USER','PSP_INTEGRATION')")
    public CompletableFuture<ResponseEntity<List<FraudDetectionResponseDTO>>> batchIngestTransactions(
            @Valid @RequestBody List<TransactionRequestDTO> requestDTOs) {

        logger.info("Received batch ingestion request for {} transactions", requestDTOs.size());

        // Every row must target a merchant of the caller's PSP; reject the whole batch otherwise.
        for (TransactionRequestDTO dto : requestDTOs) {
            ingestGuard.requireIngestableMerchant(dto.getMerchantId());
            ingestGuard.requireValidEdgeAssessmentId(dto.getEdgeAssessmentId());
        }

        try {
            // Convert DTOs to service requests
            List<TransactionIngestionService.TransactionRequest> requests = requestDTOs.stream()
                    .map(dto -> convertToServiceRequest(dto, null))
                    .collect(Collectors.toList());

            // Batch ingest transactions
            List<TransactionEntity> transactions = batchIngestionService.batchIngestTransactions(requests);

            // Process transactions in parallel (optimized orchestrator selection)
            // Cache flags for better performance
            final boolean useUltra = ultraThroughputEnabled;
            final boolean useAsync = asyncEnabled;

            List<CompletableFuture<FraudDetectionResponseDTO>> futures;

            // Optimize with early return pattern - check ultra throughput first
            if (useUltra) {
                futures = highConcurrencyOrchestrator.processTransactionsParallel(transactions)
                        .stream()
                        .map(future -> future.thenApply(fraudMapper::toResponse))
                        .collect(Collectors.toList());
            } else if (useAsync) {
                futures = transactions.stream()
                        .map(txn -> asyncFraudOrchestrator.processTransactionAsync(txn)
                                .thenApply(fraudMapper::toResponse))
                        .collect(Collectors.toList());
            } else {
                // Synchronous fallback
                futures = transactions.stream()
                        .map(txn -> CompletableFuture.supplyAsync(() -> {
                            FraudDetectionResult result = fraudOrchestrator.processTransaction(txn);
                            return fraudMapper.toResponse(result);
                        }))
                        .collect(Collectors.toList());
            }

            // Wait for all futures
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<FraudDetectionResponseDTO> results = futures.stream()
                                .map(CompletableFuture::join)
                                .collect(Collectors.toList());
                        return ResponseEntity.ok(results);
                    })
                    .exceptionally(ex -> {
                        logger.error("Error in batch processing", ex);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    });

        } catch (IllegalArgumentException | SecurityException | AccessDeniedException clientError) {
            throw clientError;
        } catch (Exception e) {
            logger.error("Error in batch ingestion", e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }

    private TransactionIngestionService.TransactionRequest convertToServiceRequest(
            TransactionRequestDTO dto, String idempotencyKey) {
        TransactionIngestionService.TransactionRequest request = new TransactionIngestionService.TransactionRequest();
        request.setClientReference(resolveClientReference(idempotencyKey, dto));
        request.setMerchantId(dto.getMerchantId());
        request.setTerminalId(dto.getTerminalId());
        request.setAmountCents(dto.getAmountCents());
        request.setCurrency(dto.getCurrency());
        request.setPan(dto.getPan());
        request.setIsoMsg(dto.getIsoMsg());
        request.setEmvTags(dto.getEmvTags());
        request.setAcquirerResponse(dto.getAcquirerResponse());
        request.setDirection(dto.getDirection());
        request.setIpAddress(dto.getIpAddress());
        request.setCountryCode(dto.getCountryCode());
        request.setChannelType(dto.getChannelType());
        request.setCashTransaction(Boolean.TRUE.equals(dto.getCashTransaction()));
        request.setCustomerAccountReference(dto.getCustomerAccountReference());
        request.setCustomerEmail(dto.getCustomerEmail());
        return request;
    }

    private String resolveClientReference(String idempotencyKey, TransactionRequestDTO dto) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey.trim();
        }
        if (dto.getClientReference() != null && !dto.getClientReference().isBlank()) {
            return dto.getClientReference().trim();
        }
        return null;
    }

    private FraudDetectionResponseDTO createErrorResponse(String message) {
        FraudDetectionResponseDTO dto = new FraudDetectionResponseDTO();
        dto.setAction("ERROR");
        dto.setReasons(java.util.List.of(message));
        return dto;
    }

    /**
     * Latest assessment and findings for a transaction (read-only, shadow ledger).
     * GET /api/v1/transactions/{id}/assessment
     */
    @GetMapping("/{id}/assessment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'PSP_USER', 'VIEWER', 'PSP_INTEGRATION')")
    public ResponseEntity<AssessmentDetailResponseDTO> getTransactionAssessment(@PathVariable Long id) {
        return ResponseEntity.ok(assessmentQueryService.getLatestByTransactionId(id));
    }

    /**
     * Get transaction by ID
     * GET /transactions/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
    public ResponseEntity<TransactionEntity> getTransactionById(@PathVariable Long id) {
        logger.info("Get transaction by ID: {}", id);
        return transactionRepository.findById(id)
                .map(transaction -> {
                    try {
                        pspIsolationService.validateTransactionAccess(transaction);
                    } catch (SecurityException denied) {
                        throw new AccessDeniedException("Cannot access a transaction from another PSP", denied);
                    }
                    return ResponseEntity.ok(transaction);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists fraud/AML alerts for a transaction (primary txn_id plus V239 alert_transactions joins).
     * GET /api/v1/transactions/{id}/alerts
     */
    @GetMapping("/{id}/alerts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER','PSP_ANALYST','VIEWER')")
    public ResponseEntity<TransactionAlertsResponseDTO> getAlertsForTransaction(@PathVariable Long id) {
        return transactionRepository.findById(id)
                .map(transaction -> {
                    pspIsolationService.validateTransactionAccess(transaction);
                    return ResponseEntity.ok(transactionAlertQueryService.getAlertsForTransaction(id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns indexed cross-links (cases, SARs, disputes, screening) for a transaction investigation graph.
     */
    @GetMapping("/{id}/compliance-links")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER')")
    public ResponseEntity<com.posgateway.aml.dto.compliance.ComplianceLinksResponse> getComplianceLinks(
            @PathVariable Long id) {
        return transactionRepository.findById(id)
                .map(transaction -> {
                    pspIsolationService.validateTransactionAccess(transaction);
                    return ResponseEntity.ok(complianceLinksQueryService.forTransaction(id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all transactions with pagination
     * GET /transactions
     * 
     * Security: PSP users can only see their own PSP's transactions.
     * Platform Administrators can see all transactions or filter by PSP.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'VIEWER')")
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size,
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) String cursor) {
        logger.info("Get all transactions request (page: {}, size: {}, pspId: {})", page, size, pspId);
        try {
            int safeSize = Math.max(1, Math.min(size, 100)); // Max 100 per page
            int safePage = Math.max(0, page);
            
            // Enforce PSP isolation - sanitize PSP ID parameter
            Long sanitizedPspId = pspIsolationService.sanitizePspId(pspId);
            
            // Build Specification for filtering
            Specification<TransactionEntity> spec = Specification.where(null);
            
            // PSP Isolation Logic
            if (sanitizedPspId != null) {
                // Filter by PSP ID - PSP users automatically get their PSP, Platform Admins can specify
                spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), sanitizedPspId));
            }
            
            // Filter out null timestamps
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("txnTs")));
            
            var pageResult = keysetSliceService.slice(
                    transactionRepository,
                    spec,
                    safePage,
                    safeSize,
                    cursor,
                    "txnTs",
                    "txnId",
                    TransactionEntity::getTxnTs,
                    TransactionEntity::getTxnId);
            logger.info("Returning page {} with {} transactions (total: {})",
                    safePage, pageResult.content().size(), pageResult.totalElements());
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            logger.error("Error fetching transactions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/transactions/health
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transaction Service is running");
    }
}
