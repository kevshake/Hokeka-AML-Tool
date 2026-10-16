package com.posgateway.aml.client.regulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C5: an empty or unparseable 2xx body from FRC goAML must NOT be read as an acceptance
 * ("RECEIVED"); status extraction fails closed to "UNKNOWN".
 */
class FrcSubmissionClientStatusTest {

    private final FrcSubmissionClient client = new FrcSubmissionClient(
            "", "", "", "X-API-Key", "", Duration.ofSeconds(5), new ObjectMapper());

    private String status(String body) {
        return ReflectionTestUtils.invokeMethod(client, "extractStatus", body);
    }

    @Test
    void emptyOrBlankBodyIsUnknown() {
        assertEquals("UNKNOWN", status(null));
        assertEquals("UNKNOWN", status("   "));
    }

    @Test
    void unparseableBodyIsUnknown() {
        assertEquals("UNKNOWN", status("<html>gateway ok</html>"));
        assertEquals("UNKNOWN", status("{\"message\":\"ok\"}"));
    }

    @Test
    void explicitStatusIsReturnedFromJsonOrXml() {
        assertEquals("ACCEPTED", status("{\"status\":\"ACCEPTED\"}"));
        assertEquals("PROCESSING", status("{\"processingStatus\":\"PROCESSING\"}"));
    }
}
