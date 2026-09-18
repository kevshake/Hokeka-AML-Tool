package com.posgateway.aml.service.graph;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.graph.MerchantNode;
import com.posgateway.aml.entity.graph.TransactionNode;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.graph.MerchantNodeRepository;
import com.posgateway.aml.repository.graph.TransactionNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Projects committed relational transactions into the Neo4j investigation graph. */
@Service
@ConditionalOnProperty(name = "neo4j.enabled", havingValue = "true")
public class Neo4jGraphIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphIngestionService.class);

    private final MerchantNodeRepository merchantNodeRepository;
    private final TransactionNodeRepository transactionNodeRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;

    public Neo4jGraphIngestionService(MerchantNodeRepository merchantNodeRepository,
                                      TransactionNodeRepository transactionNodeRepository,
                                      MerchantRepository merchantRepository,
                                      TransactionRepository transactionRepository) {
        this.merchantNodeRepository = merchantNodeRepository;
        this.transactionNodeRepository = transactionNodeRepository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional("neo4jTransactionManager")
    public void ingestTransaction(TransactionEntity tx) {
        if (tx == null || tx.getTxnId() == null) {
            throw new IllegalArgumentException("Persisted transaction with txnId is required");
        }
        Long merchantId = parseMerchantId(tx.getMerchantId());
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot project transaction " + tx.getTxnId() + ": merchant " + merchantId + " not found"));

        MerchantNode merchantNode = upsertMerchant(merchant);
        String channel = firstNonBlank(tx.getChannelType(), merchant.getTransactionChannel(), "UNKNOWN");
        java.util.Optional<TransactionNode> existingTransaction =
                transactionNodeRepository.findById(String.valueOf(tx.getTxnId()));
        TransactionNode txnNode = existingTransaction
                .orElseGet(() -> new TransactionNode(
                        String.valueOf(tx.getTxnId()), toUnits(tx.getAmountCents()), tx.getCurrency(), channel));
        txnNode.setAmount(toUnits(tx.getAmountCents()));
        txnNode.setCurrency(tx.getCurrency());
        txnNode.setChannel(channel);
        txnNode.setTxnType(tx.getDirection());
        txnNode.setStatus(tx.getAcquirerResponse());
        txnNode.setTimestamp(tx.getTxnTs() != null ? tx.getTxnTs() : LocalDateTime.now());
        txnNode.setRiskScore(tx.getTrs());
        txnNode.setDecision(tx.getDecision());
        txnNode.setFromMerchant(merchantNode);
        transactionNodeRepository.save(txnNode);
        if (existingTransaction.isEmpty()) {
            linkMerchantsSharingInstrument(tx, merchant, merchantNode);
        }
        logger.debug("Projected transaction {} into Neo4j", tx.getTxnId());
    }

    private void linkMerchantsSharingInstrument(TransactionEntity tx, Merchant merchant, MerchantNode merchantNode) {
        if (tx.getPanHash() == null || tx.getPanHash().isBlank()) {
            return;
        }
        Set<String> relatedMerchantIds = new LinkedHashSet<>();
        for (TransactionEntity related : transactionRepository.findByPanHash(tx.getPanHash())) {
            if (related.getMerchantId() != null && !related.getMerchantId().equals(tx.getMerchantId())) {
                relatedMerchantIds.add(related.getMerchantId());
            }
        }
        for (String relatedMerchantId : relatedMerchantIds) {
            try {
                Merchant relatedMerchant = merchantRepository.findById(parseMerchantId(relatedMerchantId))
                        .orElse(null);
                if (relatedMerchant == null || !sameTenant(merchant, relatedMerchant)) {
                    continue;
                }
                MerchantNode relatedNode = upsertMerchant(relatedMerchant);
                merchantNode.addTransactionWith(relatedNode, toUnits(tx.getAmountCents()).doubleValue(), 1L);
            } catch (IllegalArgumentException legacyIdentifier) {
                logger.warn("Skipping non-platform related merchant ID in graph projection: {}", relatedMerchantId);
            }
        }
        merchantNodeRepository.save(merchantNode);
    }

    private boolean sameTenant(Merchant left, Merchant right) {
        Long leftPsp = left.getPsp() != null ? left.getPsp().getPspId() : null;
        Long rightPsp = right.getPsp() != null ? right.getPsp().getPspId() : null;
        return leftPsp != null && leftPsp.equals(rightPsp);
    }

    private MerchantNode upsertMerchant(Merchant merchant) {
        String merchantId = String.valueOf(merchant.getMerchantId());
        MerchantNode node = merchantNodeRepository.findByMerchantId(merchantId)
                .orElseGet(() -> new MerchantNode(
                        merchantId, merchant.getLegalName(), merchant.getMcc(), merchant.getCountry()));
        node.setLegalName(merchant.getLegalName());
        node.setTradingName(merchant.getTradingName());
        node.setMcc(merchant.getMcc());
        node.setCountry(merchant.getCountry());
        node.setRiskLevel(merchant.getRiskLevel());
        return merchantNodeRepository.save(node);
    }

    @Transactional("neo4jTransactionManager")
    public void linkMerchants(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        MerchantNode from = merchantNodeRepository.findByMerchantId(fromMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph merchant: " + fromMerchantId));
        MerchantNode to = merchantNodeRepository.findByMerchantId(toMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph merchant: " + toMerchantId));
        from.addTransactionWith(to, amount.doubleValue(), 1L);
        merchantNodeRepository.save(from);
    }

    @Transactional("neo4jTransactionManager")
    public void updateMerchantMetrics(String merchantId, Double pageRank, Long communityId, Double betweenness) {
        MerchantNode merchant = merchantNodeRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph merchant: " + merchantId));
        merchant.setPageRank(pageRank);
        merchant.setCommunityId(communityId);
        merchant.setBetweenness(betweenness);
        merchantNodeRepository.save(merchant);
    }

    private Long parseMerchantId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Transaction merchantId is not a platform merchant ID: " + value, invalid);
        }
    }

    private BigDecimal toUnits(Long amountCents) {
        return amountCents == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(amountCents, 2);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
