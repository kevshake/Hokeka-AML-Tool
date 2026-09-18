package com.posgateway.aml.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AES-GCM converter with an explicit wire prefix. Unprefixed values are treated
 * as legacy plaintext so existing UBO rows can be migrated without downtime.
 */
@Converter
public class VersionedAesGcmStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private final AesGcmStringConverter delegate = new AesGcmStringConverter();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return null;
        return PREFIX + delegate.convertToDatabaseColumn(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        if (!dbData.startsWith(PREFIX)) return dbData;
        return delegate.convertToEntityAttribute(dbData.substring(PREFIX.length()));
    }
}
