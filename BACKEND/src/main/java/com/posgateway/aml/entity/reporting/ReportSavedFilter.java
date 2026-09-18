package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "report_saved_filters", indexes = {
        @Index(name = "idx_report_filters_user", columnList = "user_id"),
        @Index(name = "idx_report_filters_report", columnList = "report_id")
})
public class ReportSavedFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "filter_name", nullable = false)
    private String filterName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_criteria", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> filterCriteria;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getFilterName() { return filterName; }
    public void setFilterName(String filterName) { this.filterName = filterName; }
    public Map<String, Object> getFilterCriteria() { return filterCriteria; }
    public void setFilterCriteria(Map<String, Object> filterCriteria) { this.filterCriteria = filterCriteria; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
