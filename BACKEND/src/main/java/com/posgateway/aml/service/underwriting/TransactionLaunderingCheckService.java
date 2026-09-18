package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.underwriting.SignalSeverity;
import com.posgateway.aml.model.underwriting.VerificationSignal;
import com.posgateway.aml.service.aml.AmlScenarioDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction-laundering / MCC-consistency internal checks for underwriting.
 *
 * <p>Rather than fabricate a classifier, this wires the platform's EXISTING, real
 * transaction detectors ({@link AmlScenarioDetectionService}: structuring, round-dollar,
 * funnel accounts, trade-based ML) over the merchant's recent activity and translates any
 * hits into normalized {@link VerificationSignal}s that feed the underwriting decision. For
 * a brand-new merchant with no history these return nothing; for an active merchant they
 * surface laundering typologies as underwriting evidence and force manual review.
 */
@Service
public class TransactionLaunderingCheckService {

    private static final Logger log = LoggerFactory.getLogger(TransactionLaunderingCheckService.class);

    /** Look-back window for laundering typologies. */
    private static final int WINDOW_DAYS = 30;
    /** Round-amount hits above this count are treated as a laundering indicator. */
    private static final int ROUND_DOLLAR_ALERT_COUNT = 5;

    private final AmlScenarioDetectionService scenarioDetectionService;

    public TransactionLaunderingCheckService(AmlScenarioDetectionService scenarioDetectionService) {
        this.scenarioDetectionService = scenarioDetectionService;
    }

    public List<VerificationSignal> findSignals(Merchant merchant) {
        List<VerificationSignal> signals = new ArrayList<>();
        if (merchant.getMerchantId() == null) {
            return signals;
        }
        String mid = String.valueOf(merchant.getMerchantId());
        String ref = mid;
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(WINDOW_DAYS);

        try {
            int structuring = size(scenarioDetectionService.detectStructuring(mid, start, end));
            if (structuring > 0) {
                signals.add(VerificationSignal.of("TL_STRUCTURING", SignalSeverity.HIGH, "INTERNAL",
                        ref, true, structuring + " structuring occurrence(s) in last " + WINDOW_DAYS + " days"));
            }

            int funnel = size(scenarioDetectionService.detectFunnelAccounts(mid, start, end));
            if (funnel > 0) {
                signals.add(VerificationSignal.of("TL_FUNNEL_ACCOUNT", SignalSeverity.HIGH, "INTERNAL",
                        ref, true, funnel + " funnel-account pattern(s) detected"));
            }

            int tbml = size(scenarioDetectionService.detectTradeBasedMl(mid, start, end));
            if (tbml > 0) {
                signals.add(VerificationSignal.of("TL_TRADE_BASED", SignalSeverity.HIGH, "INTERNAL",
                        ref, true, tbml + " trade-based-ML indicator(s) detected"));
            }

            int roundDollar = size(scenarioDetectionService.detectRoundDollar(mid, start, end));
            if (roundDollar >= ROUND_DOLLAR_ALERT_COUNT) {
                signals.add(VerificationSignal.of("TL_ROUND_AMOUNT", SignalSeverity.MEDIUM, "INTERNAL",
                        ref, false, roundDollar + " round-amount transactions — possible layering"));
            }
        } catch (Exception e) {
            log.warn("Transaction-laundering checks failed for merchant {}: {}", mid, e.getMessage());
        }
        return signals;
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
