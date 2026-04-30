package com.posgateway.aml.service.analytics;



import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// @RequiredArgsConstructor removed
@Service
public class CorporateStructureService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CorporateStructureService.class);

    private final BeneficialOwnerRepository beneficialOwnerRepository;
    private final MerchantRepository merchantRepository;

    public CorporateStructureService(BeneficialOwnerRepository beneficialOwnerRepository, MerchantRepository merchantRepository) {
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.merchantRepository = merchantRepository;
    }


    @Transactional(readOnly = true)
    public CorporateGraph buildCorporateGraph(Long merchantId) {
        log.info("Building Corporate Graph for Merchant ID: {}", merchantId);

        Merchant rootMerchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        CorporateGraph graph = new CorporateGraph();
        graph.setRootMerchantName(rootMerchant.getLegalName());
        graph.setRootMerchantId(merchantId);

        // 1. Get Owners of Root
        List<BeneficialOwner> rootOwners = beneficialOwnerRepository.findByMerchant_MerchantId(merchantId);

        for (BeneficialOwner owner : rootOwners) {
            GraphNode ownerNode = new GraphNode(
                    "OWNER-" + owner.getOwnerId(),
                    owner.getFullName(),
                    "UBO",
                    "Passport: " + (owner.getPassportNumber() != null
                            ? "***" + owner.getPassportNumber().substring(owner.getPassportNumber().length() - 4)
                            : "N/A"),
                    null,
                    false);
            graph.getNodes().add(ownerNode);
            graph.getEdges().add(new GraphEdge("OWNS", ownerNode.getId(), "MERCHANT-" + merchantId));

            // 2. Find Related Companies (Merchants shared by this Owner)
            // Query by Passport/National ID
            List<BeneficialOwner> otherAppearances = new ArrayList<>();
            if (owner.getPassportNumber() != null) {
                otherAppearances.addAll(beneficialOwnerRepository.findByPassportNumber(owner.getPassportNumber()));
            } else if (owner.getNationalId() != null) {
                otherAppearances.addAll(beneficialOwnerRepository.findByNationalId(owner.getNationalId()));
            }

            for (BeneficialOwner other : otherAppearances) {
                if (!other.getMerchant().getMerchantId().equals(merchantId)) {
                    Merchant related = other.getMerchant();

                    GraphNode relatedMerchantNode = new GraphNode(
                            "MERCHANT-" + related.getMerchantId(),
                            related.getLegalName(),
                            "MERCHANT",
                            null,
                            related.getStatus(),
                            false);

                    // Add node if not exists (using Set or logic)
                    if (graph.getNodes().stream().noneMatch(n -> n.getId().equals(relatedMerchantNode.getId()))) {
                        graph.getNodes().add(relatedMerchantNode);
                    }

                    graph.getEdges().add(new GraphEdge("OWNS", ownerNode.getId(), relatedMerchantNode.getId()));
                }
            }
        }

        // Add Root Node last to ensure it exists
        graph.getNodes().add(new GraphNode(
                "MERCHANT-" + merchantId,
                rootMerchant.getLegalName(),
                "MERCHANT",
                null,
                rootMerchant.getStatus(),
                true));

        return graph;
    }

    public static class CorporateGraph {
        private Long rootMerchantId;
        private String rootMerchantName;
        private List<GraphNode> nodes = new ArrayList<>();
        private List<GraphEdge> edges = new ArrayList<>();

        public CorporateGraph() {
        }

        public Long getRootMerchantId() {
            return rootMerchantId;
        }

        public void setRootMerchantId(Long rootMerchantId) {
            this.rootMerchantId = rootMerchantId;
        }

        public String getRootMerchantName() {
            return rootMerchantName;
        }

        public void setRootMerchantName(String rootMerchantName) {
            this.rootMerchantName = rootMerchantName;
        }

        public List<GraphNode> getNodes() {
            return nodes;
        }

        public void setNodes(List<GraphNode> nodes) {
            this.nodes = nodes;
        }

        public List<GraphEdge> getEdges() {
            return edges;
        }

        public void setEdges(List<GraphEdge> edges) {
            this.edges = edges;
        }
    }

    public static class GraphNode {
        private String id;
        private String label;
        private String type; // MERCHANT, UBO
        private String details;
        private String status;
        private boolean isRoot;

        public GraphNode() {
        }

        public GraphNode(String id, String label, String type, String details, String status, boolean isRoot) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.details = details;
            this.status = status;
            this.isRoot = isRoot;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isRoot() {
            return isRoot;
        }

        public void setRoot(boolean root) {
            isRoot = root;
        }
    }

    public static class GraphEdge {
        private String relationship;
        private String source;
        private String target;

        public GraphEdge() {
        }

        public GraphEdge(String relationship, String source, String target) {
            this.relationship = relationship;
            this.source = source;
            this.target = target;
        }

        public String getRelationship() {
            return relationship;
        }

        public void setRelationship(String relationship) {
            this.relationship = relationship;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}
