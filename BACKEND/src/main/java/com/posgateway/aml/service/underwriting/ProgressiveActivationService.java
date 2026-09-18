package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.underwriting.MerchantActivationControl;
import com.posgateway.aml.model.underwriting.UnderwritingDecision;
import com.posgateway.aml.model.underwriting.UnderwritingOutcome;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.underwriting.MerchantActivationControlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Progressive / conditional activation. A merchant approved with controls (or in EDD) does
 * not receive full capability immediately: this service applies a REAL reduced daily limit
 * for a heightened-monitoring window and records the graduated state + controls, so the
 * restriction is enforced (the reduced limit flows into limit enforcement) and auditable.
 * The original limit is stored so the merchant can be graduated back up once the window
 * closes and delivery/refund performance is verified.
 */
@Service
public class ProgressiveActivationService {

    private static final Logger log = LoggerFactory.getLogger(ProgressiveActivationService.class);

    private static final BigDecimal RESTRICTED_FACTOR = new BigDecimal("0.25"); // EDD → 25% of limit
    private static final BigDecimal WATCH_FACTOR = new BigDecimal("0.50");      // controls → 50%
    private static final int MONITORING_DAYS = 30;

    private final MerchantActivationControlRepository controlRepository;
    private final MerchantRepository merchantRepository;

    public ProgressiveActivationService(MerchantActivationControlRepository controlRepository,
                                        MerchantRepository merchantRepository) {
        this.controlRepository = controlRepository;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Apply progressive controls based on the underwriting outcome. No-op (returns null)
     * for APPROVE / MANUAL_REVIEW / REJECT — only conditional approvals get a reduced
     * initial limit + monitoring window.
     */
    @Transactional
    public MerchantActivationControl applyControls(Merchant merchant, UnderwritingOutcome outcome) {
        UnderwritingDecision d = outcome.getDecision();
        String state;
        BigDecimal factor;
        if (d == UnderwritingDecision.ENHANCED_DUE_DILIGENCE) {
            state = "RESTRICTED";
            factor = RESTRICTED_FACTOR;
        } else if (d == UnderwritingDecision.APPROVE_WITH_CONTROLS) {
            state = "WATCH";
            factor = WATCH_FACTOR;
        } else {
            return null;
        }

        BigDecimal original = merchant.getDailyLimit();
        BigDecimal reduced = null;
        if (original != null && original.signum() > 0) {
            reduced = original.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            merchant.setDailyLimit(reduced);
            merchantRepository.save(merchant);
        }

        MerchantActivationControl control = new MerchantActivationControl();
        control.setMerchantId(merchant.getMerchantId());
        control.setState(state);
        control.setControls(String.join(",", outcome.getRequiredControls()));
        control.setOriginalDailyLimit(original);
        control.setReducedDailyLimit(reduced);
        control.setMonitoringUntil(LocalDateTime.now().plusDays(MONITORING_DAYS));
        control.setActive(true);
        MerchantActivationControl saved = controlRepository.save(control);

        log.info("Progressive activation for merchant {}: state={} dailyLimit {}→{} controls={}",
                merchant.getMerchantId(), state, original, reduced, control.getControls());
        return saved;
    }
}
