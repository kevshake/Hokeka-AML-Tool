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
 *   <li>count unavailable (microservice not up yet at boot, count &lt; 0) → WARN only (transient;
 *       the {@code sanctionsData} health indicator continues to report it at runtime).</li>
 * </ul>
 *
 * <p>Fail-closed is intentionally gated to {@code production} + a flag so dev/test (where the
 * microservice may not be running) still boot; the {@link ApplicationReadyEvent} fires after the
 * context is up, so aborting here does not corrupt partial state.
 */
@Component
public class SanctionsDataStartupCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SanctionsDataStartupCheck.class);

    private final SanctionsCountClient sanctionsCountClient;

    public SanctionsDataStartupCheck(SanctionsCountClient sanctionsCountClient) {
        this.sanctionsCountClient = sanctionsCountClient;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        boolean isProduction = Arrays.asList(env.getActiveProfiles()).contains("production");
        // Fail-closed by default under production; anywhere else, alarm-only.
        boolean failClosedOnEmpty = Boolean.parseBoolean(
                env.getProperty("sanctions.startup.fail-closed-on-empty", String.valueOf(isProduction)));

        long count;
        try {
            count = sanctionsCountClient.getCount();
        } catch (RuntimeException e) {
            log.warn("Sanctions watchlist startup check could not read the count: {} — "
                    + "runtime /actuator/health (sanctionsData) will continue to report status.",
                    e.getMessage());
            return;
        }

        if (count > 0) {
            log.info("Sanctions watchlist OK: {} records loaded in the aml-microservice.", count);
            return;
        }

        if (count < 0) {
            // Microservice/Aerospike not reachable at boot — likely a startup ordering race, not
            // necessarily an empty list. Warn but don't abort; the health indicator covers runtime.
            log.warn("Sanctions watchlist count UNAVAILABLE at startup (aml-microservice not reachable). "
                    + "Cannot confirm the watchlist is loaded — monitor /actuator/health (sanctionsData).");
            return;
        }

        // count == 0 : microservice is up but the watchlist is EMPTY -> screening is non-functional.
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
