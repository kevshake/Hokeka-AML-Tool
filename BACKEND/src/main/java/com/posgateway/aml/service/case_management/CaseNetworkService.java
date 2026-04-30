package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
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

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(CaseNetworkService.class);

    private final ComplianceCaseRepository caseRepository;
    private final CaseTransactionRepository caseTransactionRepository;
    @SuppressWarnings("unused")
    private final TransactionRepository transactionRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    @SuppressWarnings("unused")
    private final UserRepository userRepository;

    @Autowired
    public CaseNetworkService(ComplianceCaseRepository caseRepository,
                              CaseTransactionRepository caseTransactionRepository,
                              TransactionRepository transactionRepository,
                              SuspiciousActivityReportRepository sarRepository,
                              UserRepository userRepository) {
        this.caseRepository = caseRepository;
        this.caseTransactionRepository = caseTransactionRepository;
        this.transactionRepository = transactionRepository;
        this.sarRepository = sarRepository;
        this.userRepository = userRepository;
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

        // Find related transactions
        List<CaseTransaction> caseTransactions = caseTransactionRepository.findByComplianceCase(rootCase);
        caseTransactions.forEach(ct -> {
            if (ct.getTransaction() != null) {
                graph.addNode(createTransactionNode(ct.getTransaction()));
                graph.addEdge(createCaseTransactionEdge(rootCase, ct.getTransaction()));
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

        // Find related SARs
        List<SuspiciousActivityReport> sars = sarRepository.findAll().stream()
                .filter(sar -> sar.getComplianceCase() != null && sar.getComplianceCase().getId().equals(caseId))
                .collect(Collectors.toList());
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

        return graph;
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

        // Find entities from related transactions
        List<CaseTransaction> transactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        transactions.forEach(ct -> {
            TransactionEntity tx = ct.getTransaction();
            if (tx.getMerchantId() != null) {
                entities.add(EntityNode.builder()
                        .id(tx.getMerchantId())
                        .type("MERCHANT")
                        .label("Merchant: " + tx.getMerchantId())
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

    /**
     * Create transaction node
     */
    private NetworkNode createTransactionNode(TransactionEntity transaction) {
        return NetworkNode.builder()
                .id("TXN_" + transaction.getTxnId())
                .type("TRANSACTION")
                .label("TXN: " + transaction.getTxnId())
                .data(transaction)
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
    private NetworkEdge createCaseTransactionEdge(ComplianceCase complianceCase, TransactionEntity transaction) {
        return NetworkEdge.builder()
                .from("CASE_" + complianceCase.getId())
                .to("TXN_" + transaction.getTxnId())
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

