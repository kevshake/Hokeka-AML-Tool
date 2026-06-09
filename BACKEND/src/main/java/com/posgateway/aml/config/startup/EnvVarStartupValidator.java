package com.posgateway.aml.config.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Validates required environment variables at startup, prints a clear status banner,
 * and writes a `.env.missing` stub at the working directory listing every required
 * env var that is unset or empty so operators can fill it in and restart.
 *
 * Triggered on ApplicationReadyEvent so the full Spring Environment is available.
 *
 * Behaviour:
 *   - OK         : env var resolved to a non-blank value
 *   - WARN       : resolved but to an insecure dev/placeholder fallback (in production this is a problem)
 *   - MISSING    : required for the active profile and resolved to blank — logged loudly, written to .env.missing
 *   - SKIP       : feature toggle disables this var (e.g. AEROSPIKE_PASSWORD when aerospike.enabled=false)
 *
 * The validator never fails the boot — the user explicitly asked for log-based prompts so they can
 * fill in missing values without the JVM crashing on a cryptic SQLException downstream.
 */
@Component
public class EnvVarStartupValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(EnvVarStartupValidator.class);
    private static final String BANNER = "============================================================";
    private static final String ENV_MISSING_FILENAME = ".env.missing";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String[] activeProfiles = env.getActiveProfiles();
        String profileLabel = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);

        List<EnvVarSpec> specs = registry();
        List<EvaluatedVar> results = new ArrayList<>(specs.size());
        for (EnvVarSpec spec : specs) {
            results.add(evaluate(spec, env));
        }

        crossCheckSanctionsWiring(results);

        printBanner(profileLabel, results);
        writeEnvMissingFile(results);
    }

    /**
     * Cross-check sanctions wiring: if {@code SANCTIONS_DOWNLOAD_ENABLED=true} but
     * {@code AML_MICROSERVICE_BASE_URL} is unset, the daily ingest job will silently
     * skip every batch. Promote that case to a WARN row in the banner.
     */
    private void crossCheckSanctionsWiring(List<EvaluatedVar> results) {
        boolean sanctionsOn = Boolean.parseBoolean(System.getenv("SANCTIONS_DOWNLOAD_ENABLED"));
        if (!sanctionsOn) return;
        String msUrl = System.getenv("AML_MICROSERVICE_BASE_URL");
        if (msUrl != null && !msUrl.isBlank()) return;
        for (int i = 0; i < results.size(); i++) {
            EvaluatedVar r = results.get(i);
            if ("AML_MICROSERVICE_BASE_URL".equals(r.spec.name())) {
                results.set(i, new EvaluatedVar(r.spec, Status.WARN,
                        "SANCTIONS_DOWNLOAD_ENABLED=true but no microservice URL — daily ingest will be skipped"));
                return;
            }
        }
    }

    /**
     * Registry of env vars the platform cares about. Add new entries here as we wire features.
     * Required-for-prod vars without a safe default belong here so missing values surface
     * before the first DB connection / external call fails.
     */
    private List<EnvVarSpec> registry() {
        Predicate<Environment> isProduction = e ->
                Arrays.asList(e.getActiveProfiles()).contains("production")
                        || Arrays.asList(e.getActiveProfiles()).contains("prod");
        // H2 profile uses an in-memory datasource configured in application-h2.properties,
        // so the external Postgres env vars don't apply.
        Predicate<Environment> externalDbRequired = e ->
                !Arrays.asList(e.getActiveProfiles()).contains("h2");
        Predicate<Environment> aerospikeEnabled = e ->
                Boolean.parseBoolean(e.getProperty("aerospike.enabled", "false"));
        Predicate<Environment> neo4jEnabled = e ->
                Boolean.parseBoolean(e.getProperty("neo4j.enabled", "false"));
        Predicate<Environment> kafkaEnabled = e ->
                Boolean.parseBoolean(e.getProperty("kafka.enabled", "true"));
        Predicate<Environment> sumsubEnabled = e ->
                Boolean.parseBoolean(e.getProperty("sumsub.enabled", "false"));
        Predicate<Environment> sanctionsEnabled = e ->
                Boolean.parseBoolean(e.getProperty("sanctions.download.enabled", "false"));
        Predicate<Environment> scoringEnabled = e ->
                Boolean.parseBoolean(e.getProperty("scoring.service.enabled", "true"));
        Predicate<Environment> aiRuleGeneratorEnabled = e ->
                Boolean.parseBoolean(e.getProperty("ai.rule-generator.enabled", "false"));
        // Regulator clients: required only when (a) we're on prod and (b) the
        // matching `regulators.<name>.enabled` flag is true. Disabled regulators
        // never block boot.
        Predicate<Environment> fincenRequired = e -> isProduction.test(e)
                && Boolean.parseBoolean(e.getProperty("regulators.fincen.enabled", "false"));
        Predicate<Environment> fcaRequired = e -> isProduction.test(e)
                && Boolean.parseBoolean(e.getProperty("regulators.fca.enabled", "false"));

        return List.of(
                // --- Core: required unless the in-memory h2 profile is active ---
                EnvVarSpec.requiredIf("DATABASE_URL", externalDbRequired,
                        "Postgres JDBC URL the app connects to (e.g. jdbc:postgresql://host:5432/db)"),
                EnvVarSpec.requiredIf("DATABASE_USERNAME", externalDbRequired,
                        "Postgres user"),
                EnvVarSpec.requiredIf("DATABASE_PASSWORD", externalDbRequired,
                        "Postgres password"),

                // --- Auth ---
                EnvVarSpec.requiredIf("JWT_SECRET", isProduction,
                        "HMAC signing secret for session JWTs. MUST be a long random string in production. " +
                                "Test profile has a hardcoded fallback labelled 'do-not-use-in-production'."),

                // --- Audit log integrity ---
                EnvVarSpec.requiredIf("AUDIT_HMAC_KEY", isProduction,
                        "HMAC signing key used by AuditLogService to checksum each audit row. " +
                                "MUST be a long random string in production — without it audit-log " +
                                "checksums are forgeable. AuditLogService refuses to boot on prod when blank."),

                // --- Password reset token pepper ---
                EnvVarSpec.requiredIf("AUTH_PASSWORD_RESET_PEPPER", isProduction,
                        "Pepper mixed into password-reset token hashes by PasswordResetService. " +
                                "MUST be a long random string in production — without it reset-token " +
                                "hashes are predictable. PasswordResetService refuses to boot on prod when blank."),

                // --- CORS / external access ---
                EnvVarSpec.requiredIf("CORS_ALLOWED_ORIGINS", isProduction,
                        "Comma-separated origin allowlist for production CORS. e.g. https://app.example.com"),

                // --- aml-microservice integration (Aerospike-backed transaction lookup lives there now) ---
                EnvVarSpec.recommended("AML_MICROSERVICE_BASE_URL",
                        "Base URL of the aml-microservice (e.g. http://aml-microservice:8080). Used by BACKEND " +
                                "to delegate transaction lookups and AML checks. If unset, integration falls " +
                                "back to a circuit-broken stub and live monitoring will degrade."),

                // --- Aerospike (only when enabled — Aerospike now lives in the microservice) ---
                EnvVarSpec.requiredWhen("AEROSPIKE_HOSTS", aerospikeEnabled,
                        "Aerospike seed hosts (host:port,host:port). Only required if aerospike.enabled=true. " +
                                "Aerospike is being migrated to the aml-microservice — BACKEND should not need this."),
                EnvVarSpec.requiredWhen("AEROSPIKE_PASSWORD", aerospikeEnabled,
                        "Aerospike password. Only required if aerospike.enabled=true."),

                // --- Neo4j (graph) ---
                EnvVarSpec.requiredWhen("NEO4J_URI", neo4jEnabled,
                        "Neo4j Bolt URI. Required if neo4j.enabled=true."),
                EnvVarSpec.requiredWhen("NEO4J_PASSWORD", neo4jEnabled,
                        "Neo4j password. Required if neo4j.enabled=true."),

                // --- Kafka ---
                EnvVarSpec.requiredWhen("KAFKA_BOOTSTRAP_SERVERS", kafkaEnabled,
                        "Kafka bootstrap servers. Required if kafka.enabled=true."),

                // --- KYC vendor (Sumsub) ---
                EnvVarSpec.requiredWhen("SUMSUB_API_KEY", sumsubEnabled,
                        "Sumsub API key for KYC verification. Required if sumsub.enabled=true."),
                EnvVarSpec.requiredWhen("SUMSUB_API_SECRET", sumsubEnabled,
                        "Sumsub API secret. Required if sumsub.enabled=true."),

                // --- Sanctions / OpenSanctions ---
                EnvVarSpec.recommended("SANCTIONS_DOWNLOAD_ENABLED",
                        "Toggles the daily OpenSanctions downloader. When TRUE, BACKEND POSTs ingested "
                                + "entities to ${AML_MICROSERVICE_URL}/internal/v1/sanctions/ingest, so "
                                + "AML_MICROSERVICE_BASE_URL and AML_MS_INTERNAL_KEY must also be set. "
                                + "If TRUE without AML_MICROSERVICE_BASE_URL the ingest will be skipped "
                                + "and sanctions data will go stale — see startup WARN."),
                EnvVarSpec.requiredWhen("SANCTIONS_OPENSANCTIONS_URL", sanctionsEnabled,
                        "OpenSanctions dataset URL. Required if sanctions.download.enabled=true."),

                // --- ML scoring sidecar ---
                EnvVarSpec.requiredWhen("SCORING_SERVICE_URL", scoringEnabled,
                        "URL of the XGBoost scoring sidecar. Required if scoring.service.enabled=true."),

                // --- Notifications (email/SMTP) ---
                EnvVarSpec.recommended("MAIL_HOST",
                        "SMTP host for outbound notifications (password reset, alerts). " +
                                "If unset, NotificationService falls back to log-only mode and emails are NOT sent."),
                EnvVarSpec.recommended("MAIL_USERNAME",
                        "SMTP username. Goes with MAIL_HOST."),
                EnvVarSpec.recommended("MAIL_PASSWORD",
                        "SMTP password. Goes with MAIL_HOST."),

                // --- AI Rule Generator (Anthropic Claude) ---
                EnvVarSpec.requiredWhen("ANTHROPIC_API_KEY", aiRuleGeneratorEnabled,
                        "Anthropic API key for the AI rule generator. " +
                                "Required when ai.rule-generator.enabled=true. " +
                                "Without it, POST /api/v1/rules/generate returns 503."),

                // --- Regulator submission clients (FinCEN / FCA) ---
                // Required only when the regulator is enabled AND we're on prod, so dev/test
                // deploys are not forced to provide credentials they don't have yet.
                EnvVarSpec.requiredIf("REGULATORS_FINCEN_ENDPOINT", fincenRequired,
                        "FinCEN BSA E-Filing endpoint URL. Required when regulators.fincen.enabled=true on prod. " +
                                "Default is intentionally blank — no placeholder host."),
                EnvVarSpec.requiredIf("REGULATORS_FINCEN_KEYSTORE_PATH", fincenRequired,
                        "Path to the .p12 client keystore used for mutual TLS to FinCEN. " +
                                "Required when regulators.fincen.enabled=true on prod."),
                EnvVarSpec.requiredIf("REGULATORS_FINCEN_KEYSTORE_PASSWORD", fincenRequired,
                        "Password for the FinCEN client keystore. " +
                                "Required when regulators.fincen.enabled=true on prod."),
                EnvVarSpec.requiredIf("REGULATORS_FCA_ENDPOINT", fcaRequired,
                        "FCA / NCA SAR Online endpoint URL. Required when regulators.fca.enabled=true on prod."),
                EnvVarSpec.requiredIf("REGULATORS_FCA_API_KEY", fcaRequired,
                        "API key for the FCA submission endpoint. " +
                                "Required when regulators.fca.enabled=true on prod."),
                EnvVarSpec.requiredIf("REGULATORS_FCA_HMAC_SECRET", fcaRequired,
                        "HMAC-SHA256 secret used to sign FCA request bodies (X-Signature header). " +
                                "Required when regulators.fca.enabled=true on prod.")
        );
    }

    private EvaluatedVar evaluate(EnvVarSpec spec, Environment env) {
        String value = System.getenv(spec.name());
        if (value != null && !value.isBlank()) {
            return new EvaluatedVar(spec, Status.OK, mask(spec, value));
        }
        if (!spec.isRequiredForCurrentEnv(env)) {
            return new EvaluatedVar(spec, Status.SKIP, "feature disabled");
        }
        return new EvaluatedVar(spec, Status.MISSING, "");
    }

    private String mask(EnvVarSpec spec, String value) {
        if (spec.isSecret()) {
            if (value.length() <= 4) return "****";
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }
        if (value.length() > 60) {
            return value.substring(0, 57) + "...";
        }
        return value;
    }

    private void printBanner(String profileLabel, List<EvaluatedVar> results) {
        int missing = (int) results.stream().filter(r -> r.status == Status.MISSING).count();
        int warn = (int) results.stream().filter(r -> r.status == Status.WARN).count();
        int ok = (int) results.stream().filter(r -> r.status == Status.OK).count();
        int skip = (int) results.stream().filter(r -> r.status == Status.SKIP).count();

        StringBuilder sb = new StringBuilder("\n");
        sb.append(BANNER).append('\n');
        sb.append("  STARTUP ENV-VAR CHECK  (profile: ").append(profileLabel).append(")\n");
        sb.append(BANNER).append('\n');
        for (EvaluatedVar r : results) {
            sb.append(String.format("  [%-7s] %-32s %s%n",
                    r.status.name(),
                    r.spec.name(),
                    r.status == Status.MISSING ? "<-- required, set this and restart" : r.displayValue));
        }
        sb.append(BANNER).append('\n');
        sb.append(String.format("  RESULT: %d ok, %d warn, %d skipped, %d MISSING%n", ok, warn, skip, missing));
        if (missing > 0) {
            sb.append(BANNER).append('\n');
            sb.append("  Missing variables also written to ").append(ENV_MISSING_FILENAME).append('\n');
            sb.append("  Fill in values, then either:\n");
            sb.append("    bash:        export $(grep -v '^#' ").append(ENV_MISSING_FILENAME).append(" | xargs)\n");
            sb.append("    powershell:  Get-Content ").append(ENV_MISSING_FILENAME)
                    .append(" | Where-Object {$_ -notmatch '^#'} | ForEach-Object { $kv = $_ -split '=', 2; ")
                    .append("[Environment]::SetEnvironmentVariable($kv[0], $kv[1], 'Process') }\n");
            sb.append("    docker:      pass via --env-file ").append(ENV_MISSING_FILENAME).append('\n');
        }
        sb.append(BANNER);

        if (missing > 0) {
            log.error(sb.toString());
        } else if (warn > 0) {
            log.warn(sb.toString());
        } else {
            log.info(sb.toString());
        }
    }

    private void writeEnvMissingFile(List<EvaluatedVar> results) {
        List<EvaluatedVar> missing = results.stream().filter(r -> r.status == Status.MISSING).toList();
        if (missing.isEmpty()) {
            // Clean up stale file from a previous failed boot so its presence isn't misleading
            try {
                Path p = Paths.get(ENV_MISSING_FILENAME);
                if (Files.exists(p)) Files.delete(p);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by EnvVarStartupValidator at ").append(Instant.now()).append('\n');
        sb.append("# Fill in the values below, then load them into your shell or pass via --env-file.\n");
        sb.append("# This file is gitignored.\n\n");
        for (EvaluatedVar r : missing) {
            sb.append("# ").append(r.spec.description()).append('\n');
            sb.append(r.spec.name()).append("=\n\n");
        }
        try {
            Files.writeString(Paths.get(ENV_MISSING_FILENAME), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write {}: {}", ENV_MISSING_FILENAME, e.getMessage());
        }
    }

    private enum Status { OK, WARN, MISSING, SKIP }

    private record EvaluatedVar(EnvVarSpec spec, Status status, String displayValue) {}
}
