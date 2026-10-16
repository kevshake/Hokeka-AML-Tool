package com.posgateway.aml.config.startup;

import com.posgateway.aml.client.aml.SanctionsCountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Boot-time safeguard for the sanctions watchlist (fix for W38-2).
 *
 * <p>The sanctions dataset download ({@code SanctionsListDownloadService}) is disabled by default
 * ({@code sanctions.download.enabled=false}). If a deployment never enables it, the Aerospike
 * sanctions set stays empty and <b>every sanctions screen silently passes</b> — an invisible,
 * worst-case AML failure. This listener makes that condition loud at startup instead of silent:
 *
 * <ul>
 *   <li>watchlist has records → single INFO line.</li>
 *   <li>watchlist EMPTY (count 0, microservice reachable) → a loud multi-line ERROR banner; and
 *       under the {@code production} profile, if {@code sanctions.startup.fail-closed-on-empty=true}
 *       (default {@code true} in prod), the boot is aborted so an operator cannot unknowingly run
 *       an AML platform with no sanctions data.</li>
 *   <li>count unavailable (count &lt; 0) → the check retries for
 *       {@code sanctions.startup.count-wait-seconds} (default 60 s under {@code production}, 0
 *       elsewhere) to absorb container start-up ordering. If it is still unknown, under
 *       {@code production} with {@code sanctions.startup.fail-closed-on-unavailable=true} (the default
 *       there) boot is aborted: an AML platform that cannot confirm it has sanctions data must not
 *       start serving. Elsewhere it logs a WARN and continues.</li>
 * </ul>
 *
 * <p>The outcome is therefore deterministic for a given configuration and microservice state; it no
 * longer depends on whether a single 400 ms probe happened to win a race.
 *
 * <p>Fail-closed is intentionally gated to {@code production} + a flag so dev/test (where the
 * microservice may not be running) still boot; the {@link ApplicationReadyEvent} fires after the
 * context is up, so aborting here does not corrupt partial state.
 */
@Component
public class SanctionsDataStartupCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SanctionsDataStartupCheck.class);

    /** Delay between count attempts while waiting for the microservice. */
    static final long RETRY_INTERVAL_MS = 2000L;

    private final SanctionsCountClient sanctionsCountClient;
    /** Time source (replaced in tests together with {@link #sleeper}). */
    java.util.function.LongSupplier clock = System::currentTimeMillis;
    /** Sleep hook (replaced in tests so retries do not slow the suite). */
    java.util.function.LongConsumer sleeper = ms -> {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    };

    private final org.springframework.beans.factory.ObjectProvider<
            com.posgateway.aml.service.download.SanctionsListDownloadService> downloadServiceProvider;

    public SanctionsDataStartupCheck(SanctionsCountClient sanctionsCountClient,
                                     org.springframework.beans.factory.ObjectProvider<
                                             com.posgateway.aml.service.download.SanctionsListDownloadService>
                                             downloadServiceProvider) {
        this.sanctionsCountClient = sanctionsCountClient;
        this.downloadServiceProvider = downloadServiceProvider;
    }

    /**
     * First-deploy seeding: when the watchlist is empty and the OpenSanctions download is enabled,
     * runs the download once synchronously (the scheduled job would only run at 02:00, after the
     * fail-closed check had already refused to boot) and re-reads the count.
     *
     * @return the count after seeding, or 0 when seeding is not possible or did not load anything
     */
    long seedEmptyWatchlist(boolean seedOnEmpty) {
        if (!seedOnEmpty || downloadServiceProvider == null) {
            return 0L;
        }
        com.posgateway.aml.service.download.SanctionsListDownloadService download =
                downloadServiceProvider.getIfAvailable();
        if (download == null || !download.isDownloadEnabled()) {
            return 0L;
        }
        log.warn("Sanctions watchlist is EMPTY at startup and sanctions.download.enabled=true: running the "
                + "initial OpenSanctions download now (first deploy). This can take several minutes.");
        try {
            download.manualDownload();
        } catch (RuntimeException e) {
            log.error("Initial sanctions download failed: {}", e.getMessage());
            return 0L;
        }
        long after = readCountWithRetry(0);
        return Math.max(after, 0L);
    }

    /**
     * Reads the count, retrying while it is unavailable (-1) until {@code waitSeconds} elapse.
     *
     * @return the first non-negative count, or -1 if none was obtained in time
     */
    long readCountWithRetry(long waitSeconds) {
        long deadline = clock.getAsLong() + Math.max(0L, waitSeconds) * 1000L;
        int attempt = 0;
        while (true) {
            attempt++;
            long count;
            try {
                count = sanctionsCountClient.getCount();
            } catch (RuntimeException e) {
                log.warn("Sanctions count attempt {} failed: {}", attempt, e.getMessage());
                count = -1L;
            }
            if (count >= 0 || clock.getAsLong() >= deadline || Thread.currentThread().isInterrupted()) {
                return count;
            }
            log.info("Sanctions watchlist count not available yet (attempt {}); retrying in {} ms",
                    attempt, RETRY_INTERVAL_MS);
            sleeper.accept(RETRY_INTERVAL_MS);
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        boolean isProduction = Arrays.asList(env.getActiveProfiles()).contains("production");
        // Fail-closed by default under production; anywhere else, alarm-only.
        boolean failClosedOnEmpty = Boolean.parseBoolean(
                env.getProperty("sanctions.startup.fail-closed-on-empty", String.valueOf(isProduction)));

        long waitSeconds = Long.parseLong(env.getProperty("sanctions.startup.count-wait-seconds",
                isProduction ? "60" : "0"));
        boolean failClosedOnUnavailable = Boolean.parseBoolean(env.getProperty(
                "sanctions.startup.fail-closed-on-unavailable", String.valueOf(isProduction)));

        long count = readCountWithRetry(waitSeconds);

        if (count > 0) {
            log.info("Sanctions watchlist OK: {} records loaded in the aml-microservice.", count);
            return;
        }

        if (count < 0) {
            String msg = "Sanctions watchlist count UNAVAILABLE after waiting " + waitSeconds + "s "
                    + "(aml-microservice unreachable, Aerospike down, or internal key mismatch).";
            if (failClosedOnUnavailable) {
                throw new IllegalStateException("FAIL-CLOSED: " + msg + " Refusing to start without confirming "
                        + "sanctions data. Check aml-ms health and AML_MS_INTERNAL_KEY, or set "
                        + "sanctions.startup.fail-closed-on-unavailable=false (NOT recommended in production).");
            }
            log.warn("{} Continuing; monitor /actuator/health (sanctionsData).", msg);
            return;
        }

        // count == 0 : microservice is up but the watchlist is EMPTY. Try first-deploy seeding.
        boolean seedOnEmpty = Boolean.parseBoolean(env.getProperty("sanctions.startup.seed-on-empty", "true"));
        long seeded = seedEmptyWatchlist(seedOnEmpty);
        if (seeded > 0) {
            log.info("Sanctions watchlist seeded at startup: {} records loaded in the aml-microservice.", seeded);
            return;
        }

        // Still EMPTY -> screening is non-functional.
        String banner = "\n"
                + "============================================================================\n"
                + "  🚨 SANCTIONS WATCHLIST IS EMPTY (0 records) — SANCTIONS SCREENING IS DEAD  \n"
                + "============================================================================\n"
                + "  Every sanctions screen will return NO MATCH. This silently disables a\n"
                + "  core AML control. Cause: the OpenSanctions download is not populating the\n"
                + "  aml-microservice Aerospike set.\n"
                + "  FIX: set  sanctions.download.enabled=true  AND  sanctions.opensanctions.url=<url>\n"
                + "       (plus the microservice internal-auth key), then trigger/await the daily\n"
                + "       download. Verify via GET /actuator/health (sanctionsData) -> UP.\n"
                + "============================================================================";
        log.error(banner);

        if (failClosedOnEmpty) {
            throw new IllegalStateException(
                    "FAIL-CLOSED: sanctions watchlist is empty — refusing to run AML screening with no "
                            + "sanctions data. Load the watchlist (sanctions.download.enabled=true + "
                            + "sanctions.opensanctions.url), or set sanctions.startup.fail-closed-on-empty=false "
                            + "to boot anyway (NOT recommended in production).");
        }
        log.error("Continuing startup with an EMPTY sanctions watchlist "
                + "(sanctions.startup.fail-closed-on-empty=false). Sanctions screening is NON-FUNCTIONAL.");
    }
}
