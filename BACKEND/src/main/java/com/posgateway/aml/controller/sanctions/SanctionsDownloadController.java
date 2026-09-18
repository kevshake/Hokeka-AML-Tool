package com.posgateway.aml.controller.sanctions;

import com.posgateway.aml.service.download.SanctionsListDownloadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API for OpenSanctions watchlist ingest ({@link SanctionsListDownloadService}).
 * Ingest is disabled by default ({@code sanctions.download.enabled=false}).
 */
@RestController
@RequestMapping("/sanctions/download")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO')")
public class SanctionsDownloadController {

    private final SanctionsListDownloadService downloadService;

    public SanctionsDownloadController(SanctionsListDownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(downloadService.status());
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        if (!downloadService.isDownloadEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "triggered", false,
                    "message", "Sanctions download is disabled. Set SANCTIONS_DOWNLOAD_ENABLED=true and sanctions.opensanctions.url."));
        }
        downloadService.manualDownload();
        return ResponseEntity.accepted().body(Map.of(
                "triggered", true,
                "message", "OpenSanctions download started. Check /sanctions/lists for ingest metadata."));
    }
}
