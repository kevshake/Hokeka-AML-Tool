package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;

/**
 * Relationship properties for TRANSACTS_WITH between Merchants.
 * Stores transaction statistics for the relationship.
 */
@RelationshipProperties
public class MerchantRelationship {

    @RelationshipId
    private Long id;

    @TargetNode
    private MerchantNode target;

    @Property("totalAmount")
    private Double totalAmount;

    @Property("txnCount")
    private Long txnCount;

    @Property("avgAmount")
    private Double avgAmount;

    @Property("firstTxnAt")
    private LocalDateTime firstTxnAt;

    @Property("lastTxnAt")
    private LocalDateTime lastTxnAt;

    public MerchantRelationship() {
    }

    public MerchantRelationship(MerchantNode target, Double totalAmount, Long txnCount) {
        this.target = target;
        this.totalAmount = totalAmount;
        this.txnCount = txnCount;
        this.avgAmount = txnCount > 0 ? totalAmount / txnCount : 0.0;
        this.lastTxnAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MerchantNode getTarget() {
        return target;
    }

    public void setTarget(MerchantNode target) {
        this.target = target;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Long getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(Long txnCount) {
        this.txnCount = txnCount;
    }

    public Double getAvgAmount() {
        return avgAmount;
    }

    public void setAvgAmount(Double avgAmount) {
        this.avgAmount = avgAmount;
    }

    public LocalDateTime getFirstTxnAt() {
        return firstTxnAt;
    }

    public void setFirstTxnAt(LocalDateTime firstTxnAt) {
        this.firstTxnAt = firstTxnAt;
    }

    public LocalDateTime getLastTxnAt() {
        return lastTxnAt;
    }

    public void setLastTxnAt(LocalDateTime lastTxnAt) {
        this.lastTxnAt = lastTxnAt;
    }

    public void addTransaction(Double amount) {
        this.txnCount++;
        this.totalAmount += amount;
        this.avgAmount = this.totalAmount / this.txnCount;
        this.lastTxnAt = LocalDateTime.now();
    }
}
