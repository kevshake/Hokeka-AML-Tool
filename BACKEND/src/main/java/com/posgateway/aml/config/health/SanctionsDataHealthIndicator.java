package com.posgateway.aml.config.health;

import com.posgateway.aml.client.aml.SanctionsCountClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for the sanctions watchlist (fix for W38-2).
 *
 * <p>The sanctions dataset is loaded into the {@code aml-microservice} Aerospike set by
 * {@code SanctionsListDownloadService}, which is <b>disabled by default</b>
 * ({@code sanctions.download.enabled=false}). If a deployment forgets to enable it (and set
 * {@code sanctions.opensanctions.url}), the watchlist stays empty and every sanctions screen
 * silently returns "no match" — the worst-case AML failure because it is invisible.
 *
 * <p>This indicator exposes the live watchlist record count on {@code /actuator/health} so
 * monitoring/alerting can detect an empty or unavailable watchlist:
 * <ul>
 *   <li>{@code count > 0}  → UP   (screening is backed by real data)</li>
 *   <li>{@code count == 0} → DOWN (EMPTY — screening is non-functional)</li>
 *   <li>{@code count < 0}  → DOWN (microservice unavailable — screening degraded)</li>
 * </ul>
 * Read-only: it never changes screening behaviour, only makes the data state observable.
 */
@Component("sanctionsData")
public class SanctionsDataHealthIndicator implements HealthIndicator {

    private final SanctionsCountClient sanctionsCountClient;

    public SanctionsDataHealthIndicator(SanctionsCountClient sanctionsCountClient) {
        this.sanctionsCountClient = sanctionsCountClient;
    }

    @Override
    public Health health() {
        long count = sanctionsCountClient.getCount();
        if (count > 0) {
            return Health.up()
                    .withDetail("watchlistRecords", count)
                    .withDetail("status", "loaded")
                    .build();
        }
        if (count == 0) {
            return Health.down()
                    .withDetail("watchlistRecords", 0L)
                    .withDetail("status", "EMPTY")
                    .withDetail("reason",
                            "Sanctions watchlist is empty — all screening will return no match. "
                                    + "Enable sanctions.download.enabled=true and configure "
                                    + "sanctions.opensanctions.url, then re-run the download.")
                    .build();
        }
        // count < 0 -> microservice / Aerospike unavailable
        return Health.down()
                .withDetail("watchlistRecords", -1L)
                .withDetail("status", "UNAVAILABLE")
                .withDetail("reason", "Could not read sanctions count from aml-microservice; "
                        + "screening data status is unknown.")
                .build();
    }
}
