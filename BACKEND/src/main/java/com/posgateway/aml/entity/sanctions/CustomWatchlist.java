package com.posgateway.aml.entity.sanctions;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_watchlists")
public class CustomWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watchlist_name", nullable = false, unique = true, length = 100)
    private String watchlistName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "list_type", nullable = false, length = 50)
    private String listType; // INTERNAL, EXTERNAL

    @Column(name = "status", nullable = false, length = 50)
    private String status; // ACTIVE, INACTIVE

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWatchlistName() { return watchlistName; }
    public void setWatchlistName(String watchlistName) { this.watchlistName = watchlistName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getListType() { return listType; }
    public void setListType(String listType) { this.listType = listType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
