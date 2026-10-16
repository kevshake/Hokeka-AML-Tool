package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.service.edge.EdgeBundleDistributionService;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Production policy beans for {@link EdgeTunnelConfiguration}: the existing edge-channel
 * authorisation, the {@code psps.support_access_enabled} policy, the §6.1 default tunnel settings
 * and the rule-bundle version. Work package R3 replaces the settings provider with its config
 * document by declaring a {@code @Primary} {@link TunnelPspSettingsProvider}.
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

    /** §6.1 defaults until R3's stored config documents exist. */
    @Bean
    public TunnelPspSettingsProvider contractDefaultTunnelSettings() {
        return new ContractDefaultTunnelSettings();
    }

    /** Rule bundle version from the distribution service (the value {@code /edge/bundle} serves). */
    @Bean
    public TunnelBundleVersionSource tunnelBundleVersionSource(EdgeBundleDistributionService distribution) {
        return distribution::currentVersion;
    }
}
