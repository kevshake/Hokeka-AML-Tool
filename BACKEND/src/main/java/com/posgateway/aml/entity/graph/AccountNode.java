package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Neo4j Node representing an Account in the transaction graph.
 * Tracks money flow between accounts for AML analysis.
 */
@Node("Account")
public class AccountNode {

    @Id
    private String accountId;

    @Property("accountType")
    private String accountType; // BANK, WALLET, CARD

    @Property("currency")
    private String currency;

    @Property("country")
    private String country;

    @Property("createdAt")
    private LocalDateTime createdAt;

    // Graph analytics properties
    @Property("inDegree")
    private Long inDegree;

    @Property("outDegree")
    private Long outDegree;

    @Property("pageRank")
    private Double pageRank;

    // Relationships
    @Relationship(type = "SENDS_TO", direction = Relationship.Direction.OUTGOING)
    private Set<AccountTransfer> sendsTo = new HashSet<>();

    @Relationship(type = "USED_BY", direction = Relationship.Direction.INCOMING)
    private Set<DeviceNode> usedByDevices = new HashSet<>();

    public AccountNode() {
    }

    public AccountNode(String accountId, String accountType, String currency, String country) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.currency = currency;
        this.country = country;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getInDegree() {
        return inDegree;
    }

    public void setInDegree(Long inDegree) {
        this.inDegree = inDegree;
    }

    public Long getOutDegree() {
        return outDegree;
    }

    public void setOutDegree(Long outDegree) {
        this.outDegree = outDegree;
    }

    public Double getPageRank() {
        return pageRank;
    }

    public void setPageRank(Double pageRank) {
        this.pageRank = pageRank;
    }

    public Set<AccountTransfer> getSendsTo() {
        return sendsTo;
    }

    public void setSendsTo(Set<AccountTransfer> sendsTo) {
        this.sendsTo = sendsTo;
    }

    public Set<DeviceNode> getUsedByDevices() {
        return usedByDevices;
    }

    public void setUsedByDevices(Set<DeviceNode> usedByDevices) {
        this.usedByDevices = usedByDevices;
    }
}
