package com.posgateway.aml.entity.sanctions;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Metadata for sanctions list downloads ({@code sanctions_lists} table).
 */
@Entity
@Table(name = "sanctions_lists", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sanctions_lists_version", columnNames = {"list_name", "version"})
})
public class SanctionsList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "list_id")
    private Integer listId;

    @Column(name = "list_name", nullable = false, length = 100)
    private String listName;

    @Column(name = "list_source", nullable = false, length = 100)
    private String listSource;

    @Column(name = "version", length = 100)
    private String version;

    @Column(name = "downloaded_at", nullable = false)
    private LocalDateTime downloadedAt;

    @Column(name = "record_count")
    private Integer recordCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        if (downloadedAt == null) {
            downloadedAt = LocalDateTime.now();
        }
    }

    public Integer getListId() {
        return listId;
    }

    public void setListId(Integer listId) {
        this.listId = listId;
    }

    public String getListName() {
        return listName;
    }

    public void setListName(String listName) {
        this.listName = listName;
    }

    public String getListSource() {
        return listSource;
    }

    public void setListSource(String listSource) {
        this.listSource = listSource;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getDownloadedAt() {
        return downloadedAt;
    }

    public void setDownloadedAt(LocalDateTime downloadedAt) {
        this.downloadedAt = downloadedAt;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
