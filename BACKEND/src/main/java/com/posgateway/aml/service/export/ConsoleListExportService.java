package com.posgateway.aml.service.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.export.ConsoleListExportRequestDto;
import com.posgateway.aml.dto.export.ConsoleListExportStartResponse;
import com.posgateway.aml.dto.export.ConsoleListViewType;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.export.ConsoleListExportJob;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.export.ConsoleListExportJobRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.reporting.ReportExportService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates Console list exports: RBAC checks, row counts, synchronous streaming downloads,
 * and asynchronous jobs for very large result sets with immutable audit logging per export.
 */
@Service
public class ConsoleListExportService {

    public static final int EXPORT_PAGE_SIZE = 500;
    public static final long ASYNC_ROW_THRESHOLD = 5_000L;
    public static final long SYNC_BINARY_MAX_ROWS = 2_000L;

    private final ConsoleListExportQueryFacade queryFacade;
    private final ConsoleListExportJobRepository jobRepository;
    private final ConsoleListExportAsyncWorker asyncWorker;
    private final ReportExportService reportExportService;
    private final AuditLogService auditLogService;
    private final PspIsolationService pspIsolationService;
    private final ObjectMapper objectMapper;

    @Value("${console.export.storage-dir:${java.io.tmpdir}/hokeka-console-exports}")
    private String exportStorageDir;

    public ConsoleListExportService(ConsoleListExportQueryFacade queryFacade,
                                    ConsoleListExportJobRepository jobRepository,
                                    ConsoleListExportAsyncWorker asyncWorker,
                                    ReportExportService reportExportService,
                                    AuditLogService auditLogService,
                                    PspIsolationService pspIsolationService,
                                    ObjectMapper objectMapper) {
        this.queryFacade = queryFacade;
        this.jobRepository = jobRepository;
        this.asyncWorker = asyncWorker;
        this.reportExportService = reportExportService;
        this.auditLogService = auditLogService;
        this.pspIsolationService = pspIsolationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts or immediately streams an export for the authenticated user and current filter set.
     */
    public ResponseEntity<?> export(User user, ConsoleListExportRequestDto request, String clientIp) {
        assertCanExport(user, request.viewType());
        Map<String, String> filters = request.filters() != null ? request.filters() : Map.of();
        String format = normalizeFormat(request.format());
        long totalRows = queryFacade.countRows(request.viewType(), filters);

        if (shouldRunAsync(format, totalRows)) {
            ConsoleListExportJob job = queueAsyncJob(user, request, filters, format, totalRows);
            auditExport(user, request.viewType(), format, totalRows, job.getJobUuid(), clientIp, "ASYNC");
            return ResponseEntity.accepted().body(new ConsoleListExportStartResponse(job.getJobUuid(), job.getStatus(), totalRows));
        }

        if ("CSV".equals(format)) {
            auditExport(user, request.viewType(), format, totalRows, "SYNC", clientIp, "SYNC");
            return streamCsv(user, request.viewType(), filters, request.includeCorrelated(), totalRows);
        }

        byte[] bytes = materializeBinaryExport(request.viewType(), filters, request.includeCorrelated(), format, totalRows);
        auditExport(user, request.viewType(), format, totalRows, "SYNC", clientIp, "SYNC");
        return binaryResponse(bytes, format, request.viewType());
    }

    /** Returns async job status for polling from the Console export menu. */
    public ConsoleListExportJob jobStatus(String jobUuid, User user) {
        ConsoleListExportJob job = jobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found"));
        assertJobAccess(user, job);
        return job;
    }

    /** Streams a completed async export file to the caller after RBAC and ownership checks. */
    public ResponseEntity<byte[]> downloadJob(String jobUuid, User user) throws IOException {
        ConsoleListExportJob job = jobStatus(jobUuid, user);
        if (!"COMPLETED".equals(job.getStatus()) || job.getFilePath() == null) {
            throw new IllegalStateException("Export is not ready for download");
        }
        byte[] bytes = Files.readAllBytes(Path.of(job.getFilePath()));
        String format = job.getExportFormat();
        return binaryResponse(bytes, format, ConsoleListViewType.valueOf(job.getViewType()));
    }

    /** Decides whether the export must run asynchronously based on format and estimated row volume. */
    private boolean shouldRunAsync(String format, long totalRows) {
        if (totalRows > ASYNC_ROW_THRESHOLD) {
            return true;
        }
        return !"CSV".equals(format) && totalRows > SYNC_BINARY_MAX_ROWS;
    }

    /** Persists a PENDING job row and kicks off background generation. */
    private ConsoleListExportJob queueAsyncJob(User user,
                                               ConsoleListExportRequestDto request,
                                               Map<String, String> filters,
                                               String format,
                                               long totalRows) {
        ConsoleListExportJob job = ConsoleListExportJob.builder()
                .jobUuid(UUID.randomUUID().toString())
                .viewType(request.viewType().name())
                .exportFormat(format)
                .status("PENDING")
                .pspId(user.getPsp() != null ? user.getPsp().getPspId() : 0L)
                .userId(user.getId())
                .username(user.getUsername())
                .filtersJson(writeJson(filters))
                .includeCorrelated(request.includeCorrelated())
                .rowCount(totalRows)
                .createdAt(LocalDateTime.now())
                .build();
        jobRepository.save(job);
        asyncWorker.runJob(job.getJobUuid(), exportStorageDir);
        return job;
    }

    /** Streams CSV to the HTTP response without buffering the full result set in heap memory. */
    private ResponseEntity<StreamingResponseBody> streamCsv(ConsoleListViewType viewType,
                                                            Map<String, String> filters,
                                                            boolean includeCorrelated,
                                                            long totalRows) {
        StreamingResponseBody body = outputStream -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write('\uFEFF');
            boolean headerWritten = false;
            long written = 0;
            int page = 0;
            while (written < totalRows) {
                List<Map<String, Object>> rows = queryFacade.fetchPage(viewType, filters, page, EXPORT_PAGE_SIZE, includeCorrelated);
                if (rows.isEmpty()) {
                    break;
                }
                if (!headerWritten) {
                    writer.write(String.join(",", rows.get(0).keySet()));
                    writer.newLine();
                    headerWritten = true;
                }
                for (Map<String, Object> row : rows) {
                    writer.write(toCsvLine(row));
                    writer.newLine();
                    written++;
                }
                page++;
            }
            writer.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileBase(viewType) + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** Pages through rows and renders PDF or XLSX bytes for smaller synchronous exports. */
    private byte[] materializeBinaryExport(ConsoleListViewType viewType,
                                           Map<String, String> filters,
                                           boolean includeCorrelated,
                                           String format,
                                           long totalRows) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 0;
        long collected = 0;
        while (collected < totalRows) {
            List<Map<String, Object>> rows = queryFacade.fetchPage(viewType, filters, page, EXPORT_PAGE_SIZE, includeCorrelated);
            if (rows.isEmpty()) {
                break;
            }
            all.addAll(rows);
            collected += rows.size();
            page++;
        }
        String title = "Hokeka Console Export — " + viewType.name();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Generated", LocalDateTime.now().toString());
        meta.put("Rows", String.valueOf(all.size()));
        if ("PDF".equals(format)) {
            return reportExportService.exportToPDF(all, title, meta);
        }
        return reportExportService.exportToXLSX(all, title);
    }

    /** Validates the caller holds permission to export the requested Console view. */
    private void assertCanExport(User user, ConsoleListViewType viewType) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        boolean allowed = switch (viewType) {
            case AUDIT_LOGS -> has(user, Permission.VIEW_AUDIT_LOGS);
            case USERS -> has(user, Permission.MANAGE_USERS);
            case RULES -> has(user, Permission.MANAGE_RULES);
            case SARS -> has(user, Permission.VIEW_SAR) || has(user, Permission.REPORT_VIEW);
            case MERCHANTS -> has(user, Permission.MERCHANT_VIEW);
            default -> has(user, Permission.EXPORT_DATA) || has(user, Permission.REPORT_VIEW)
                    || has(user, Permission.VIEW_CASES) || has(user, Permission.VIEW_TRANSACTION_DETAILS);
        };
        if (!allowed) {
            throw new AccessDeniedException("Insufficient permission to export " + viewType);
        }
    }

    /** Ensures async job downloads are limited to the owning user or platform administrators. */
    private void assertJobAccess(User user, ConsoleListExportJob job) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return;
        }
        if (user == null || !user.getId().equals(job.getUserId())) {
            throw new AccessDeniedException("Cannot access another user's export job");
        }
    }

    /** Writes an immutable audit log row describing the export action and scope. */
    private void auditExport(User user,
                             ConsoleListViewType viewType,
                             String format,
                             long rowCount,
                             String jobRef,
                             String clientIp,
                             String mode) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("viewType", viewType.name());
        after.put("format", format);
        after.put("rowCount", rowCount);
        after.put("jobRef", jobRef);
        after.put("mode", mode);
        auditLogService.logAction(user, "EXPORT", "CONSOLE_LIST_EXPORT", viewType.name(), null, after, clientIp,
                "Console list export");
    }

    /** Checks whether a user entity includes a named permission via their role template. */
    private boolean has(User user, Permission permission) {
        return user.getRole() != null && user.getRole().getPermissions() != null
                && user.getRole().getPermissions().contains(permission);
    }

    /** Normalizes export format aliases from the Console UI into CSV, PDF, or XLSX. */
    private static String normalizeFormat(String raw) {
        if (raw == null) {
            return "CSV";
        }
        String upper = raw.trim().toUpperCase();
        if ("EXCEL".equals(upper) || "XLSX".equals(upper)) {
            return "XLSX";
        }
        if ("PDF".equals(upper)) {
            return "PDF";
        }
        return "CSV";
    }

    /** Serializes filter maps for async job persistence. */
    private String writeJson(Map<String, String> filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Builds a single CSV line with comma sanitization for Excel compatibility. */
    private static String toCsvLine(Map<String, Object> row) {
        return row.values().stream()
                .map(v -> v == null ? "" : String.valueOf(v).replace(",", ";"))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /** Builds a download response for PDF/XLSX byte payloads. */
    private ResponseEntity<byte[]> binaryResponse(byte[] bytes, String format, ConsoleListViewType viewType) {
        MediaType mediaType = "PDF".equals(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String ext = "PDF".equals(format) ? "pdf" : "xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileBase(viewType) + "." + ext + "\"")
                .contentType(mediaType)
                .body(bytes);
    }

    /** Returns a filesystem-safe base filename for an export view. */
    private static String fileBase(ConsoleListViewType viewType) {
        return viewType.name().toLowerCase().replace('_', '-') + "-export";
    }

    private ResponseEntity<StreamingResponseBody> streamCsv(User user,
                                                            ConsoleListViewType viewType,
                                                            Map<String, String> filters,
                                                            boolean includeCorrelated,
                                                            long totalRows) {
        return streamCsv(viewType, filters, includeCorrelated, totalRows);
    }
}
