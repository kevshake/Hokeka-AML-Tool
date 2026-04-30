package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Neo4j Node representing a Merchant in the transaction graph.
 * Used for graph-based AML analytics and network analysis.
 */
@Node("Merchant")
public class MerchantNode {

    @Id
    private String merchantId;

    @Property("legalName")
    private String legalName;

    @Property("tradingName")
    private String tradingName;

    @Property("mcc")
    private String mcc;

    @Property("country")
    private String country;

    @Property("riskLevel")
    private String riskLevel;

    @Property("createdAt")
    private LocalDateTime createdAt;

    // Graph analytics properties (computed by Neo4j GDS)
    @Property("pageRank")
    private Double pageRank;

    @Property("communityId")
    private Long communityId;

    @Property("betweenness")
    private Double betweenness;

    // Relationships
    @Relationship(type = "TRANSACTS_WITH", direction = Relationship.Direction.OUTGOING)
    private Set<MerchantRelationship> transactsWith = new HashSet<>();

    @Relationship(type = "OWNS_ACCOUNT", direction = Relationship.Direction.OUTGOING)
    private Set<AccountNode> ownedAccounts = new HashSet<>();

    public MerchantNode() {
    }

    public MerchantNode(String merchantId, String legalName, String mcc, String country) {
        this.merchantId = merchantId;
        this.legalName = legalName;
        this.mcc = mcc;
        this.country = country;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradingName() {
        return tradingName;
    }

    public void setTradingName(String tradingName) {
        this.tradingName = tradingName;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getPageRank() {
        return pageRank;
    }

    public void setPageRank(Double pageRank) {
        this.pageRank = pageRank;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public void setCommunityId(Long communityId) {
        this.communityId = communityId;
    }

    public Double getBetweenness() {
        return betweenness;
    }

    public void setBetweenness(Double betweenness) {
        this.betweenness = betweenness;
    }

    public Set<MerchantRelationship> getTransactsWith() {
        return transactsWith;
    }

    public void setTransactsWith(Set<MerchantRelationship> transactsWith) {
        this.transactsWith = transactsWith;
    }

    public Set<AccountNode> getOwnedAccounts() {
        return ownedAccounts;
    }

    public void setOwnedAccounts(Set<AccountNode> ownedAccounts) {
        this.ownedAccounts = ownedAccounts;
    }

    public void addTransactionWith(MerchantNode target, Double amount, Long txnCount) {
        this.transactsWith.add(new MerchantRelationship(target, amount, txnCount));
    }
}
