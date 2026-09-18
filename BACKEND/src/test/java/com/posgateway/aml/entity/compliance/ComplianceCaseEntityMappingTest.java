package com.posgateway.aml.entity.compliance;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * compliance_cases PK is {@code case_id} (V2), not {@code id}.
 */
class ComplianceCaseEntityMappingTest {

    @Test
    void primaryKeyMapsToCaseIdColumn() throws NoSuchFieldException {
        Column column = ComplianceCase.class.getDeclaredField("id").getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("case_id", column.name());
    }
}
