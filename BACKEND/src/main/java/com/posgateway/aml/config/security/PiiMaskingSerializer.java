package com.posgateway.aml.config.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Serializer to mask PII data
 * Example: "123456789" -> "****56789"
 */
public class PiiMaskingSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        if (value.length() <= 4) {
            gen.writeString("****");
        } else {
            String masked = "****" + value.substring(value.length() - 4);
            gen.writeString(masked);
        }
    }
}
