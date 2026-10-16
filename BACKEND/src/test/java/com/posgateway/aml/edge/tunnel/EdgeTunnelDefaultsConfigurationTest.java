package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.service.edge.EdgeBundleDistributionService;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.residency.EdgeTunnelSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Production policy wiring: the tunnel's settings provider is R3's config document
 * ({@link ResidencyTunnelPspSettings}, {@code @Primary}); the old fixed-defaults provider is gone.
 */
class EdgeTunnelDefaultsConfigurationTest {

    /** Context with the defaults configuration and test doubles for its collaborators. */
    private ApplicationContextRunner runner(EdgeTunnelSettingsService settings) {
        return new ApplicationContextRunner()
                .withBean(EdgeEnrollmentService.class, () -> mock(EdgeEnrollmentService.class))
                .withBean(EdgeBundleDistributionService.class, () -> mock(EdgeBundleDistributionService.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(EdgeTunnelSettingsService.class, () -> settings)
                .withUserConfiguration(EdgeTunnelDefaultsConfiguration.class);
    }

    /** Verifies: the only (and primary) provider reads R3's per-PSP settings. */
    @Test
    void settingsProviderIsTheResidencyConfigDocument() {
        EdgeTunnelSettingsService settings = mock(EdgeTunnelSettingsService.class);
        when(settings.settingsFor(5L)).thenReturn(new EdgeTunnelSettingsService.TunnelSettings(
                true, List.of("txn.get"), false, 2, 9L));
        runner(settings).run(ctx -> {
            assertThat(ctx).hasSingleBean(TunnelPspSettingsProvider.class);
            TunnelPspSettingsProvider provider = ctx.getBean(TunnelPspSettingsProvider.class);
            assertThat(provider).isInstanceOf(ResidencyTunnelPspSettings.class);
            assertThat(provider.settings(5L)).isEqualTo(
                    new TunnelPspSettingsProvider.TunnelPspSettings(true, Set.of("txn.get"), false, 2, 9L));
        });
    }

    /** Verifies: with another provider bean on the context, the residency one still wins. */
    @Test
    void residencyProviderIsPrimary() {
        EdgeTunnelSettingsService settings = mock(EdgeTunnelSettingsService.class);
        TunnelPspSettingsProvider other = pspId -> new TunnelPspSettingsProvider.TunnelPspSettings(
                true, Set.of(), false, 1, 1L);
        runner(settings).withBean("otherTunnelSettings", TunnelPspSettingsProvider.class, () -> other)
                .run(ctx -> assertThat(ctx.getBean(TunnelPspSettingsProvider.class))
                        .isInstanceOf(ResidencyTunnelPspSettings.class));
    }

    /** Verifies: with the tunnel switched off no tunnel policy beans exist. */
    @Test
    void disabledTunnelRegistersNoPolicyBeans() {
        runner(mock(EdgeTunnelSettingsService.class)).withPropertyValues("edge.tunnel.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(TunnelPspSettingsProvider.class));
    }
}
