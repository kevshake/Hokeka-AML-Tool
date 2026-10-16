package com.posgateway.aml.service.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.client.aml.AmlMicroserviceProperties;
import com.posgateway.aml.repository.sanctions.SanctionsListRepository;
import com.posgateway.aml.service.sanctions.WatchlistUpdateTrackingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Measures parse throughput for a representative OpenSanctions JSON-lines sample (PEP + sanctions).
 */
@Tag("opensanctions")
class OpenSanctionsSampleLoadTest {

    @Test
    void sampleJsonLinesParseIncludesPepClassification() throws Exception {
        SanctionsListDownloadService service = newSanctionsDownloadServiceStub();
        Method parse = SanctionsListDownloadService.class.getDeclaredMethod("parseSanctionEntity",
                com.fasterxml.jackson.databind.JsonNode.class);
        parse.setAccessible(true);

        Path sample = Path.of("src/test/resources/opensanctions-sample.jsonl");
        long start = System.nanoTime();
        long heapBefore = usedHeapBytes();
        int pepCount = 0;
        int total = 0;
        try (var lines = java.nio.file.Files.lines(sample)) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            for (String line : lines.toList()) {
                if (line.isBlank()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> entity = (Map<String, Object>) parse.invoke(
                        service, mapper.readTree(line));
                if (entity == null) continue;
                total++;
                if ("PEP".equals(entity.get("pepLevel"))) {
                    pepCount++;
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long heapDeltaMb = (usedHeapBytes() - heapBefore) / (1024 * 1024);

        assertEquals(2, total, "sample fixture must contain two entities");
        assertTrue(pepCount >= 1, "sample must classify at least one PEP via role.pep topic");
        assertTrue(elapsedMs < 30_000, "sample parse must finish quickly (ms=" + elapsedMs + ")");
        System.out.printf("OpenSanctions sample parse: entities=%d pep=%d elapsedMs=%d heapDeltaMb=%d%n",
                total, pepCount, elapsedMs, heapDeltaMb);
    }

    /** Builds a minimal service instance for package-private parser reflection (no network I/O). */
    private static SanctionsListDownloadService newSanctionsDownloadServiceStub() {
        return new SanctionsListDownloadService(
                new ObjectMapper(),
                mock(RestTemplate.class),
                mock(WatchlistUpdateTrackingService.class),
                mock(AmlMicroserviceProperties.class),
                mock(SanctionsListRepository.class));
    }

    /** Returns current JVM heap usage for coarse memory measurement during sample ingest tests. */
    private static long usedHeapBytes() {
        var bean = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return bean.getUsed();
    }
}
