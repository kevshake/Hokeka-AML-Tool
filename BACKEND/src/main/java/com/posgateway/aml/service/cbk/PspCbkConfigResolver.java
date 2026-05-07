package com.posgateway.aml.service.cbk;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkProperties;
import com.posgateway.aml.integration.cbk.PspCbkContext;
import com.posgateway.aml.repository.PspRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves per-PSP CBK configuration into a {@link PspCbkContext} ready for
 * use by the GDI client.
 *
 * <p>Resolution rules:
 * <ol>
 *   <li>PSP must exist and have {@code cbkReportingEnabled = true}.</li>
 *   <li>PSP must have a non-blank {@code cbkInstitutionCode}.</li>
 *   <li>OAuth2 credentials: per-PSP values ({@code cbkClientId} /
 *       {@code cbkClientSecret}) take precedence; falls back to global
 *       {@link CbkProperties} when the per-PSP fields are blank.</li>
 * </ol>
 *
 * <p>Returns {@link Optional#empty()} when either condition 1 or 2 is not met;
 * callers should treat that as a "skip" and not attempt a CBK submission.
 */
@Component
public class PspCbkConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(PspCbkConfigResolver.class);

    private final PspRepository pspRepository;
    private final CbkProperties cbkProperties;

    public PspCbkConfigResolver(PspRepository pspRepository, CbkProperties cbkProperties) {
        this.pspRepository = pspRepository;
        this.cbkProperties = cbkProperties;
    }

    /**
     * Attempt to build a {@link PspCbkContext} for the given PSP.
     *
     * @param pspId the PSP to resolve
     * @return context ready for submission, or empty if the PSP is not eligible
     */
    public Optional<PspCbkContext> resolve(Long pspId) {
        Optional<Psp> optPsp = pspRepository.findById(pspId);
        if (optPsp.isEmpty()) {
            log.warn("CBK config resolve: PSP {} not found — skipping", pspId);
            return Optional.empty();
        }

        Psp psp = optPsp.get();

        if (!Boolean.TRUE.equals(psp.getCbkReportingEnabled())) {
            log.debug("CBK config resolve: PSP {} has cbkReportingEnabled=false — skipping", pspId);
            return Optional.empty();
        }

        String institutionCode = psp.getCbkInstitutionCode();
        if (institutionCode == null || institutionCode.isBlank()) {
            log.warn("CBK config resolve: PSP {} has cbkReportingEnabled=true but no institutionCode — skipping", pspId);
            return Optional.empty();
        }

        // Per-PSP credentials take precedence; fall back to global config.
        String clientId = hasValue(psp.getCbkClientId())
                ? psp.getCbkClientId()
                : cbkProperties.getClientId();
        String clientSecret = hasValue(psp.getCbkClientSecret())
                ? psp.getCbkClientSecret()
                : cbkProperties.getClientSecret();

        PspCbkContext context = new PspCbkContext(pspId, institutionCode, clientId, clientSecret);
        log.debug("CBK config resolve: PSP {} resolved — institutionCode={}", pspId, institutionCode);
        return Optional.of(context);
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
