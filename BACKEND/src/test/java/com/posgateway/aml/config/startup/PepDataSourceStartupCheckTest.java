package com.posgateway.aml.config.startup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The PEP data-source startup warning reflects what sanctions.download.enabled gates. */
class PepDataSourceStartupCheckTest {

    @Test
    void warnsWhenDownloadDisabled() {
        assertThat(PepDataSourceStartupCheck.warning(false, "https://example.test/entities.json"))
                .hasValueSatisfying(msg -> assertThat(msg)
                        .contains("PEP DATA SOURCE DISABLED")
                        .contains("sanctions.download.enabled=false")
                        .contains("SANCTIONS_DOWNLOAD_ENABLED=true"));
    }

    @Test
    void warnsWhenEnabledWithoutDatasetUrl() {
        assertThat(PepDataSourceStartupCheck.warning(true, " "))
                .hasValueSatisfying(msg -> assertThat(msg).contains("MISCONFIGURED"));
    }

    @Test
    void silentWhenEnabledAndConfigured() {
        assertThat(PepDataSourceStartupCheck.warning(true, "https://example.test/entities.json")).isEmpty();
    }
}
