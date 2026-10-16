package com.posgateway.aml.config.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Startup warning for the PEP data source (gap P0-2).
 *
 * <p>{@code sanctions.download.enabled} (env {@code SANCTIONS_DOWNLOAD_ENABLED}, default
 * {@code false} in every profile) gates {@code SanctionsListDownloadService}: the scheduled daily
 * OpenSanctions download, its ingest into the aml-microservice watchlist, and the staleness
 * watchdog. That ingest is the platform's only automated source of PEP classification — it sets
 * {@code pepLevel} (PEP / RCA) from the {@code role.pep} / {@code role.rca} topics — so with the
 * flag off, screening cannot flag a PEP that was not already ingested earlier. PEP flags entered
 * manually (e.g. on beneficial-owner records) are unaffected.
 *
 * <p>This check only logs; it does not change any default or block the boot. The separate
 * {@link SanctionsDataStartupCheck} handles the empty-watchlist fail-closed case.
 */
@Component
public class PepDataSourceStartupCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PepDataSourceStartupCheck.class);

    /** Logs a WARN when the PEP feed is disabled or misconfigured, else one INFO line. */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        boolean enabled = Boolean.parseBoolean(env.getProperty("sanctions.download.enabled", "false"));
        String url = env.getProperty("sanctions.opensanctions.url", "");
        Optional<String> warning = warning(enabled, url);
        if (warning.isPresent()) {
            log.warn(warning.get());
        } else {
            log.info("PEP data source enabled: OpenSanctions daily ingest classifies PEP / RCA entities.");
        }
    }

    /**
     * Builds the operator warning for the given configuration, or empty when the feed is on
     * and has a dataset URL.
     */
    static Optional<String> warning(boolean downloadEnabled, String datasetUrl) {
        if (!downloadEnabled) {
            return Optional.of("PEP DATA SOURCE DISABLED: sanctions.download.enabled=false "
                    + "(SANCTIONS_DOWNLOAD_ENABLED). The OpenSanctions daily ingest is the only automated "
                    + "source of PEP/RCA classification, so screening will not flag PEPs that were not "
                    + "ingested previously. To enable: SANCTIONS_DOWNLOAD_ENABLED=true plus "
                    + "SANCTIONS_OPENSANCTIONS_URL, AML_MICROSERVICE_BASE_URL and AML_MS_INTERNAL_KEY.");
        }
        if (datasetUrl == null || datasetUrl.isBlank()) {
            return Optional.of("PEP DATA SOURCE MISCONFIGURED: sanctions.download.enabled=true but "
                    + "sanctions.opensanctions.url (SANCTIONS_OPENSANCTIONS_URL) is blank; no PEP data "
                    + "will be downloaded.");
        }
        return Optional.empty();
    }
}
