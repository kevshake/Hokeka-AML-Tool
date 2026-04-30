package com.posgateway.aml.repository.graph;

import com.posgateway.aml.entity.graph.MerchantNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for MerchantNode entities.
 * Provides graph-specific queries for AML analysis.
 */
@Repository
public interface MerchantNodeRepository extends Neo4jRepository<MerchantNode, String> {

    Optional<MerchantNode> findByMerchantId(String merchantId);

    List<MerchantNode> findByCountry(String country);

    List<MerchantNode> findByRiskLevel(String riskLevel);

    @Query("MATCH (m:Merchant) WHERE m.mcc = $mcc RETURN m")
    List<MerchantNode> findByMcc(String mcc);

    @Query("MATCH (m:Merchant)-[r:TRANSACTS_WITH]->(other:Merchant) " +
            "WHERE m.merchantId = $merchantId " +
            "RETURN other, r")
    List<MerchantNode> findConnectedMerchants(String merchantId);

    @Query("MATCH (m:Merchant) " +
            "WHERE m.pageRank > $threshold " +
            "RETURN m ORDER BY m.pageRank DESC LIMIT 100")
    List<MerchantNode> findHighInfluenceMerchants(Double threshold);

    @Query("MATCH (m:Merchant) " +
            "WHERE m.communityId = $communityId " +
            "RETURN m")
    List<MerchantNode> findByCommunity(Long communityId);

    @Query("MATCH (m:Merchant)-[r:TRANSACTS_WITH*1..3]-(other:Merchant) " +
            "WHERE m.merchantId = $merchantId " +
            "RETURN DISTINCT other")
    List<MerchantNode> findNetworkWithin3Hops(String merchantId);
}
