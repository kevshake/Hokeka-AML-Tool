package com.posgateway.aml.service.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.export.ConsoleListViewType;
import com.posgateway.aml.entity.export.ConsoleListExportJob;
import com.posgateway.aml.repository.export.ConsoleListExportJobRepository;
import com.posgateway.aml.service.reporting.ReportExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Background worker that materializes large Console exports page-by-page into a temp file
 * so the HTTP request can return immediately with a job id.
 */
@Service
public class ConsoleListExportAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(ConsoleListExportAsyncWorker.class);

    private final ConsoleListExportJobRepository jobRepository;
    private final ConsoleListExportQueryFacade queryFacade;
    private final ReportExportService reportExportService;
    private final ObjectMapper objectMapper;

    public ConsoleListExportAsyncWorker(ConsoleListExportJobRepository jobRepository,
                                        ConsoleListExportQueryFacade queryFacade,
                                        ReportExportService reportExportService,
                                        ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.queryFacade = queryFacade;
        this.reportExportService = reportExportService;
        this.objectMapper = objectMapper;
    }

    /** Loads a queued job and writes its export artifact to disk without loading all rows at once. */
    @Async
    public void runJob(String jobUuid, String storageDir) {
        ConsoleListExportJob job = jobRepository.findByJobUuid(jobUuid).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus("RUNNING");
        jobRepository.save(job);
        try {
            Path dir = Path.of(storageDir);
            Files.createDirectories(dir);
            ConsoleListViewType viewType = ConsoleListViewType.valueOf(job.getViewType());
            Map<String, String> filters = readFilters(job.getFiltersJson());
            long total = job.getRowCount() != null ? job.getRowCount() : queryFacade.countRows(viewType, filters);
            String format = job.getExportFormat();
            Path target = dir.resolve(jobUuid + "." + extension(format));
            if ("CSV".equals(format)) {
                writeCsvFile(target, viewType, filters, job.isIncludeCorrelated(), total);
            } else {
                writeBinaryFile(target, viewType, filters, job.isIncludeCorrelated(), format, total);
            }
            job.setStatus("COMPLETED");
            job.setFilePath(target.toString());
            job.setFileName(target.getFileName().toString());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Async export job {} failed: {}", jobUuid, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    /** Writes CSV using paged queries so heap usage stays bounded for large exports. */
    private void writeCsvFile(Path target,
                              ConsoleListViewType viewType,
                              Map<String, String> filters,
                              boolean includeCorrelated,
                              long totalRows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            boolean headerWritten = false;
            long written = 0;
            int page = 0;
            while (written < totalRows) {
                List<Map<String, Object>> rows = queryFacade.fetchPage(viewType, filters, page,
                        ConsoleListExportService.EXPORT_PAGE_SIZE, includeCorrelated);
                if (rows.isEmpty()) {
                    break;
                }
                if (!headerWritten) {
                    writer.write(String.join(",", rows.get(0).keySet()));
                    writer.newLine();
                    headerWritten = true;
                }
                for (Map<String, Object> row : rows) {
                    writer.write(row.values().stream()
                            .map(v -> v == null ? "" : String.valueOf(v).replace(",", ";"))
                            .reduce((a, b) -> a + "," + b)
                            .orElse(""));
                    writer.newLine();
                    written++;
                }
                page++;
            }
        }
    }

    /** Collects paged rows for PDF/XLSX async jobs (still page-bound; total row cap enforced upstream). */
    private void writeBinaryFile(Path target,
                                 ConsoleListViewType viewType,
                                 Map<String, String> filters,
                                 boolean includeCorrelated,
                                 String format,
                                 long totalRows) throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 0;
        long collected = 0;
        while (collected < totalRows) {
            List<Map<String, Object>> rows = queryFacade.fetchPage(viewType, filters, page,
                    ConsoleListExportService.EXPORT_PAGE_SIZE, includeCorrelated);
            if (rows.isEmpty()) {
                break;
            }
            all.addAll(rows);
            collected += rows.size();
            page++;
        }
        String title = "Hokeka Console Export — " + viewType.name();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Rows", String.valueOf(all.size()));
        byte[] bytes = "PDF".equals(format)
                ? reportExportService.exportToPDF(all, title, meta)
                : reportExportService.exportToXLSX(all, title);
        Files.write(target, bytes);
    }

    /** Parses stored JSON filters back into a string map for query replay. */
    private Map<String, String> readFilters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            return Map.of();
        }
    }

    /** Maps export format to a file extension for async artifacts. */
    private static String extension(String format) {
        if ("PDF".equals(format)) {
            return "pdf";
        }
        if ("XLSX".equals(format)) {
            return "xlsx";
        }
        return "csv";
    }
}
