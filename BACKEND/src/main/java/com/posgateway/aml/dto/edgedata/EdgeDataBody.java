package com.posgateway.aml.dto.edgedata;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response body of a tunnel-backed endpoint: serialised as the wrapped JSON, but its
 * {@link #toString()} never shows it. Spring MVC logs the returned value's {@code toString()} at
 * DEBUG/TRACE ("Writing [...]"), so a plain JSON node would put pulled transaction data into the
 * application log whenever web logging is turned up — contract §7.16 forbids that at any level.
 *
 * @param json the envelope or item, built per request and never stored
 */
public record EdgeDataBody(@JsonValue JsonNode json) {

    /** A fixed, payload-free description for logs. */
    @Override
    public String toString() {
        return "EdgeDataBody[redacted]";
    }
}
