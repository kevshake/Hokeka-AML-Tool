package com.posgateway.aml.entity.features;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Customer Features Entity
 * 
 * Purpose: Pre-computed customer behavioral features for O(1) rule evaluation
 * This replaces expensive real-time aggregations (COUNT, SUM queries on transactions table)
 * 
 * Design Pattern:
 * - Features are computed asynchronously from Kafka events
 * - Stored in PostgreSQL for persistence
 * - Cached in Redis for sub-millisecond access
 * - Updated via event-driven architecture (features.updates topic)
 * 
 * Performance Impact:
 * - Rule evaluation: O(1) lookup vs O(n) aggregation query
 * - Target latency: < 5ms for feature retrieval
 */
@Entity
@Table(name = "customer_features", 
       indexes = {
           @Index(name = "idx_cf_customer", columnList = "customer_id", unique = true),
           @Index(name = "idx_cf_updated", columnList = "updated_at"),
           @Index(name = "idx_cf_risk", columnList = "risk_score")
       })
public class CustomerFeatures {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Core identifier
    @Column(name = "customer_id", nullable = false, unique = true, length = 64)
    private String customerId;

    // ========== Velocity Features (Rolling Windows) ==========
    
    @Column(name = "tx_count_1h")
    private Integer txCount1h = 0;
    
    @Column(name = "tx_count_24h")
    private Integer txCount24h = 0;
    
    @Column(name = "tx_count_7d")
    private Integer txCount7d = 0;
    
    @Column(name = "tx_count_30d")
    private Integer txCount30d = 0;

    @Column(name = "tx_volume_1h", columnDefinition = "BIGINT")
    private Long txVolume1h = 0L;
    
    @Column(name = "tx_volume_24h", columnDefinition = "BIGINT")
    private Long txVolume24h = 0L;
    
    @Column(name = "tx_volume_7d", columnDefinition = "BIGINT")
    private Long txVolume7d = 0L;
    
    @Column(name = "tx_volume_30d", columnDefinition = "BIGINT")
    private Long txVolume30d = 0L;

    // ========== Behavioral Baselines ==========
    
    @Column(name = "avg_tx_amount", columnDefinition = "DECIMAL(19,2)")
    private Double avgTxAmount = 0.0;
    
    @Column(name = "max_tx_amount", columnDefinition = "DECIMAL(19,2)")
    private Double maxTxAmount = 0.0;
    
    @Column(name = "min_tx_amount", columnDefinition = "DECIMAL(19,2)")
    private Double minTxAmount = 0.0;
    
    @Column(name = "usual_hours_start")
    private Integer usualHoursStart = 0; // 0-23
    
    @Column(name = "usual_hours_end")
    private Integer usualHoursEnd = 23; // 0-23
    
    @Column(name = "home_country", length = 2)
    private String homeCountry;
    
    @Column(name = "usual_merchant_ids", columnDefinition = "TEXT")
    private String usualMerchantIds; // JSON array of top merchants

    // ========== Risk Indicators ==========
    
    @Column(name = "risk_score")
    private Integer riskScore = 0; // 0-100
    
    @Column(name = "countries_last_24h", columnDefinition = "TEXT")
    private String countriesLast24h; // JSON array ["KE", "UG"]
    
    @Column(name = "countries_last_7d", columnDefinition = "TEXT")
    private String countriesLast7d; // JSON array
    
    @Column(name = "unique_countries_24h")
    private Integer uniqueCountries24h = 0;
    
    @Column(name = "unique_countries_7d")
    private Integer uniqueCountries7d = 0;
    
    @Column(name = "channels_used_24h", columnDefinition = "TEXT")
    private String channelsUsed24h; // JSON array ["POS", "ATM", "API"]

    // ========== Device & Security ==========
    
    @Column(name = "unique_devices_24h")
    private Integer uniqueDevices24h = 0;
    
    @Column(name = "unique_ips_24h")
    private Integer uniqueIps24h = 0;
    
    @Column(name = "failed_tx_count_24h")
    private Integer failedTxCount24h = 0;

    // ========== Temporal Features ==========
    
    @Column(name = "last_tx_timestamp")
    private LocalDateTime lastTxTimestamp;
    
    @Column(name = "last_tx_amount", columnDefinition = "DECIMAL(19,2)")
    private Double lastTxAmount = 0.0;
    
    @Column(name = "last_tx_country", length = 2)
    private String lastTxCountry;
    
    @Column(name = "time_since_last_tx_minutes")
    private Integer timeSinceLastTxMinutes = 0;

    // ========== Metadata ==========
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version = 0L; // Optimistic locking for concurrent updates

    // ========== Computed Helper Methods ==========
    
    /**
     * Check if transaction amount exceeds customer's typical pattern
     */
    public boolean isAmountSuspicious(Double amount) {
        if (avgTxAmount == null || avgTxAmount == 0) return false;
        return amount > (avgTxAmount * 5); // 5x average is suspicious
    }
    
    /**
     * Check if velocity exceeds thresholds
     */
    public boolean isVelocityExceeded(int threshold1h, int threshold24h) {
        return (txCount1h != null && txCount1h > threshold1h) ||
               (txCount24h != null && txCount24h > threshold24h);
    }
    
    /**
     * Check for geographic anomaly
     */
    public boolean isGeographicAnomaly(String currentCountry) {
        if (homeCountry == null || currentCountry == null) return false;
        return !homeCountry.equals(currentCountry);
    }

    // ========== Getters & Setters ==========
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public Integer getTxCount1h() { return txCount1h; }
    public void setTxCount1h(Integer txCount1h) { this.txCount1h = txCount1h; }
    
    public Integer getTxCount24h() { return txCount24h; }
    public void setTxCount24h(Integer txCount24h) { this.txCount24h = txCount24h; }
    
    public Integer getTxCount7d() { return txCount7d; }
    public void setTxCount7d(Integer txCount7d) { this.txCount7d = txCount7d; }
    
    public Integer getTxCount30d() { return txCount30d; }
    public void setTxCount30d(Integer txCount30d) { this.txCount30d = txCount30d; }
    
    public Long getTxVolume1h() { return txVolume1h; }
    public void setTxVolume1h(Long txVolume1h) { this.txVolume1h = txVolume1h; }
    
    public Long getTxVolume24h() { return txVolume24h; }
    public void setTxVolume24h(Long txVolume24h) { this.txVolume24h = txVolume24h; }
    
    public Long getTxVolume7d() { return txVolume7d; }
    public void setTxVolume7d(Long txVolume7d) { this.txVolume7d = txVolume7d; }
    
    public Long getTxVolume30d() { return txVolume30d; }
    public void setTxVolume30d(Long txVolume30d) { this.txVolume30d = txVolume30d; }
    
    public Double getAvgTxAmount() { return avgTxAmount; }
    public void setAvgTxAmount(Double avgTxAmount) { this.avgTxAmount = avgTxAmount; }
    
    public Double getMaxTxAmount() { return maxTxAmount; }
    public void setMaxTxAmount(Double maxTxAmount) { this.maxTxAmount = maxTxAmount; }
    
    public Double getMinTxAmount() { return minTxAmount; }
    public void setMinTxAmount(Double minTxAmount) { this.minTxAmount = minTxAmount; }
    
    public Integer getUsualHoursStart() { return usualHoursStart; }
    public void setUsualHoursStart(Integer usualHoursStart) { this.usualHoursStart = usualHoursStart; }
    
    public Integer getUsualHoursEnd() { return usualHoursEnd; }
    public void setUsualHoursEnd(Integer usualHoursEnd) { this.usualHoursEnd = usualHoursEnd; }
    
    public String getHomeCountry() { return homeCountry; }
    public void setHomeCountry(String homeCountry) { this.homeCountry = homeCountry; }
    
    public String getUsualMerchantIds() { return usualMerchantIds; }
    public void setUsualMerchantIds(String usualMerchantIds) { this.usualMerchantIds = usualMerchantIds; }
    
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    
    public String getCountriesLast24h() { return countriesLast24h; }
    public void setCountriesLast24h(String countriesLast24h) { this.countriesLast24h = countriesLast24h; }
    
    public String getCountriesLast7d() { return countriesLast7d; }
    public void setCountriesLast7d(String countriesLast7d) { this.countriesLast7d = countriesLast7d; }
    
    public Integer getUniqueCountries24h() { return uniqueCountries24h; }
    public void setUniqueCountries24h(Integer uniqueCountries24h) { this.uniqueCountries24h = uniqueCountries24h; }
    
    public Integer getUniqueCountries7d() { return uniqueCountries7d; }
    public void setUniqueCountries7d(Integer uniqueCountries7d) { this.uniqueCountries7d = uniqueCountries7d; }
    
    public String getChannelsUsed24h() { return channelsUsed24h; }
    public void setChannelsUsed24h(String channelsUsed24h) { this.channelsUsed24h = channelsUsed24h; }
    
    public Integer getUniqueDevices24h() { return uniqueDevices24h; }
    public void setUniqueDevices24h(Integer uniqueDevices24h) { this.uniqueDevices24h = uniqueDevices24h; }
    
    public Integer getUniqueIps24h() { return uniqueIps24h; }
    public void setUniqueIps24h(Integer uniqueIps24h) { this.uniqueIps24h = uniqueIps24h; }
    
    public Integer getFailedTxCount24h() { return failedTxCount24h; }
    public void setFailedTxCount24h(Integer failedTxCount24h) { this.failedTxCount24h = failedTxCount24h; }
    
    public LocalDateTime getLastTxTimestamp() { return lastTxTimestamp; }
    public void setLastTxTimestamp(LocalDateTime lastTxTimestamp) { this.lastTxTimestamp = lastTxTimestamp; }
    
    public Double getLastTxAmount() { return lastTxAmount; }
    public void setLastTxAmount(Double lastTxAmount) { this.lastTxAmount = lastTxAmount; }
    
    public String getLastTxCountry() { return lastTxCountry; }
    public void setLastTxCountry(String lastTxCountry) { this.lastTxCountry = lastTxCountry; }
    
    public Integer getTimeSinceLastTxMinutes() { return timeSinceLastTxMinutes; }
    public void setTimeSinceLastTxMinutes(Integer timeSinceLastTxMinutes) { this.timeSinceLastTxMinutes = timeSinceLastTxMinutes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
