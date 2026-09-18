package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Neo4j Node representing a Transaction in the graph.
 * Central node for linking merchants, accounts, and devices.
 */
@Node("Transaction")
public class TransactionNode {

    @Id
    private String txnId;

    @Property("amount")
    private BigDecimal amount;

    @Property("currency")
    private String currency;

    @Property("channel")
    private String channel; // POS, ONLINE, ATM, MOBILE

    @Property("txnType")
    private String txnType; // PURCHASE, TRANSFER, REFUND

    @Property("status")
    private String status;

    @Property("timestamp")
    private LocalDateTime timestamp;

    @Property("riskScore")
    private Double riskScore;

    @Property("decision")
    private String decision; // ALLOW, HOLD, BLOCK

    // Relationships
    @Relationship(type = "FROM_MERCHANT", direction = Relationship.Direction.OUTGOING)
    private MerchantNode fromMerchant;

    @Relationship(type = "TO_MERCHANT", direction = Relationship.Direction.OUTGOING)
    private MerchantNode toMerchant;

    @Relationship(type = "FROM_ACCOUNT", direction = Relationship.Direction.OUTGOING)
    private AccountNode fromAccount;

    @Relationship(type = "TO_ACCOUNT", direction = Relationship.Direction.OUTGOING)
    private AccountNode toAccount;

    @Relationship(type = "USED_DEVICE", direction = Relationship.Direction.OUTGOING)
    private DeviceNode device;

    public TransactionNode() {
    }

    public TransactionNode(String txnId, BigDecimal amount, String currency, String channel) {
        this.txnId = txnId;
        this.amount = amount;
        this.currency = currency;
        this.channel = channel;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public MerchantNode getFromMerchant() {
        return fromMerchant;
    }

    public void setFromMerchant(MerchantNode fromMerchant) {
        this.fromMerchant = fromMerchant;
    }

    public MerchantNode getToMerchant() {
        return toMerchant;
    }

    public void setToMerchant(MerchantNode toMerchant) {
        this.toMerchant = toMerchant;
    }

    public AccountNode getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(AccountNode fromAccount) {
        this.fromAccount = fromAccount;
    }

    public AccountNode getToAccount() {
        return toAccount;
    }

    public void setToAccount(AccountNode toAccount) {
        this.toAccount = toAccount;
    }

    public DeviceNode getDevice() {
        return device;
    }

    public void setDevice(DeviceNode device) {
        this.device = device;
    }
}
