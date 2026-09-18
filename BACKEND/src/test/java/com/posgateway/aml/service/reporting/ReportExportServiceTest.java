package com.posgateway.aml.service.reporting;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {

    @Test
    void xlsxExportProducesWorkbookPackageWithTypedCells() throws Exception {
        ReportExportService service = new ReportExportService();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer_name", "Alice & Co");
        row.put("risk_score", 82.5);
        row.put("blocked", true);

        byte[] workbook = service.exportToXLSX(java.util.List.of(row), "Risk Report");

        assertThat(workbook[0]).isEqualTo((byte) 'P');
        assertThat(workbook[1]).isEqualTo((byte) 'K');
        Set<String> entries = new TreeSet<>();
        String worksheet = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    worksheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(entries).contains("[Content_Types].xml", "xl/workbook.xml",
                "xl/styles.xml", "xl/worksheets/sheet1.xml");
        assertThat(worksheet).contains("Alice &amp; Co", "t=\"n\"><v>82.5", "t=\"b\"><v>1");
    }
}
