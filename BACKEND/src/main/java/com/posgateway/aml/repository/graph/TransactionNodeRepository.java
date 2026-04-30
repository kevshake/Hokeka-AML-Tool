package com.posgateway.aml.repository.graph;

import com.posgateway.aml.entity.graph.TransactionNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for TransactionNode entities.
 * Provides graph-specific queries for transaction analysis.
 */
@Repository
public interface TransactionNodeRepository extends Neo4jRepository<TransactionNode, String> {

    Optional<TransactionNode> findByTxnId(String txnId);

    @Query("MATCH (t:Transaction)-[:FROM_MERCHANT]->(m:Merchant) " +
            "WHERE m.merchantId = $merchantId " +
            "RETURN t ORDER BY t.timestamp DESC LIMIT $limit")
    List<TransactionNode> findByMerchant(String merchantId, int limit);

    @Query("MATCH (t:Transaction) " +
            "WHERE t.timestamp >= $startTime AND t.timestamp <= $endTime " +
            "RETURN t ORDER BY t.timestamp DESC")
    List<TransactionNode> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    @Query("MATCH (t:Transaction) " +
            "WHERE t.riskScore > $threshold " +
            "RETURN t ORDER BY t.riskScore DESC LIMIT 100")
    List<TransactionNode> findHighRiskTransactions(Double threshold);

    @Query("MATCH (t:Transaction)-[:USED_DEVICE]->(d:Device) " +
            "WHERE d.deviceId = $deviceId " +
            "RETURN t ORDER BY t.timestamp DESC")
    List<TransactionNode> findByDevice(String deviceId);
}
