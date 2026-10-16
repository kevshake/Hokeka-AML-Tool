package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.service.edge.EdgeBundleDistributionService;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.residency.EdgeTunnelSettingsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Production policy beans for {@link EdgeTunnelConfiguration}: the existing edge-channel
 * authorisation, the {@code psps.support_access_enabled} policy, the per-PSP tunnel settings from
 * R3's sealed Edge config document, and the rule-bundle version.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "edge.tunnel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EdgeTunnelDefaultsConfiguration {

    /** Same gate as {@code /edge/bundle}: {@link EdgeEnrollmentService#findAuthorized}. */
    @Bean
    public TunnelNodeAuthorizer tunnelNodeAuthorizer(EdgeEnrollmentService enrollmentService) {
        return enrollmentService::findAuthorized;
    }

    /** §9 cloud gate over {@code psps.support_access_enabled}. */
    @Bean
    public SupportAccessPolicy supportAccessPolicy(JdbcTemplate jdbcTemplate) {
        return new JdbcSupportAccessPolicy(jdbcTemplate);
    }

    /**
     * Per-PSP {@code tunnel} section of the Edge config document (R3); a PSP without stored edge
     * settings gets the §6.1 defaults from the document builder. {@code @Primary} so it wins over
     * any other provider bean on the context.
     */
    @Bean
    @Primary
    public TunnelPspSettingsProvider residencyTunnelPspSettings(EdgeTunnelSettingsService settingsService) {
        return new ResidencyTunnelPspSettings(settingsService);
    }

    /** Rule bundle version from the distribution service (the value {@code /edge/bundle} serves). */
    @Bean
    public TunnelBundleVersionSource tunnelBundleVersionSource(EdgeBundleDistributionService distribution) {
        return distribution::currentVersion;
    }
}
