package com.posgateway.aml.service.jev;

import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Component;

/**
 * Method-security helper: JEV operator settings are platform-operator only.
 */
@Component("jevOperatorAuth")
public class JevOperatorAuthorization {

    private final PspIsolationService pspIsolationService;

    public JevOperatorAuthorization(PspIsolationService pspIsolationService) {
        this.pspIsolationService = pspIsolationService;
    }

    public boolean isPlatformOperator() {
        return pspIsolationService.isPlatformAdministrator();
    }
}
