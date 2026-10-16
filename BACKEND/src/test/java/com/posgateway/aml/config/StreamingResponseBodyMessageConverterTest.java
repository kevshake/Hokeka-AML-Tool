package com.posgateway.aml.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression (sim run 2026-10-16): {@code POST /console/exports} (CSV) and {@code GET /exports/cases.csv}
 * returned 500 "No converter for [...$$Lambda] with preset Content-Type 'text/csv'" because they
 * return a StreamingResponseBody inside a wildcard {@code ResponseEntity<?>}.
 */
class StreamingResponseBodyMessageConverterTest {

    @RestController
    static class WildcardCsvController {
        @GetMapping("/csv")
        public ResponseEntity<?> csv() {
            StreamingResponseBody body = out -> out.write("a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8").body(body);
        }
    }

    @Test
    void wildcardResponseEntityWithStreamingBodyIsWrittenAsCsv() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WildcardCsvController())
                .setMessageConverters(new StreamingResponseBodyMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .build();
        mvc.perform(get("/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(content().string("a,b\n1,2\n"));
    }
}
