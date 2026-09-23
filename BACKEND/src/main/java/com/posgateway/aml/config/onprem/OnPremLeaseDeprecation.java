package com.posgateway.aml.config.onprem;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Full-BACKEND on-prem lease mode was removed as a product path. The sole supported on-premises
 * deployment is the Edge Node ({@code edge-host} + {@code edge-engine}). See {@code docs/SYSTEM-GLOSSARY.md}.
 */
public final class OnPremLeaseDeprecation {

    public static final String CODE = "ONPREM_LEASE_DEPRECATED";

    public static final String MESSAGE =
            "Full-BACKEND on-prem lease mode is no longer supported. "
                    + "Deploy an Edge Node (edge-host + edge-engine) and dual-post to the control plane. "
                    + "See docs/edge-client-install-guide.md and docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md.";

    private OnPremLeaseDeprecation() {
    }

    public static ResponseEntity<Map<String, Object>> gone() {
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "On-prem lease mode removed",
                        "code", CODE,
                        "message", MESSAGE,
                        "replacement", "edge-node",
                        "installGuide", "docs/edge-client-install-guide.md",
                        "dualPostContract", "docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md"));
    }

    public static String jsonBody() {
        return "{\"error\":\"On-prem lease mode removed\","
                + "\"code\":\"" + CODE + "\","
                + "\"message\":\"" + escape(MESSAGE) + "\","
                + "\"replacement\":\"edge-node\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
