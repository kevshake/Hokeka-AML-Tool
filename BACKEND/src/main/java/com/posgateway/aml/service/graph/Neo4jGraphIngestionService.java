package com.posgateway.aml.service.graph;

import com.posgateway.aml.entity.graph.*;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.graph.MerchantNodeRepository;
import com.posgateway.aml.repository.graph.TransactionNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for ingesting transactions into Neo4j graph database.
 * Converts relational transaction data into graph nodes and relationships.
 */
@Service
@ConditionalOnProperty(name = "neo4j.enabled", havingValue = "true", matchIfMissing = false)
public class Neo4jGraphIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphIngestionService.class);

    private final MerchantNodeRepository merchantNodeRepository;
    private final TransactionNodeRepository transactionNodeRepository;

    @Autowired
    public Neo4jGraphIngestionService(
            MerchantNodeRepository merchantNodeRepository,
            TransactionNodeRepository transactionNodeRepository) {
        this.merchantNodeRepository = merchantNodeRepository;
        this.transactionNodeRepository = transactionNodeRepository;
    }

    /**
     * Ingest a transaction into the Neo4j graph.
     * Creates/updates nodes and relationships.
     */
    @Transactional
    public void ingestTransaction(TransactionEntity tx) {
        logger.debug("Ingesting transaction {} into Neo4j graph", tx.getTxnId());

        try {
            // 1. Get or create merchant node
            MerchantNode merchantNode = getOrCreateMerchant(tx);

            // 2. Create transaction node
            TransactionNode txnNode = new TransactionNode(
                    tx.getTxnId().toString(),
                    tx.getAmountCents() != null
                            ? BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO,
                    tx.getCurrency(),
                    "POS"); // Default channel when not available
            txnNode.setFromMerchant(merchantNode);
            txnNode.setTimestamp(tx.getTxnTs() != null ? tx.getTxnTs() : LocalDateTime.now());

            // 3. Save transaction
            transactionNodeRepository.save(txnNode);

            logger.info("Transaction {} ingested into Neo4j graph", tx.getTxnId());

        } catch (Exception e) {
            logger.error("Error ingesting transaction {} into Neo4j: {}", tx.getTxnId(), e.getMessage());
        }
    }

    /**
     * Get or create a MerchantNode for the given transaction.
     */
    private MerchantNode getOrCreateMerchant(TransactionEntity tx) {
        String merchantId = tx.getMerchantId() != null ? tx.getMerchantId().toString() : "UNKNOWN";

        return merchantNodeRepository.findByMerchantId(merchantId)
                .orElseGet(() -> {
                    MerchantNode newMerchant = new MerchantNode(
                            merchantId,
                            "Merchant " + merchantId,
                            "0000", // Default MCC - TransactionEntity doesn't have this
                            "UNK"); // Default country - TransactionEntity doesn't have this
                    return merchantNodeRepository.save(newMerchant);
                });
    }

    /**
     * Create a relationship between two merchants based on a transaction.
     */
    @Transactional
    public void linkMerchants(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        MerchantNode from = merchantNodeRepository.findByMerchantId(fromMerchantId).orElse(null);
        MerchantNode to = merchantNodeRepository.findByMerchantId(toMerchantId).orElse(null);

        if (from != null && to != null) {
            from.addTransactionWith(to, amount.doubleValue(), 1L);
            merchantNodeRepository.save(from);
            logger.info("Linked merchants {} -> {} with amount {}", fromMerchantId, toMerchantId, amount);
        }
    }

    /**
     * Update merchant graph metrics from Neo4j GDS computations.
     */
    @Transactional
    public void updateMerchantMetrics(String merchantId, Double pageRank, Long communityId, Double betweenness) {
        merchantNodeRepository.findByMerchantId(merchantId).ifPresent(merchant -> {
            merchant.setPageRank(pageRank);
            merchant.setCommunityId(communityId);
            merchant.setBetweenness(betweenness);
            merchantNodeRepository.save(merchant);
            logger.debug("Updated graph metrics for merchant {}", merchantId);
        });
    }
}
