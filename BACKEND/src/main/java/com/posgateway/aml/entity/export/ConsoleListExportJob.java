package com.posgateway.aml.entity.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persists an asynchronous Console list export so large result sets can be generated off-request
 * and downloaded when ready without holding the HTTP connection or loading all rows in memory.
 */
@Entity
@Table(name = "console_list_export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsoleListExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, unique = true, length = 64)
    private String jobUuid;

    @Column(name = "view_type", nullable = false, length = 64)
    private String viewType;

    @Column(name = "export_format", nullable = false, length = 16)
    private String exportFormat;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 255)
    private String username;

    @Column(name = "filters_json", columnDefinition = "jsonb")
    private String filtersJson;

    @Column(name = "include_correlated", nullable = false)
    private boolean includeCorrelated;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
