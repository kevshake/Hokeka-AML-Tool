# Deployment readiness runbook (Hostinger VPS + E2E verification)

This runbook closes the deployment-readiness checklist for the Hokeka AML control plane, Console,
Edge nodes, and correlated compliance data. Canonical install facts remain in
[`docs/INSTALL.md`](../INSTALL.md); this document is the **verification and ops** supplement.

## 1. Preconditions

| Item | Check |
|------|--------|
| VPS | Docker Engine + Compose v2, ports 80/443, `/opt/aml-fraud-detector` — or run `sudo ./scripts/install/hokeka-install.sh --role control-plane --yes` on a fresh Linux host ([`docs/install/07-first-run-bootstrap.md`](../install/07-first-run-bootstrap.md)) |
| Secrets | `.env` per `docs/INSTALL.md` §1.3 (`DATABASE_*`, `NEO4J_PASSWORD`, `JWT_SECRET`, …) |
| Flyway | Latest migration on main (currently `V245__*` — confirm with `ls BACKEND/src/main/resources/db/migration/V*.sql \| sort -V \| tail -1`) |
| AML internal key | `AML_MS_INTERNAL_KEY` identical on `backend-prod` and `aml-ms-prod` |
| Sanctions (prod) | `sanctions.download.enabled=true` and `sanctions.opensanctions.url` set |

## 2. Fresh database and upgrade path

**Fresh install (CI or staging):**

```bash
cd BACKEND
mvn -q test -Dtest=FlywayFreshDatabaseIntegrationTest
```

Requires Docker (Testcontainers Postgres). Applies the full `db/migration` history on an empty DB.

**Upgrade path:** deploy new backend image; Flyway runs on boot. Never edit applied migrations; add
the next sequential `V###__*.sql` only.

## 3. Control plane health

```bash
curl -sf http://127.0.0.1:2637/api/v1/actuator/health | jq .
docker compose -f docker-compose.prod.yml ps
```

Expect Postgres, Redis, Kafka, Neo4j, aml-ms, backend, frontend containers healthy per compose
healthchecks.

## 4. Decision orchestrator runtime toggle

Property default: `hokeka.decision.orchestrator=shadow` (`legacy` | `shadow` | `live`).

**Runtime (no restart):** platform operator with `CONFIGURE_SYSTEM`:

```bash
# Read
curl -s -b cookies.txt http://127.0.0.1:2637/api/v1/admin/decision-orchestrator

# shadow → live → shadow soak
curl -s -X PUT -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"mode":"live"}' http://127.0.0.1:2637/api/v1/admin/decision-orchestrator
```

Automated proof:

```bash
node scripts/live-decision-orchestrator-toggle-smoke.mjs
```

## 5. Edge runtime

```bash
chmod +x scripts/edge-runtime-smoke.sh
./scripts/edge-runtime-smoke.sh
```

On PSP premises: install via `edge-host/deploy/install.sh`, confirm rule bundle pull, `/edge/decision`,
metrics upload, and Console **Edge Nodes** page (`MANAGE_PSP` / `PSP_SETTINGS_VIEW`). Dual-post contract:
[`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](../EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

Rust toolchain is pinned in `edge-engine/rust-toolchain.toml` (minimum 1.85 for edition2024 deps).

## 6. Console role matrix (live Playwright)

Against real Console + API (not `VITE_DEV_MOCK_API`):

```bash
cd FRONTEND && npm ci && npx playwright install chromium
npm run dev &   # or production nginx URL
node ../scripts/live-console-role-matrix.mjs
```

Personas: `docs/CONSOLE_ROLE_MATRIX.md`. Demo passwords: `V127__seed_demo_accounts.sql` header
(default `Hokeka2026!`). Script provisions `velocity.mlro`, `velocity.investigator`, etc. when missing.

## 7. Kafka and Neo4j

Infra: `infra/docker-compose.infrastructure.yml` or `docker-compose.prod.yml` services.

Automated container smoke (Docker required; CI fails if these skip while Docker is present):

```bash
cd BACKEND
mvn -q test -Dtest=KafkaNeo4jContainerSmokeTest,ConsoleListKeysetBenchmarkIT
node ../scripts/assert-docker-integration-tests.mjs target/surefire-reports
```

Console list keyset benchmark prints `BENCHMARK alerts|transactions|audit` lines (counts + timings) to
Surefire stdout for regression tracking.

## 7b. Production Docker images

CI builds every service declared in `docker-compose.prod.yml`:

```bash
docker compose -f docker-compose.prod.yml build
```

The AML microservice image is multi-stage (`aml-microservice/Dockerfile`): it runs `mvn install` on
`aeroorm-java` and `mvn package` on `aml-microservice` inside the build — no pre-built JAR on the host
is required. The Console image uses repository root context so `design-tokens/` is available to Vite.

## 8. OpenSanctions

Production: enable downloader + URL (see `SanctionsListDownloadService` / `EnvVarStartupValidator`).

`sanctions.download.enabled` (`SANCTIONS_DOWNLOAD_ENABLED`, default **false** in every profile, including
production) gates the scheduled daily OpenSanctions download, its ingest into the aml-microservice
watchlist and the staleness watchdog. That ingest is the **only automated source of PEP/RCA
classification**, so with it off screening cannot flag PEPs that were not ingested earlier.
`PepDataSourceStartupCheck` logs `PEP DATA SOURCE DISABLED` at startup in that case (WARN only — the
default is not changed and boot is not blocked). Enabling requires `SANCTIONS_DOWNLOAD_ENABLED=true`,
`SANCTIONS_OPENSANCTIONS_URL`, `AML_MICROSERVICE_BASE_URL` and `AML_MS_INTERNAL_KEY`.

Sample parse benchmark (no network):

```bash
cd BACKEND
mvn -q test -Dtest=OpenSanctionsSampleLoadTest
```

Full dataset load: run manual download on staging, monitor heap and ingest duration in logs; PEP
classification uses `role.pep` / `role.rca` topics at ingest.

## 9. Full compliance chain smoke

```bash
node scripts/live-api-closure-smoke.mjs
node scripts/live-full-compliance-chain-smoke.mjs
```

Covers ingest → screening → linked records → SAR draft → MLRO approval when `velocity.mlro` exists.

## 10. Hostinger release checklist

1. Rotate placeholder secrets from bootstrap scripts before first public traffic.
2. Point DNS; host nginx TLS terminates to `127.0.0.1:2637` / `:8088`.
3. Set `HOSTINGER_PACKAGES_DIR` and release signing per `BACKEND/DEPLOYMENT.md` supplement.
4. Run sections 3–9 on staging, then production cutover.
5. Keep `hokeka.decision.orchestrator=shadow` until shadow disagreement metrics are clean, then
   `live` via runtime API or env.

## Evidence artifacts

Store CI Surefire reports, Playwright logs, and smoke script output under your ops ticket. Cloud Agent
runs attach the same scripts in-repo for reproducibility.
