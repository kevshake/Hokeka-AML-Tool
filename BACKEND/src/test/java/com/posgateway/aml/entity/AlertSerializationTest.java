package com.posgateway.aml.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GET /alerts} serialises this entity directly, so its JSON is the dashboard's contract.
 *
 * <p>The dashboard reads {@code id}, {@code alertType}, {@code priority} and {@code description};
 * the entity stores {@code alertId}, {@code sourceType}, {@code severity} and {@code reason}. Before
 * the aliases every alert id rendered as <em>"#undefined"</em>, React row keys collided (so the
 * select-all checkbox misbehaved) and bulk triage issued {@code PUT /alerts/undefined/status}.
 */
class AlertSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> serialize(Alert alert) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.convertValue(alert, Map.class);
        return json;
    }

    private Alert alert() {
        Alert a = new Alert();
        ReflectionTestUtils.setField(a, "alertId", 4711L);
        ReflectionTestUtils.setField(a, "reason", "Velocity threshold exceeded");
        ReflectionTestUtils.setField(a, "severity", "CRITICAL");
        ReflectionTestUtils.setField(a, "sourceType", "RULE_ENGINE");
        return a;
    }

    @Test
    void publishesTheFieldNamesTheDashboardReads() {
        Map<String, Object> json = serialize(alert());

        assertEquals(4711, ((Number) json.get("id")).longValue(), "row key + displayed '#id'");
        assertEquals("Velocity threshold exceeded", json.get("description"));
        assertEquals("CRITICAL", json.get("priority"));
        assertEquals("RULE_ENGINE", json.get("alertType"));
    }

    @Test
    void keepsTheCanonicalNamesSoExistingConsumersDoNotBreak() {
        Map<String, Object> json = serialize(alert());

        assertTrue(json.containsKey("alertId"), "canonical id must remain");
        assertTrue(json.containsKey("reason"));
        assertTrue(json.containsKey("severity"));
        assertTrue(json.containsKey("sourceType"));
    }

    @Test
    void alertTypeFallsBackToTheActionWhenNoSourceType() {
        Alert a = alert();
        ReflectionTestUtils.setField(a, "sourceType", null);
        ReflectionTestUtils.setField(a, "action", "BLOCK");

        assertEquals("BLOCK", serialize(a).get("alertType"));
    }

    @Test
    void anIdIsNeverNullForAPersistedAlert() {
        // The failure mode being guarded: a null here renders "#undefined" and produces
        // PUT /alerts/undefined/status on bulk triage.
        assertEquals(4711, ((Number) serialize(alert()).get("id")).longValue());
    }
}
