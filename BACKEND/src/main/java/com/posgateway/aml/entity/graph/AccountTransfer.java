package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;

/**
 * Relationship properties for SENDS_TO between Accounts.
 * Tracks money transfer statistics for AML analysis.
 */
@RelationshipProperties
public class AccountTransfer {

    @RelationshipId
    private Long id;

    @TargetNode
    private AccountNode targetAccount;

    @Property("totalAmount")
    private Double totalAmount;

    @Property("transferCount")
    private Long transferCount;

    @Property("avgAmount")
    private Double avgAmount;

    @Property("maxAmount")
    private Double maxAmount;

    @Property("currency")
    private String currency;

    @Property("firstTransferAt")
    private LocalDateTime firstTransferAt;

    @Property("lastTransferAt")
    private LocalDateTime lastTransferAt;

    public AccountTransfer() {
    }

    public AccountTransfer(AccountNode targetAccount, Double amount, String currency) {
        this.targetAccount = targetAccount;
        this.totalAmount = amount;
        this.transferCount = 1L;
        this.avgAmount = amount;
        this.maxAmount = amount;
        this.currency = currency;
        this.firstTransferAt = LocalDateTime.now();
        this.lastTransferAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AccountNode getTargetAccount() {
        return targetAccount;
    }

    public void setTargetAccount(AccountNode targetAccount) {
        this.targetAccount = targetAccount;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Long getTransferCount() {
        return transferCount;
    }

    public void setTransferCount(Long transferCount) {
        this.transferCount = transferCount;
    }

    public Double getAvgAmount() {
        return avgAmount;
    }

    public void setAvgAmount(Double avgAmount) {
        this.avgAmount = avgAmount;
    }

    public Double getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(Double maxAmount) {
        this.maxAmount = maxAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getFirstTransferAt() {
        return firstTransferAt;
    }

    public void setFirstTransferAt(LocalDateTime firstTransferAt) {
        this.firstTransferAt = firstTransferAt;
    }

    public LocalDateTime getLastTransferAt() {
        return lastTransferAt;
    }

    public void setLastTransferAt(LocalDateTime lastTransferAt) {
        this.lastTransferAt = lastTransferAt;
    }

    public void addTransfer(Double amount) {
        this.transferCount++;
        this.totalAmount += amount;
        this.avgAmount = this.totalAmount / this.transferCount;
        if (amount > this.maxAmount)
            this.maxAmount = amount;
        this.lastTransferAt = LocalDateTime.now();
    }
}
