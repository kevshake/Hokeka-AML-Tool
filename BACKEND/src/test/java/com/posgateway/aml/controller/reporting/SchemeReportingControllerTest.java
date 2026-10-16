package com.posgateway.aml.controller.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.service.reporting.ReportRunTraceService;
import com.posgateway.aml.service.reporting.SchemeReportPackExporter;
import com.posgateway.aml.service.reporting.SchemeReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Controller-level checks that the pack export honours the requested format end to end. */
class SchemeReportingControllerTest {

    private SchemeReportingService reportingService;
    private ReportRunTraceService traceService;
    private SchemeReportingController controller;
    private final LocalDate start = LocalDate.of(2026, 2, 1);
    private final LocalDate end = LocalDate.of(2026, 2, 28);

    @BeforeEach
    void setUp() {
        reportingService = mock(SchemeReportingService.class);
        traceService = mock(ReportRunTraceService.class);
        controller = new SchemeReportingController(reportingService, traceService,
                new SchemeReportPackExporter(new ObjectMapper()));
        when(reportingService.generateReport(eq("CENTRAL_BANK"), any(), any()))
                .thenReturn(Map.of("total_cases", 5));
        when(traceService.recordDocument(anyString(), any(), any(), any())).thenReturn("exec-1");
    }

    @Test
    void pdfFormatReturnsPdfBytesContentTypeAndFilename() {
        ResponseEntity<byte[]> resp = controller.generateReportPack("central_bank", start, end, "pdf");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("CENTRAL_BANK_Report_2026-02-01_2026-02-28.pdf");
        assertThat(new String(resp.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(resp.getHeaders().getFirst("X-Report-Execution-Id")).isEqualTo("exec-1");
    }

    @Test
    void csvFormatReturnsCsv() {
        ResponseEntity<byte[]> resp = controller.generateReportPack("CENTRAL_BANK", start, end, "CSV");

        assertThat(resp.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(".csv");
        assertThat(new String(resp.getBody(), StandardCharsets.UTF_8)).contains("\"total_cases\",\"5\"");
    }

    @Test
    void unsupportedFormatIsRejectedBeforeGeneratingOrTracing() {
        ResponseEntity<byte[]> resp = controller.generateReportPack("CENTRAL_BANK", start, end, "DOCX");

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(new String(resp.getBody(), StandardCharsets.UTF_8)).contains("Unsupported format");
        verify(reportingService, never()).generateReport(anyString(), any(), any());
        verify(traceService, never()).recordDocument(anyString(), any(), any(), any());
    }

    @Test
    void invertedPeriodAndUnknownTypeAreBadRequests() {
        assertThat(controller.generateReportPack("CENTRAL_BANK", end, start, "JSON").getStatusCode().value())
                .isEqualTo(400);
        assertThat(controller.generateReportPack("NOPE", start, end, "JSON").getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void accessDeniedPropagatesInsteadOfBecoming500() {
        when(reportingService.generateReport(eq("CENTRAL_BANK"), any(), any()))
                .thenThrow(new AccessDeniedException("User is not associated with a PSP"));
        assertThatThrownBy(() -> controller.generateReportPack("CENTRAL_BANK", start, end, "JSON"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
