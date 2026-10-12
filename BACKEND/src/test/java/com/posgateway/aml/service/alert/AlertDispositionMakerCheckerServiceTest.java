package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.model.AlertDisposition;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AlertDispositionMakerCheckerServiceTest {

    private final AlertDispositionMakerCheckerService service = new AlertDispositionMakerCheckerService();

    @Test
    void truePositiveRequiresSecondReviewer() {
        Alert alert = new Alert();
        boolean first = service.applyMakerChecker(alert, AlertDisposition.TRUE_POSITIVE_REPORTED, "maker");
        assertFalse(first);
        assertEquals(AlertDispositionMakerCheckerService.STATUS_PENDING, alert.getDispositionReviewStatus());
        assertEquals("maker", alert.getFirstReviewer());

        boolean second = service.applyMakerChecker(alert, AlertDisposition.TRUE_POSITIVE_REPORTED, "checker");
        assertTrue(second);
        assertEquals(AlertDispositionMakerCheckerService.STATUS_APPROVED, alert.getDispositionReviewStatus());
        assertEquals("checker", alert.getSecondReviewer());
    }

    @Test
    void secondReviewerMustDifferFromFirst() {
        Alert alert = new Alert();
        service.applyMakerChecker(alert, AlertDisposition.TRUE_POSITIVE_BLOCKED, "maker");
        assertThrows(ResponseStatusException.class,
                () -> service.applyMakerChecker(alert, AlertDisposition.TRUE_POSITIVE_BLOCKED, "maker"));
    }

    @Test
    void falsePositiveSkipsMakerChecker() {
        Alert alert = new Alert();
        assertTrue(service.applyMakerChecker(alert, AlertDisposition.FALSE_POSITIVE, "analyst"));
        assertNull(alert.getDispositionReviewStatus());
    }
}
