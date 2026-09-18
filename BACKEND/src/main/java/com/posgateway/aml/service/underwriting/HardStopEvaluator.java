package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.model.underwriting.SignalSeverity;
import com.posgateway.aml.model.underwriting.VerificationSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard-stop evaluation. A numeric risk score must never override these conditions — if
 * any fire, underwriting is REJECT regardless of the computed score (per the KYB brief).
 *
 * <p>Phase 1 derives hard stops from the normalized signal set: any CRITICAL signal, or
 * any signal carrying an explicit hard-stop code, is a hard stop. New structured hard-stop
 * conditions (confirmed sanctions prohibition, falsified documents, dissolved entity,
 * unauthorized settlement account, undisclosed third-party processing, confirmed illegal
 * products) attach here as their evidence sources come online.
 */
@Component
public class HardStopEvaluator {

    /** Signal codes that are always hard stops irrespective of severity. */
    private static final List<String> HARD_STOP_CODES = List.of(
            "NO_BENEFICIAL_OWNERS",
            "SANCTIONS_CONFIRMED",
            "ENTITY_NOT_FOUND",
            "ENTITY_DISSOLVED",
            "DOCUMENT_FALSIFIED",
            "SETTLEMENT_ACCOUNT_UNAUTHORIZED",
            "UNDISCLOSED_THIRD_PARTY_PROCESSING",
            "ILLEGAL_PRODUCTS_CONFIRMED");

    public List<String> evaluate(MerchantVerificationContext context, List<VerificationSignal> signals) {
        List<String> hardStops = new ArrayList<>();
        if (signals == null) {
            return hardStops;
        }
        for (VerificationSignal s : signals) {
            boolean isHardStop = s.getSeverity() == SignalSeverity.CRITICAL
                    || HARD_STOP_CODES.contains(s.getSignalCode());
            if (isHardStop) {
                hardStops.add(s.getSignalCode() + ": "
                        + (s.getDetail() != null ? s.getDetail() : "hard-stop condition"));
            }
        }
        return hardStops;
    }
}
