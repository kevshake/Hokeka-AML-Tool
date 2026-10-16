package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifies that alert prioritisation weights the severities the decision engine really emits
 * (CRITICAL / HIGH / MEDIUM) as well as the publisher vocabulary (WARN / INFO).
 */
class AlertPrioritizationServiceTest {

    private final AlertPrioritizationService service =
            new AlertPrioritizationService(mock(AlertRepository.class));

    /** Builds an alert with no age and no risk score so only severity contributes. */
    private static Alert alertWithSeverity(String severity) {
        Alert alert = new Alert();
        alert.setSeverity(severity);
        return alert;
    }

    @Test
    void decisionEngineSeveritiesReceiveWeight() {
        assertEquals(0.3, service.calculatePriorityScore(alertWithSeverity("CRITICAL")), 1e-9);
        assertEquals(0.225, service.calculatePriorityScore(alertWithSeverity("HIGH")), 1e-9);
        assertEquals(0.15, service.calculatePriorityScore(alertWithSeverity("MEDIUM")), 1e-9);
    }

    @Test
    void publisherSeveritiesStillWeightedAndUnknownIsZero() {
        assertEquals(0.15, service.calculatePriorityScore(alertWithSeverity("WARN")), 1e-9);
        assertEquals(0.15, service.calculatePriorityScore(alertWithSeverity("medium")), 1e-9);
        assertEquals(0.0, service.calculatePriorityScore(alertWithSeverity("INFO")), 1e-9);
        assertEquals(0.0, service.calculatePriorityScore(alertWithSeverity(null)), 1e-9);
    }

    @Test
    void highSeverityAlertOutranksMediumWithSameScore() {
        Alert medium = alertWithSeverity("MEDIUM");
        medium.setScore(0.5);
        Alert high = alertWithSeverity("HIGH");
        high.setScore(0.5);
        Alert critical = alertWithSeverity("CRITICAL");
        critical.setScore(0.5);

        List<Alert> ordered = service.prioritizeAlerts(List.of(medium, critical, high));

        assertEquals(List.of(critical, high, medium), ordered);
        assertTrue(service.calculatePriorityScore(high) > service.calculatePriorityScore(medium));
    }
}
