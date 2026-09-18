package com.posgateway.aml.service.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("${neo4j.enabled:false} and ${kafka.enabled:true}")
public class Neo4jTransactionProjectionConsumer {

    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final Neo4jGraphIngestionService graphIngestionService;

    public Neo4jTransactionProjectionConsumer(ObjectMapper objectMapper,
                                               TransactionRepository transactionRepository,
                                               Neo4jGraphIngestionService graphIngestionService) {
        this.objectMapper = objectMapper;
        this.transactionRepository = transactionRepository;
        this.graphIngestionService = graphIngestionService;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_TRANSACTIONS_RAW, groupId = "aml-neo4j-projection")
    public void projectCommittedTransaction(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode txnIdNode = root.get("transactionId");
        if (txnIdNode == null || !txnIdNode.canConvertToLong()) {
            throw new IllegalArgumentException("Raw transaction event has no numeric transactionId");
        }
        Long txnId = txnIdNode.asLong();
        TransactionEntity transaction = transactionRepository.findById(txnId)
                .orElseThrow(() -> new IllegalStateException(
                        "Committed transaction " + txnId + " was not found for Neo4j projection"));
        graphIngestionService.ingestTransaction(transaction);
    }
}
