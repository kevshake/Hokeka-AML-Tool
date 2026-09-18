package com.posgateway.aml.entity.converter;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class VersionedAesGcmStringConverterTest {
    @Test
    void encryptsNewValuesAndReadsLegacyPlaintext() {
        System.setProperty("SECURITY_ENCRYPTION_KEY",
                Base64.getEncoder().encodeToString(new byte[32]));
        VersionedAesGcmStringConverter converter = new VersionedAesGcmStringConverter();

        String encrypted = converter.convertToDatabaseColumn("A1234567");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("A1234567", encrypted);
        assertEquals("A1234567", converter.convertToEntityAttribute(encrypted));
        assertEquals("legacy-value", converter.convertToEntityAttribute("legacy-value"));
    }
}
