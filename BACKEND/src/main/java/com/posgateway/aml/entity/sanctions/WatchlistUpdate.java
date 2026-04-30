package com.posgateway.aml.entity.sanctions;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Watchlist Update Tracking Entity
 * Tracks watchlist update frequencies and dates
 */
@Entity
@Table(name = "watchlist_updates", indexes = {
    @Index(name = "idx_watchlist_updates_list", columnList = "list_name,list_type"),
    @Index(name = "idx_watchlist_updates_date", columnList = "update_date DESC"),
    @Index(name = "idx_watchlist_updates_status", columnList = "status")
})
public class WatchlistUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_name", nullable = false, length = 100)
    private String listName; // OFAC, UN, EU, etc.

    @Column(name = "list_type", nullable = false, length = 50)
    private String listType; // SANCTIONS, PEP, ADVERSE_MEDIA

    @Column(name = "update_date", nullable = false)
    private LocalDate updateDate;

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getListName() {
        return listName;
    }

    public void setListName(String listName) {
        this.listName = listName;
    }

    public String getListType() {
        return listType;
    }

    public void setListType(String listType) {
        this.listType = listType;
    }

    public LocalDate getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDate updateDate) {
        this.updateDate = updateDate;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

