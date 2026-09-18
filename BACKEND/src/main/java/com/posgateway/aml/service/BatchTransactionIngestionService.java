package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch Transaction Ingestion Service
 * High-throughput batch processing for transaction ingestion
 * Uses batch inserts for maximum performance
 */
// @RequiredArgsConstructor removed
@Service
public class BatchTransactionIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(BatchTransactionIngestionService.class);

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${throughput.batch.size:100}")
    private int batchSize;

    @Autowired
    public BatchTransactionIngestionService(TransactionRepository transactionRepository,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch ingest transactions for high throughput
     * 
     * @param requests List of transaction requests
     * @return List of saved transaction entities
     */
    @Transactional
    public List<TransactionEntity> batchIngestTransactions(
            List<TransactionIngestionService.TransactionRequest> requests) {

        logger.info("Batch ingesting {} transactions", requests.size());

        List<TransactionEntity> transactions = new ArrayList<>(requests.size());

        for (TransactionIngestionService.TransactionRequest request : requests) {
            TransactionEntity transaction = createTransactionEntity(request);
            transactions.add(transaction);
        }

        // Batch save for better performance
        List<TransactionEntity> saved = transactionRepository.saveAll(transactions);

        logger.info("Batch ingested {} transactions successfully", saved.size());
        return saved;
    }

    /**
     * Create transaction entity from request (optimized)
     */
    private TransactionEntity createTransactionEntity(TransactionIngestionService.TransactionRequest request) {
        TransactionEntity transaction = new TransactionEntity();

        transaction.setIsoMsg(request.getIsoMsg());

        // Hash PAN (optimized)
        if (request.getPan() != null && !request.getPan().isEmpty()) {
            transaction.setPanHash(hashPanOptimized(request.getPan()));
        }

        transaction.setMerchantId(request.getMerchantId());
        transaction.setTerminalId(request.getTerminalId());
        transaction.setAmountCents(request.getAmountCents());
        transaction.setCurrency(request.getCurrency());
        transaction.setTxnTs(request.getTxnTs() != null ? request.getTxnTs() : LocalDateTime.now());

        // Store EMV tags as JSON (optimized)
        if (request.getEmvTags() != null && !request.getEmvTags().isEmpty()) {
            try {
                String emvJson = objectMapper.writeValueAsString(request.getEmvTags());
                transaction.setEmvTags(emvJson);
            } catch (Exception e) {
                logger.warn("Failed to serialize EMV tags", e);
            }
        }

        transaction.setAcquirerResponse(request.getAcquirerResponse());

        return transaction;
    }

    /**
     * Optimized PAN hashing with cached MessageDigest
     */
    private static final ThreadLocal<MessageDigest> DIGEST_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MessageDigest", e);
        }
    });

    private String hashPanOptimized(String pan) {
        try {
            MessageDigest digest = DIGEST_CACHE.get();
            digest.reset(); // Reset for reuse
            byte[] hash = digest.digest(pan.getBytes());

            // Optimized hex string building
            StringBuilder hexString = new StringBuilder(64); // Pre-allocate
            for (byte b : hash) {
                int value = 0xff & b;
                if (value < 16) {
                    hexString.append('0');
                }
                hexString.append(Integer.toHexString(value));
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Failed to hash PAN", e);
            return pan;
        }
    }
}
