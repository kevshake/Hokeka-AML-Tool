package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Memory-Optimized Transaction Ingestion
 * Minimizes object allocation for high concurrency (30K+ requests)
 */
// @RequiredArgsConstructor removed
@Service
public class MemoryOptimizedTransactionIngestion {

    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizedTransactionIngestion.class);

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    // Thread-local MessageDigest for PAN hashing (reuse instances)
    private static final ThreadLocal<MessageDigest> DIGEST_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MessageDigest", e);
        }
    });

    // Pre-allocated StringBuilder for hex conversion (64 chars for SHA-256)
    private static final ThreadLocal<StringBuilder> HEX_BUILDER_CACHE = ThreadLocal
            .withInitial(() -> new StringBuilder(64));

    @Autowired
    public MemoryOptimizedTransactionIngestion(TransactionRepository transactionRepository,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingest transaction with minimal memory allocation
     * 
     * @param request Transaction request
     * @return Saved transaction entity
     */
    @Transactional
    public TransactionEntity ingestTransactionOptimized(
            TransactionIngestionService.TransactionRequest request) {

        TransactionEntity transaction = new TransactionEntity();

        transaction.setIsoMsg(request.getIsoMsg());

        // Hash PAN using thread-local MessageDigest
        if (request.getPan() != null && !request.getPan().isEmpty()) {
            transaction.setPanHash(hashPanOptimized(request.getPan()));
        }

        transaction.setMerchantId(request.getMerchantId());
        transaction.setTerminalId(request.getTerminalId());
        transaction.setAmountCents(request.getAmountCents());
        transaction.setCurrency(request.getCurrency());
        transaction.setTxnTs(request.getTxnTs() != null ? request.getTxnTs() : LocalDateTime.now());

        // Store EMV tags as JSON (lazy serialization)
        if (request.getEmvTags() != null && !request.getEmvTags().isEmpty()) {
            try {
                String emvJson = objectMapper.writeValueAsString(request.getEmvTags());
                transaction.setEmvTags(emvJson);
            } catch (Exception e) {
                logger.warn("Failed to serialize EMV tags", e);
            }
        }

        transaction.setAcquirerResponse(request.getAcquirerResponse());

        return transactionRepository.save(transaction);
    }

    /**
     * Ultra-optimized PAN hashing with thread-local reuse
     */
    private String hashPanOptimized(String pan) {
        try {
            MessageDigest digest = DIGEST_CACHE.get();
            digest.reset(); // Reset for reuse

            byte[] hash = digest.digest(pan.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Reuse StringBuilder from thread-local cache
            StringBuilder hexString = HEX_BUILDER_CACHE.get();
            hexString.setLength(0); // Clear for reuse

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

    /**
     * Batch ingest with optimized memory usage
     */
    @Transactional
    public List<TransactionEntity> batchIngestOptimized(
            List<TransactionIngestionService.TransactionRequest> requests) {

        List<TransactionEntity> transactions = new ArrayList<>(requests.size());

        for (TransactionIngestionService.TransactionRequest request : requests) {
            TransactionEntity transaction = new TransactionEntity();
            transaction.setIsoMsg(request.getIsoMsg());

            if (request.getPan() != null && !request.getPan().isEmpty()) {
                transaction.setPanHash(hashPanOptimized(request.getPan()));
            }

            transaction.setMerchantId(request.getMerchantId());
            transaction.setTerminalId(request.getTerminalId());
            transaction.setAmountCents(request.getAmountCents());
            transaction.setCurrency(request.getCurrency());
            transaction.setTxnTs(request.getTxnTs() != null ? request.getTxnTs() : LocalDateTime.now());

            if (request.getEmvTags() != null && !request.getEmvTags().isEmpty()) {
                try {
                    transaction.setEmvTags(objectMapper.writeValueAsString(request.getEmvTags()));
                } catch (Exception e) {
                    logger.warn("Failed to serialize EMV tags", e);
                }
            }

            transaction.setAcquirerResponse(request.getAcquirerResponse());
            transactions.add(transaction);
        }

        // Batch save for maximum performance
        return transactionRepository.saveAll(transactions);
    }
}
