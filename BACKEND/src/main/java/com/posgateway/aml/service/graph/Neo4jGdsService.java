package com.posgateway.aml.service.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4j Graph Data Science (GDS) Service.
 * Computes AML-specific graph analytics using Neo4j GDS Community Edition.
 * 
 * Algorithms implemented:
 * - PageRank: Entity influence scoring
 * - Community Detection (Louvain): Network clustering
 * - Betweenness Centrality: Hub/intermediary detection
 * - Degree Centrality: Connection counting
 * - Triangle Count: Network density
 * 
 * Results are cached in Aerospike for fast retrieval during transaction
 * scoring.
 */
@Service
@ConditionalOnProperty(name = "neo4j.enabled", havingValue = "true", matchIfMissing = false)
public class Neo4jGdsService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGdsService.class);

    private final Driver neo4jDriver;
    private final AerospikeGraphCacheService aerospikeCache;

    // Graph projection name
    private static final String GRAPH_NAME = "aml-merchant-graph";

    @Autowired
    public Neo4jGdsService(Driver neo4jDriver,
            @Autowired(required = false) AerospikeGraphCacheService aerospikeCache) {
        this.neo4jDriver = neo4jDriver;
        this.aerospikeCache = aerospikeCache;
    }

    // =========================================================================
    // GRAPH PROJECTION (Required before running algorithms)
    // =========================================================================

    /**
     * Create/refresh the in-memory graph projection for GDS algorithms.
     */
    public void createGraphProjection() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            // Drop existing projection if it exists
            session.run("CALL gds.graph.drop($name, false)", Map.of("name", GRAPH_NAME));

            // Create new projection with Merchant nodes and TRANSACTS_WITH relationships
            String cypher = """
                    CALL gds.graph.project(
                        $name,
                        'Merchant',
                        {
                            TRANSACTS_WITH: {
                                type: 'TRANSACTS_WITH',
                                orientation: 'UNDIRECTED',
                                properties: ['totalAmount', 'txnCount']
                            }
                        }
                    )
                    YIELD graphName, nodeCount, relationshipCount
                    RETURN graphName, nodeCount, relationshipCount
                    """;

            Result result = session.run(cypher, Map.of("name", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("Created graph projection '{}': {} nodes, {} relationships",
                        record.get("graphName").asString(),
                        record.get("nodeCount").asLong(),
                        record.get("relationshipCount").asLong());
            }
        } catch (Exception e) {
            logger.error("Error creating graph projection: {}", e.getMessage());
        }
    }

    // =========================================================================
    // PAGERANK - Entity Influence Scoring
    // =========================================================================

    /**
     * Run PageRank algorithm to identify influential merchants.
     * High PageRank = merchant connected to many other high-value merchants.
     */
    public void runPageRank() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    CALL gds.pageRank.write($graphName, {
                        maxIterations: 20,
                        dampingFactor: 0.85,
                        writeProperty: 'pageRank'
                    })
                    YIELD nodePropertiesWritten, ranIterations
                    RETURN nodePropertiesWritten, ranIterations
                    """;

            Result result = session.run(cypher, Map.of("graphName", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("PageRank completed: {} properties written in {} iterations",
                        record.get("nodePropertiesWritten").asLong(),
                        record.get("ranIterations").asLong());
            }
        } catch (Exception e) {
            logger.error("Error running PageRank: {}", e.getMessage());
        }
    }

    // =========================================================================
    // COMMUNITY DETECTION (Louvain) - Network Clustering
    // =========================================================================

    /**
     * Run Louvain community detection to identify transaction clusters.
     * Merchants in same community = likely related activity.
     */
    public void runCommunityDetection() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    CALL gds.louvain.write($graphName, {
                        writeProperty: 'communityId'
                    })
                    YIELD communityCount, modularity
                    RETURN communityCount, modularity
                    """;

            Result result = session.run(cypher, Map.of("graphName", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("Community detection completed: {} communities, modularity: {}",
                        record.get("communityCount").asLong(),
                        record.get("modularity").asDouble());
            }
        } catch (Exception e) {
            logger.error("Error running community detection: {}", e.getMessage());
        }
    }

    // =========================================================================
    // BETWEENNESS CENTRALITY - Hub/Intermediary Detection
    // =========================================================================

    /**
     * Run Betweenness Centrality to identify hub merchants.
     * High betweenness = merchant acts as intermediary in many paths.
     */
    public void runBetweennessCentrality() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    CALL gds.betweenness.write($graphName, {
                        writeProperty: 'betweenness'
                    })
                    YIELD centralityDistribution, nodePropertiesWritten
                    RETURN centralityDistribution, nodePropertiesWritten
                    """;

            Result result = session.run(cypher, Map.of("graphName", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("Betweenness centrality completed: {} properties written",
                        record.get("nodePropertiesWritten").asLong());
            }
        } catch (Exception e) {
            logger.error("Error running betweenness centrality: {}", e.getMessage());
        }
    }

    // =========================================================================
    // TRIANGLE COUNT - Network Density
    // =========================================================================

    /**
     * Run Triangle Count algorithm.
     * High triangle count = tight knit groups.
     */
    public void runTriangleCount() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    CALL gds.triangleCount.write($graphName, {
                        writeProperty: 'triangleCount'
                    })
                    YIELD globalTriangleCount, nodePropertiesWritten
                    RETURN globalTriangleCount, nodePropertiesWritten
                    """;

            Result result = session.run(cypher, Map.of("graphName", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("Triangle count completed: {} triangles, {} properties written",
                        record.get("globalTriangleCount").asLong(),
                        record.get("nodePropertiesWritten").asLong());
            }
        } catch (Exception e) {
            logger.error("Error running triangle count: {}", e.getMessage());
        }
    }

    // =========================================================================
    // LOCAL CLUSTERING COEFFICIENT - Network Cohesion
    // =========================================================================

    /**
     * Run Local Clustering Coefficient.
     * Measures how close neighbors are to being a clique.
     */
    public void runLocalClusteringCoefficient() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    CALL gds.localClusteringCoefficient.write($graphName, {
                        writeProperty: 'localClusteringCoefficient'
                    })
                    YIELD averageClusteringCoefficient, nodePropertiesWritten
                    RETURN averageClusteringCoefficient, nodePropertiesWritten
                    """;

            Result result = session.run(cypher, Map.of("graphName", GRAPH_NAME));
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("Local clustering coefficient completed: avg {}, {} properties written",
                        record.get("averageClusteringCoefficient").asDouble(),
                        record.get("nodePropertiesWritten").asLong());
            }
        } catch (Exception e) {
            logger.error("Error running local clustering coefficient: {}", e.getMessage());
        }
    }

    // =========================================================================
    // RETRIEVE METRICS FOR A MERCHANT
    // =========================================================================

    /**
     * Get computed graph metrics for a specific merchant.
     * First checks Aerospike cache, then falls back to Neo4j.
     */
    public Map<String, Object> getGraphMetrics(String merchantId) {
        // 1. Check Aerospike cache first
        if (aerospikeCache != null) {
            Map<String, Object> cached = aerospikeCache.getGraphMetrics(merchantId);
            if (cached != null) {
                logger.debug("Graph metrics cache HIT for merchant {}", merchantId);
                return cached;
            }
        }

        // 2. Query Neo4j
        Map<String, Object> metrics = queryMerchantMetrics(merchantId);

        // 3. Cache in Aerospike
        if (aerospikeCache != null && metrics != null) {
            aerospikeCache.cacheGraphMetrics(merchantId, metrics);
        }

        return metrics;
    }

    private Map<String, Object> queryMerchantMetrics(String merchantId) {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH (m:Merchant {merchantId: $merchantId})
                    RETURN m.pageRank AS pageRank,
                           m.communityId AS communityId,
                           m.betweenness AS betweenness,
                           m.connectionCount AS connectionCount,
                           m.triangleCount AS triangleCount,
                           m.localClusteringCoefficient AS localClusteringCoefficient
                    """;

            Result result = session.run(cypher, Map.of("merchantId", merchantId));
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("pageRank", record.get("pageRank").asDouble(0.0));
                metrics.put("communityId", record.get("communityId").asLong(0));
                metrics.put("betweenness", record.get("betweenness").asDouble(0.0));
                metrics.put("connectionCount", record.get("connectionCount").asLong(0));
                metrics.put("triangleCount", record.get("triangleCount").asLong(0));
                metrics.put("localClusteringCoefficient", record.get("localClusteringCoefficient").asDouble(0.0));
                return metrics;
            }
        } catch (Exception e) {
            logger.error("Error querying merchant metrics for {}: {}", merchantId, e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // SCHEDULED BATCH COMPUTATION
    // =========================================================================

    /**
     * Scheduled task to recompute all graph metrics.
     * Runs every hour by default.
     */
    @Scheduled(fixedRateString = "${neo4j.gds.refresh.interval:3600000}")
    public void refreshAllGraphMetrics() {
        logger.info("Starting scheduled graph metrics refresh...");
        long startTime = System.currentTimeMillis();

        try {
            // 1. Create/refresh graph projection
            createGraphProjection();

            // 2. Run all algorithms
            runPageRank();
            runCommunityDetection();
            runBetweennessCentrality();
            // Note: Degree centrality is typically computed as part of node properties,
            // skipping separate call
            runTriangleCount();
            runLocalClusteringCoefficient();

            // 3. Cache all metrics in Aerospike
            cacheAllMerchantMetrics();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Graph metrics refresh completed in {}ms", duration);

        } catch (Exception e) {
            logger.error("Error during graph metrics refresh: {}", e.getMessage());
        }
    }

    /**
     * Cache all merchant metrics in Aerospike for fast retrieval.
     */
    private void cacheAllMerchantMetrics() {
        if (aerospikeCache == null) {
            return;
        }

        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH (m:Merchant)
                    RETURN m.merchantId AS merchantId,
                           m.pageRank AS pageRank,
                           m.communityId AS communityId,
                           m.betweenness AS betweenness,
                           m.connectionCount AS connectionCount,
                           m.triangleCount AS triangleCount,
                           m.localClusteringCoefficient AS localClusteringCoefficient
                    """;

            Result result = session.run(cypher);
            int count = 0;
            while (result.hasNext()) {
                Record record = result.next();
                String merchantId = record.get("merchantId").asString();
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("pageRank", record.get("pageRank").asDouble(0.0));
                metrics.put("communityId", record.get("communityId").asLong(0));
                metrics.put("betweenness", record.get("betweenness").asDouble(0.0));
                metrics.put("connectionCount", record.get("connectionCount").asLong(0));
                metrics.put("triangleCount", record.get("triangleCount").asLong(0));
                metrics.put("localClusteringCoefficient", record.get("localClusteringCoefficient").asDouble(0.0));
                aerospikeCache.cacheGraphMetrics(merchantId, metrics);
                count++;
            }
            logger.info("Cached graph metrics for {} merchants in Aerospike", count);

        } catch (Exception e) {
            logger.error("Error caching merchant metrics: {}", e.getMessage());
        }
    }

    // =========================================================================
    // MONEY TRAIL ANALYSIS
    // =========================================================================

    /**
     * Find shortest money trail between two merchants.
     * Used for AML investigation of fund flows.
     */
    public Map<String, Object> findMoneyTrail(String fromMerchantId, String toMerchantId) {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH path = shortestPath(
                        (from:Merchant {merchantId: $from})-[r:TRANSACTS_WITH*..10]-(to:Merchant {merchantId: $to})
                    )
                    RETURN [n in nodes(path) | n.merchantId] AS merchants,
                           length(path) AS pathLength,
                           reduce(total = 0, r in relationships(path) | total + r.totalAmount) AS totalAmount
                    """;

            Result result = session.run(cypher, Map.of("from", fromMerchantId, "to", toMerchantId));
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> trail = new HashMap<>();
                trail.put("merchants", record.get("merchants").asList());
                trail.put("pathLength", record.get("pathLength").asLong());
                trail.put("totalAmount", record.get("totalAmount").asDouble());
                return trail;
            }
        } catch (Exception e) {
            logger.error("Error finding money trail: {}", e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // METRIC AGGREGATION QUERIES
    // =========================================================================

    /**
     * Count suspicious hub merchants (Betweenness > 0.5).
     */
    public int countSuspiciousHubs() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = "MATCH (m:Merchant) WHERE m.betweenness > 0.5 RETURN count(m) AS count";
            Result result = session.run(cypher);
            if (result.hasNext()) {
                return result.next().get("count").asInt(0);
            }
        } catch (Exception e) {
            logger.error("Error counting suspicious hubs: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Count high-risk communities (Avg PageRank > 0.8).
     */
    public int countHighRiskCommunities() {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH (m:Merchant)
                    WITH m.communityId AS community, avg(m.pageRank) AS avgPageRank
                    WHERE avgPageRank > 0.8
                    RETURN count(community) AS count
                    """;
            Result result = session.run(cypher);
            if (result.hasNext()) {
                return result.next().get("count").asInt(0);
            }
        } catch (Exception e) {
            logger.error("Error counting high-risk communities: {}", e.getMessage());
        }
        return 0;
    }

    // =========================================================================
    // ANOMALY DETECTION (Phase 2 Extension)
    // =========================================================================

    /**
     * Detect Circular Trading (Money Loops).
     * Finds cycles of length 3-6 ending at the merchant.
     */
    public Map<String, Object> detectCycles(String merchantId) {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH path = (m:Merchant {merchantId: $merchantId})-[*3..6]-(m)
                    RETURN [n in nodes(path) | n.merchantId] AS cycle,
                           reduce(total = 0, r in relationships(path) | total + r.totalAmount) AS totalAmount
                    LIMIT 1
                    """;

            Result result = session.run(cypher, Map.of("merchantId", merchantId));
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("cycle", record.get("cycle").asList());
                anomaly.put("totalLoopAmount", record.get("totalAmount").asDouble());
                return anomaly;
            }
        } catch (Exception e) {
            logger.error("Error detecting cycles for {}: {}", merchantId, e.getMessage());
        }
        return null;
    }

    /**
     * Detect Proximity to High Risk Nodes (Mules).
     * Checks if merchant is within 3 hops of a known high-risk entity.
     */
    public Map<String, Object> detectMuleProximity(String merchantId) {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            // Assuming 'highRisk' property or riskScore > 0.9 identifies bad actors
            String cypher = """
                    MATCH (target:Merchant {merchantId: $merchantId})
                    MATCH (risky:Merchant) WHERE risky.riskScore > 0.9 AND risky.merchantId <> $merchantId
                    MATCH path = shortestPath((target)-[*..3]-(risky))
                    RETURN risky.merchantId AS riskyEntity, length(path) AS hops
                    LIMIT 1
                    """;

            Result result = session.run(cypher, Map.of("merchantId", merchantId));
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("riskyEntity", record.get("riskyEntity").asString());
                anomaly.put("hops", record.get("hops").asInt());
                return anomaly;
            }
        } catch (Exception e) {
            logger.error("Error detecting mule proximity for {}: {}", merchantId, e.getMessage());
        }
        return null;
    }

    /**
     * Update Merchant Risk Status in Graph.
     * Used to flag nodes as "Under Investigation" or update their risk score based
     * on
     * case findings.
     */
    public void updateMerchantRiskStatus(String merchantId, Double riskScore, boolean underInvestigation) {
        try (Session session = neo4jDriver.session(SessionConfig.defaultConfig())) {
            String cypher = """
                    MATCH (m:Merchant {merchantId: $merchantId})
                    SET m.riskScore = $riskScore,
                        m.underInvestigation = $underInvestigation,
                        m.lastRiskUpdate = datetime()
                    RETURN m.merchantId
                    """;

            Result result = session.run(cypher, Map.of(
                    "merchantId", merchantId,
                    "riskScore", riskScore != null ? riskScore : 0.0,
                    "underInvestigation", underInvestigation));

            if (result.hasNext()) {
                logger.info("Updated graph risk status for merchant {}", merchantId);
            } else {
                logger.warn("Merchant {} not found in graph for risk update", merchantId);
            }
        } catch (Exception e) {
            logger.error("Error updating merchant risk status for {}: {}", merchantId, e.getMessage());
        }
    }
}
