package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.model.AlertDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces maker-checker (two-person) approval before true-positive alert dispositions finalize.
 */
@Service
public class AlertDispositionMakerCheckerService {

    public static final String STATUS_PENDING = "PENDING_SECOND_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";

    /**
     * Applies maker-checker rules: first reviewer submits, second distinct reviewer approves true positives.
     *
     * @return true when disposition is fully approved and downstream case/SAR bridges may run
     */
    public boolean applyMakerChecker(Alert alert, AlertDisposition disposition, String reviewerUsername) {
        if (disposition == null || !disposition.isTruePositive()) {
            alert.setDispositionReviewStatus(null);
            return true;
        }
        if (reviewerUsername == null || reviewerUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reviewer identity is required for true-positive dispositions");
        }
        if (STATUS_PENDING.equals(alert.getDispositionReviewStatus())) {
            if (reviewerUsername.equals(alert.getFirstReviewer())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Second reviewer must differ from the first reviewer (maker-checker)");
            }
            alert.setSecondReviewer(reviewerUsername);
            alert.setDispositionReviewStatus(STATUS_APPROVED);
            return true;
        }
        alert.setFirstReviewer(reviewerUsername);
        alert.setDispositionReviewStatus(STATUS_PENDING);
        return false;
    }
}
