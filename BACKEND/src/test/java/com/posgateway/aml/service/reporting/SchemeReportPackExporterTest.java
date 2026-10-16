package com.posgateway.aml.service.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the scheme pack renders real JSON / CSV / PDF content for the requested format. */
class SchemeReportPackExporterTest {

    private final SchemeReportPackExporter exporter = new SchemeReportPackExporter(new ObjectMapper());
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = LocalDate.of(2026, 1, 31);

    /** Builds a pack shaped like CentralBankReportGenerator output. */
    private Map<String, Object> pack() {
        Map<String, Object> data = new HashMap<>();
        data.put("total_cases", 3);
        data.put("total_sars", 1);
        data.put("cases_by_status", Map.of("OPEN", 2L, "CLOSED", 1L));
        Map<String, Object> sar = new HashMap<>();
        sar.put("sar_id", 42L);
        sar.put("merchant_id", "M-1");
        sar.put("status", "=HYPERLINK(\"x\")");
        data.put("sar_details", List.of(sar));
        data.put("psp_id", 7L);
        return data;
    }

    @Test
    void parseAcceptsKnownFormatsCaseInsensitivelyAndRejectsOthers() {
        assertThat(SchemeReportPackExporter.Format.parse("csv")).isEqualTo(SchemeReportPackExporter.Format.CSV);
        assertThat(SchemeReportPackExporter.Format.parse(" Pdf ")).isEqualTo(SchemeReportPackExporter.Format.PDF);
        assertThat(SchemeReportPackExporter.Format.parse(null)).isEqualTo(SchemeReportPackExporter.Format.JSON);
        assertThatThrownBy(() -> SchemeReportPackExporter.Format.parse("XLSX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported format");
    }

    @Test
    void csvContainsSummaryFlattenedBreakdownAndRowTableWithInjectionGuard() {
        String csv = new String(exporter.render("CENTRAL_BANK", start, end, pack(),
                SchemeReportPackExporter.Format.CSV).content(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("# \"CENTRAL_BANK\" report pack, period 2026-01-01 to 2026-01-31");
        assertThat(csv).contains("\"total_cases\",\"3\"");
        assertThat(csv).contains("\"cases_by_status.OPEN\",\"2\"");
        assertThat(csv).contains("# \"sar_details\"");
        assertThat(csv).contains("\"merchant_id\",\"sar_id\",\"status\"");
        // Formula-leading value is neutralised with a leading quote and inner quotes doubled.
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"");
    }

    @Test
    void pdfIsARealPdfContainingThePackData() throws Exception {
        SchemeReportPackExporter.RenderedPack pack = exporter.render("CENTRAL_BANK", start, end, pack(),
                SchemeReportPackExporter.Format.PDF);

        byte[] bytes = pack.content();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(bytes);
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("CENTRAL_BANK Report Pack");
            assertThat(text).contains("total_cases");
            assertThat(text).contains("cases_by_status.OPEN");
            assertThat(text).contains("sar_details");
            assertThat(text).contains("M-1");
        } finally {
            reader.close();
        }
    }

    @Test
    void emptyRowSetRendersNoRecordsInsteadOfDisappearing() {
        Map<String, Object> data = new HashMap<>();
        data.put("high_risk_merchants", List.of());
        data.put("total_merchants_scanned", 0);
        String csv = new String(exporter.render("SCHEME_MONITORING", start, end, data,
                SchemeReportPackExporter.Format.CSV).content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("# \"high_risk_merchants\"").contains("\"no records\"");
        assertThat(exporter.render("SCHEME_MONITORING", start, end, data,
                SchemeReportPackExporter.Format.PDF).content()).isNotEmpty();
    }

    @Test
    void jsonAndMediaTypesAndFilenameMatchTheFormat() {
        String json = new String(exporter.render("CENTRAL_BANK", start, end, pack(),
                SchemeReportPackExporter.Format.JSON).content(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"total_cases\" : 3");
        assertThat(SchemeReportPackExporter.Format.PDF.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(SchemeReportPackExporter.Format.CSV.mediaType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(exporter.filename("CENTRAL_BANK", start, end, SchemeReportPackExporter.Format.PDF))
                .isEqualTo("CENTRAL_BANK_Report_2026-01-01_2026-01-31.pdf");
        assertThat(exporter.filename("../x y", start, end, SchemeReportPackExporter.Format.CSV))
                .isEqualTo("___x_y_Report_2026-01-01_2026-01-31.csv");
    }
}
