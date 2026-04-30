package com.posgateway.aml.entity.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Neo4j Node representing a Device in the transaction graph.
 * Used for device fingerprinting and fraud network detection.
 */
@Node("Device")
public class DeviceNode {

    @Id
    private String deviceId;

    @Property("fingerprint")
    private String fingerprint;

    @Property("ipAddress")
    private String ipAddress;

    @Property("userAgent")
    private String userAgent;

    @Property("deviceType")
    private String deviceType; // MOBILE, DESKTOP, TABLET, POS_TERMINAL

    @Property("os")
    private String os;

    @Property("country")
    private String country;

    @Property("firstSeenAt")
    private LocalDateTime firstSeenAt;

    @Property("lastSeenAt")
    private LocalDateTime lastSeenAt;

    @Property("riskScore")
    private Double riskScore;

    // Relationships
    @Relationship(type = "USED_BY", direction = Relationship.Direction.OUTGOING)
    private Set<AccountNode> usedByAccounts = new HashSet<>();

    public DeviceNode() {
    }

    public DeviceNode(String deviceId, String fingerprint, String ipAddress) {
        this.deviceId = deviceId;
        this.fingerprint = fingerprint;
        this.ipAddress = ipAddress;
        this.firstSeenAt = LocalDateTime.now();
        this.lastSeenAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public Set<AccountNode> getUsedByAccounts() {
        return usedByAccounts;
    }

    public void setUsedByAccounts(Set<AccountNode> usedByAccounts) {
        this.usedByAccounts = usedByAccounts;
    }

    public void updateLastSeen() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
