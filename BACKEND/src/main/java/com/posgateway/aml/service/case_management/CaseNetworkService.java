package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Case Network Service
 * Builds network graphs showing relationships between cases, transactions, and entities
 */
@Service
public class CaseNetworkService {

    private static final Logger logger = LoggerFactory.getLogger(CaseNetworkService.class);

    private final ComplianceCaseRepository caseRepository;
    private final CaseTransactionRepository caseTransactionRepository;
    private final TransactionReferenceResolver transactionReferences;
    private final SuspiciousActivityReportRepository sarRepository;
    @SuppressWarnings("unused")
    private final UserRepository userRepository;
    private final com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService;

    @Autowired
    public CaseNetworkService(ComplianceCaseRepository caseRepository,
                              CaseTransactionRepository caseTransactionRepository,
                              TransactionReferenceResolver transactionReferences,
                              SuspiciousActivityReportRepository sarRepository,
                              UserRepository userRepository,
                              @Autowired(required = false) com.posgateway.aml.service.graph.Neo4jGdsService neo4jGdsService) {
        this.caseRepository = caseRepository;
        this.caseTransactionRepository = caseTransactionRepository;
        this.transactionReferences = transactionReferences;
        this.sarRepository = sarRepository;
        this.userRepository = userRepository;
        this.neo4jGdsService = neo4jGdsService;
    }

    /**
     * Build network graph for a case
     */
    public NetworkGraphDTO buildNetworkGraph(Long caseId, int depth) {
        ComplianceCase rootCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        NetworkGraphDTO graph = new NetworkGraphDTO();

        // Add root case as node
        graph.addNode(createCaseNode(rootCase));

        // Find related cases
        Set<ComplianceCase> relatedCases = findRelatedCases(rootCase, depth);
        relatedCases.forEach(relatedCase -> {
            graph.addNode(createCaseNode(relatedCase));
            graph.addEdge(createCaseEdge(rootCase, relatedCase, "RELATED_CASE"));
        });

        // Find related transactions (by ID reference; details are pulled from the Edge, R6)
        List<CaseTransaction> caseTransactions = caseTransactionRepository.findByComplianceCase(rootCase);
        Map<UUID, Assessment> records = decisionRecords(rootCase, caseTransactions);
        caseTransactions.forEach(ct -> {
            TransactionReference reference = ct.getTransactionReference();
            if (!reference.isEmpty()) {
                graph.addNode(createTransactionNode(reference, recordOf(reference, records)));
                graph.addEdge(createCaseTransactionEdge(rootCase, reference));
            }
        });

        // Find related entities (merchants, customers)
        Set<EntityNode> entities = findRelatedEntities(rootCase);
        entities.forEach(entity -> {
            // Represent entity nodes as generic network nodes in the graph
            NetworkNode entityNode = NetworkNode.builder()
                    .id(entity.getId())
                    .type(entity.getType())
                    .label(entity.getLabel())
                    .data(null)
                    .build();
            graph.addNode(entityNode);
            graph.addEdge(createCaseEntityEdge(rootCase, entity));
        });

        // Find related SARs (DB-scoped to this case; was a full-table scan + per-SAR lazy load — W49-4)
        List<SuspiciousActivityReport> sars = sarRepository.findByComplianceCase_Id(caseId);
        sars.forEach(sar -> {
            graph.addNode(createSarNode(sar));
            graph.addEdge(createCaseSarEdge(rootCase, sar));
        });

        // Find assigned users
        if (rootCase.getAssignedTo() != null) {
            graph.addNode(createUserNode(rootCase.getAssignedTo()));
            graph.addEdge(createCaseUserEdge(rootCase, rootCase.getAssignedTo(), "ASSIGNED_TO"));
        }

        // Find merchant if case has merchantId
        if (rootCase.getMerchantId() != null) {
            String merchantId = "MERCHANT_" + rootCase.getMerchantId();
            NetworkNode merchantNode = NetworkNode.builder()
                    .id(merchantId)
                    .type("MERCHANT")
                    .label("Merchant: " + rootCase.getMerchantId())
                    .data(null)
                    .build();
            graph.addNode(merchantNode);
            graph.addEdge(NetworkEdge.builder()
                    .from("CASE_" + rootCase.getId())
                    .to(merchantId)
                    .type("HAS_MERCHANT")
                    .label("Merchant")
                    .build());
        }

        attachNeo4jMetrics(graph, rootCase);
        return graph;
    }

    private void attachNeo4jMetrics(NetworkGraphDTO graph, ComplianceCase rootCase) {
        if (neo4jGdsService == null || rootCase.getMerchantId() == null) {
            return;
        }
        try {
            String merchantId = String.valueOf(rootCase.getMerchantId());
            Map<String, Object> metrics = new LinkedHashMap<>();
            Map<String, Object> trail = neo4jGdsService.findMoneyTrail(merchantId, merchantId);
            if (trail != null) {
                metrics.putAll(trail);
            }
            Map<String, Object> cycles = neo4jGdsService.detectCycles(merchantId);
            if (cycles != null) {
                metrics.putAll(cycles);
            }
            Map<String, Object> mule = neo4jGdsService.detectMuleProximity(merchantId);
            if (mule != null) {
                metrics.putAll(mule);
            }
            if (!metrics.isEmpty()) {
                graph.setNeo4jMetrics(metrics);
            }
        } catch (RuntimeException e) {
            logger.warn("Neo4j GDS metrics skipped for case {}: {}", rootCase.getId(), e.getMessage());
        }
    }

    /**
     * Find related cases recursively
     */
    private Set<ComplianceCase> findRelatedCases(ComplianceCase complianceCase, int depth) {
        Set<ComplianceCase> related = new HashSet<>();
        Queue<ComplianceCase> queue = new LinkedList<>();
        queue.add(complianceCase);
        int currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < depth) {
            ComplianceCase current = queue.poll();
            if (current.getRelatedCases() != null) {
                Set<ComplianceCase> directRelated = current.getRelatedCases();
                related.addAll(directRelated);
                queue.addAll(directRelated);
            }
            currentDepth++;
        }

        return related;
    }

    /**
     * Find related entities (merchants, customers)
     */
    private Set<EntityNode> findRelatedEntities(ComplianceCase complianceCase) {
        Set<EntityNode> entities = new HashSet<>();

        // Merchants named by the decision records of the case's transactions (class-D merchant ids)
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        Map<UUID, Assessment> records = decisionRecords(complianceCase, transactions);
        transactions.forEach(ct -> {
            Assessment record = recordOf(ct.getTransactionReference(), records);
            if (record != null && record.getMerchantId() != null) {
                entities.add(EntityNode.builder()
                        .id(record.getMerchantId())
                        .type("MERCHANT")
                        .label("Merchant: " + record.getMerchantId())
                        .build());
            }
        });

        return entities;
    }

    /**
     * Create case node
     */
    private NetworkNode createCaseNode(ComplianceCase complianceCase) {
        return NetworkNode.builder()
                .id("CASE_" + complianceCase.getId())
                .type("CASE")
                .label(complianceCase.getCaseReference())
                .data(complianceCase)
                .build();
    }

    /** The case PSP's decision records named by the case's transaction links (PSP-scoped lookup). */
    private Map<UUID, Assessment> decisionRecords(ComplianceCase complianceCase, List<CaseTransaction> links) {
        return transactionReferences.decisionRecords(complianceCase.getPspId(),
                links.stream().map(CaseTransaction::getTransactionReference).toList());
    }

    /** The decision record of one reference, or null. */
    private static Assessment recordOf(TransactionReference reference, Map<UUID, Assessment> records) {
        return reference.edgeAssessmentId() == null ? null : records.get(reference.edgeAssessmentId());
    }

    /** Stable graph node id of a transaction reference: Edge id, else legacy id, else txnRef. */
    private static String transactionNodeId(TransactionReference reference) {
        if (reference.edgeAssessmentId() != null) {
            return "TXN_" + reference.edgeAssessmentId();
        }
        if (reference.legacyTransactionId() != null) {
            return "TXN_" + reference.legacyTransactionId();
        }
        return "TXN_REF_" + reference.txnRef();
    }

    /**
     * Create transaction node: the ID reference plus the decision record's decision. Transaction
     * details are not read from the cloud; {@code transactionDetails} stays null until the R6 live pull.
     */
    private NetworkNode createTransactionNode(TransactionReference reference, Assessment record) {
        Map<String, Object> data = new LinkedHashMap<>(reference.toMap());
        if (record != null && record.getDecision() != null) {
            data.put("decision", record.getDecision());
        }
        data.put("transactionDetails", null);
        return NetworkNode.builder()
                .id(transactionNodeId(reference))
                .type("TRANSACTION")
                .label("TXN: " + reference.label())
                .data(data)
                .build();
    }

    /**
     * Create case edge
     */
    private NetworkEdge createCaseEdge(ComplianceCase from, ComplianceCase to, String relationshipType) {
        return NetworkEdge.builder()
                .from("CASE_" + from.getId())
                .to("CASE_" + to.getId())
                .type(relationshipType)
                .label("Related Case")
                .build();
    }

    /**
     * Create case-transaction edge
     */
    private NetworkEdge createCaseTransactionEdge(ComplianceCase complianceCase, TransactionReference reference) {
        return NetworkEdge.builder()
                .from("CASE_" + complianceCase.getId())
                .to(transactionNodeId(reference))
                .type("HAS_TRANSACTION")
                .label("Contains Transaction")
                .build();
    }

    /**
     * Create case-entity edge
     */
    private NetworkEdge createCaseEntityEdge(ComplianceCase complianceCase, EntityNode entity) {
        return NetworkEdge.builder()
                .from("CASE_" + complianceCase.getId())
                .to(entity.getId())
                .type("RELATED_ENTITY")
                .label("Related " + entity.getType())
                .build();
    }

    /**
     * Create SAR node
     */
    private NetworkNode createSarNode(SuspiciousActivityReport sar) {
        return NetworkNode.builder()
                .id("SAR_" + sar.getId())
                .type("SAR")
                .label(sar.getSarReference())
                .data(sar)
                .build();
    }

    /**
     * Create case-SAR edge
     */
    private NetworkEdge createCaseSarEdge(ComplianceCase complianceCase, SuspiciousActivityReport sar) {
        return NetworkEdge.builder()
                .from("CASE_" + complianceCase.getId())
                .to("SAR_" + sar.getId())
                .type("HAS_SAR")
                .label("Has SAR")
                .build();
    }

    /**
     * Create user node
     */
    private NetworkNode createUserNode(User user) {
        return NetworkNode.builder()
                .id("USER_" + user.getId())
                .type("USER")
                .label(user.getUsername())
                .data(user)
                .build();
    }

    /**
     * Create case-user edge
     */
    private NetworkEdge createCaseUserEdge(ComplianceCase complianceCase, User user, String relationshipType) {
        return NetworkEdge.builder()
                .from("CASE_" + complianceCase.getId())
                .to("USER_" + user.getId())
                .type(relationshipType)
                .label(relationshipType.replace("_", " "))
                .build();
    }

    /**
     * Network Graph DTO
     */
    public static class NetworkGraphDTO {
        private List<NetworkNode> nodes = new ArrayList<>();
        private List<NetworkEdge> edges = new ArrayList<>();
        private Map<String, Object> neo4jMetrics;

        public void addNode(NetworkNode node) {
            if (nodes.stream().noneMatch(n -> n.getId().equals(node.getId()))) {
                nodes.add(node);
            }
        }

        public void addEdge(NetworkEdge edge) {
            if (edges.stream().noneMatch(e -> e.getFrom().equals(edge.getFrom()) && e.getTo().equals(edge.getTo()))) {
                edges.add(edge);
            }
        }

        public List<NetworkNode> getNodes() {
            return nodes;
        }

        public List<NetworkEdge> getEdges() {
            return edges;
        }

        public Map<String, Object> getNeo4jMetrics() {
            return neo4jMetrics;
        }

        public void setNeo4jMetrics(Map<String, Object> neo4jMetrics) {
            this.neo4jMetrics = neo4jMetrics;
        }
    }

    /**
     * Network Node
     */
    public static class NetworkNode {
        private String id;
        private String type;
        private String label;
        private Object data;

        public static NetworkNodeBuilder builder() {
            return new NetworkNodeBuilder();
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getLabel() { return label; }
        public Object getData() { return data; }

        public static class NetworkNodeBuilder {
            private String id;
            private String type;
            private String label;
            private Object data;

            public NetworkNodeBuilder id(String id) { this.id = id; return this; }
            public NetworkNodeBuilder type(String type) { this.type = type; return this; }
            public NetworkNodeBuilder label(String label) { this.label = label; return this; }
            public NetworkNodeBuilder data(Object data) { this.data = data; return this; }

            public NetworkNode build() {
                NetworkNode node = new NetworkNode();
                node.id = this.id;
                node.type = this.type;
                node.label = this.label;
                node.data = this.data;
                return node;
            }
        }
    }

    /**
     * Network Edge
     */
    public static class NetworkEdge {
        private String from;
        private String to;
        private String type;
        private String label;

        public static NetworkEdgeBuilder builder() {
            return new NetworkEdgeBuilder();
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getType() { return type; }
        public String getLabel() { return label; }

        public static class NetworkEdgeBuilder {
            private String from;
            private String to;
            private String type;
            private String label;

            public NetworkEdgeBuilder from(String from) { this.from = from; return this; }
            public NetworkEdgeBuilder to(String to) { this.to = to; return this; }
            public NetworkEdgeBuilder type(String type) { this.type = type; return this; }
            public NetworkEdgeBuilder label(String label) { this.label = label; return this; }

            public NetworkEdge build() {
                NetworkEdge edge = new NetworkEdge();
                edge.from = this.from;
                edge.to = this.to;
                edge.type = this.type;
                edge.label = this.label;
                return edge;
            }
        }
    }

    /**
     * Entity Node
     */
    public static class EntityNode {
        private String id;
        private String type;
        private String label;

        public static EntityNodeBuilder builder() {
            return new EntityNodeBuilder();
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getLabel() { return label; }

        public static class EntityNodeBuilder {
            private String id;
            private String type;
            private String label;

            public EntityNodeBuilder id(String id) { this.id = id; return this; }
            public EntityNodeBuilder type(String type) { this.type = type; return this; }
            public EntityNodeBuilder label(String label) { this.label = label; return this; }

            public EntityNode build() {
                EntityNode node = new EntityNode();
                node.id = this.id;
                node.type = this.type;
                node.label = this.label;
                return node;
            }
        }
    }
}

