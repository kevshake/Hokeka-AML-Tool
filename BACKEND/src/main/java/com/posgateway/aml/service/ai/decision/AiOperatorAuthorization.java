package com.posgateway.aml.service.ai.decision;

import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Component;

/**
 * Method-security helper: JEV operator settings are platform-operator only.
 */
@Component("aiOperatorAuth")
public class AiOperatorAuthorization {

    private final PspIsolationService pspIsolationService;

    public AiOperatorAuthorization(PspIsolationService pspIsolationService) {
        this.pspIsolationService = pspIsolationService;
    }

    public boolean isPlatformOperator() {
        return pspIsolationService.isPlatformAdministrator();
    }
}
