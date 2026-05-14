package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * UserSettings Entity
 * Stores per-user UI preferences such as theme, notification settings, and display options.
 * One row per user; created on first explicit save (PUT /settings).
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String theme = "light";

    @Column(nullable = false)
    private Boolean notifications = true;

    @Column(name = "auto_refresh", nullable = false)
    private Boolean autoRefresh = true;

    @Column(name = "refresh_interval", nullable = false)
    private Integer refreshInterval = 30;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(name = "date_format", nullable = false)
    private String dateFormat = "YYYY-MM-DD";

    @Column(name = "items_per_page", nullable = false)
    private Integer itemsPerPage = 50;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserSettings() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Boolean getNotifications() {
        return notifications;
    }

    public void setNotifications(Boolean notifications) {
        this.notifications = notifications;
    }

    public Boolean getAutoRefresh() {
        return autoRefresh;
    }

    public void setAutoRefresh(Boolean autoRefresh) {
        this.autoRefresh = autoRefresh;
    }

    public Integer getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Integer refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Integer getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "UserSettings{" +
                "id=" + id +
                ", userId=" + userId +
                ", theme='" + theme + '\'' +
                ", notifications=" + notifications +
                ", autoRefresh=" + autoRefresh +
                ", refreshInterval=" + refreshInterval +
                ", timezone='" + timezone + '\'' +
                ", dateFormat='" + dateFormat + '\'' +
                ", itemsPerPage=" + itemsPerPage +
                '}';
    }
}
