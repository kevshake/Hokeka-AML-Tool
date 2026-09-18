# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This is a multi-module AML / fraud-detection platform. Three deployable units live side-by-side at the repo root:

- `BACKEND/` — Main Spring Boot 3.2 / Java 17 application (`com.posgateway:aml-fraud-detector`). The bulk of business logic, REST controllers, persistence, rules engine, ML, KYC, sanctions screening, case management, and reporting all live here. Default port `2637`.
- `aml-microservice/` — Smaller standalone Spring Boot service (`com.hokeka:aml-microservice`) focused on Aerospike-backed AML lookups. Independent pom; deploys separately.
- `FRONTEND/` — React 18 + TypeScript + Vite + MUI SPA. Dev server on `5173`, proxies `/api/v1` to the BACKEND on `2637` (override with `VITE_PROXY_TARGET`).
- `infra/`, `docker-compose.prod.yml`, `docker-compose.test.yml`, `BACKEND/docker-compose.yml` — Compose files for Postgres, Aerospike, Neo4j, Kafka, nginx, etc.
- `docs/` — Architecture, SDLC, security, integrations, chargeback, performance docs (most subdirs are sparse — read before assuming anything is canonical).
- `website/` — Marketing site, separate from the app.

## Common commands

### BACKEND (Maven, run from `BACKEND/`)
- Build: `mvn clean package`
- Run app (dev profile): `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (or use `start-dev.sh`)
- Run all tests: `mvn test`
- Run a single test class: `mvn test -Dtest=ClassName`
- Run a single test method: `mvn test -Dtest=ClassName#methodName`
- Flyway migrate (uses creds baked into pom): `mvn flyway:migrate`
- Profiles available via `application-{profile}.properties`: `dev`, `prod`, `production`, `testenv`. **`production` ≠ `testenv`** — `testenv` uses `ddl-auto=create-drop` and is unsafe for external access (see `BACKEND/DEPLOYMENT.md`).

### aml-microservice (run from `aml-microservice/`)
- Build / run / test: same Maven commands as BACKEND. Configured via `src/main/resources/application.yml`.

### FRONTEND (npm, run from `FRONTEND/`)
- Dev server: `npm run dev`
- Production build: `npm run build` (runs `tsc` then `vite build`) or `npm run build:prod` (skips `tsc`)
- Type check only: `npm run typecheck`
- Lint: `npm run lint` (zero-warning policy: `--max-warnings 0`)

### Docker
- Full prod stack: `docker-compose -f docker-compose.prod.yml up`
- Test stack: `docker-compose -f docker-compose.test.yml up`
- Infrastructure only (DBs, Kafka, etc.): `docker-compose -f infra/docker-compose.infrastructure.yml up`

## Architecture notes

### BACKEND package structure
Root package `com.posgateway.aml`, organized by layer then by domain:
- `controller/<domain>` — REST endpoints. Domains include `alert`, `aml`, `analytics`, `auth`, `case_management`, `chargeback`, `compliance`, `crypto`, `document`, `fatf`, `limits`, `monitoring`, `network`, `notification`, `psp`, `reporting`, `risk`, `rules`, `search`, `vasp`, `admin`.
- `entity/<domain>`, `dto/<domain>`, `service/`, `repository/` follow the same domain split.
- `config/` (incl. `config/security/`) — Spring config, security, CORS, rate limiting, filters.
- `aspect/` — AOP cross-cutting (auditing, etc.).

### Data & external systems
The BACKEND is a heavy polyglot-persistence app. Expect simultaneous use of:
- **PostgreSQL** (primary OLTP, JPA + Hibernate + **Hibernate Envers** for entity auditing).
- **Flyway** migrations under `BACKEND/src/main/resources/db/migration/V*.sql` — versions are well past V100. Always add the next sequential `V###__description.sql`; never edit applied migrations.
- **Aerospike** (low-latency lookups, also primary store for `aml-microservice`).
- **Neo4j** (graph relationships for network analysis).
- **Kafka** (event streaming).
- **Caffeine** + Spring Cache, **Resilience4j** (circuit breakers), **HikariCP**.
- **Drools 8** + **Easy Rules** + **SpEL** — three coexisting rule engines (rules under `src/main/resources/rules`).
- **DL4J / ND4J** for deep anomaly detection.
- **MapStruct** + **Lombok** annotation processors — both must be on the annotation processor path (already configured in pom). A `java-delomboked/` mirror exists alongside `java/` — leave it alone unless you understand why it's there.
- **VGS proxy** (`vgs-proxy-spring`) for tokenization.
- **OpenPDF** for SAR/STR report generation.
- **SpringDoc OpenAPI** — Swagger UI exposed by the running app.

### Security & external access
Production hardening (when running under the `production` profile, not `testenv`):
- `SecurityHeadersFilter` (HSTS, CSP, X-Frame-Options, …).
- `ProductionRateLimitFilter` (100 RPM general, 10 RPM auth).
- CORS allowlist sourced from `CORS_ALLOWED_ORIGINS`; defaults include `hokeka.com` subdomains.
- Session cookies: Secure, HttpOnly, SameSite=Strict; CSRF on.
- nginx terminates SSL; Spring binds `0.0.0.0` and trusts forwarded headers. See `BACKEND/DEPLOYMENT.md` and `BACKEND/nginx/fraud-detector-api.conf`.

### FRONTEND structure
- `src/pages/<Domain>/` — one folder per top-level route (Alerts, Cases, Crypto, KycDocuments, Network, PepManagement, RegulatoryReports, Rules, SarReports, Screening, TransactionMonitoring, etc.). Pages map roughly 1:1 to BACKEND controller domains.
- `src/features/`, `src/components/`, `src/contexts/`, `src/hooks/`, `src/services/`, `src/lib/`, `src/types/`.
- React Query for server state; React Router v6; MUI v5 for UI; Chart.js + Recharts for analytics.
- Vite manual chunking is configured (`vendor-react`, `vendor-mui`, `vendor-query`, `vendor-charts`) — preserve this when touching `vite.config.ts`.

### Cross-service contract
FRONTEND assumes `/api/v1/*` lives on the BACKEND (port 2637 in dev). The `aml-microservice` is a separate service and is not proxied through Vite by default — wire it explicitly when needed.

## Working with this repo

- **`BACKEND/` is noisy.** The directory contains many committed `*.log`, `compile_*.txt`, `cookies*.txt`, `debug_output*.txt`, and one-off `*.md` migration reports. Treat them as scratch artefacts — don't rely on them as documentation, and don't add more.
- **Migration discipline matters.** With Hibernate Envers + Flyway + multi-profile DDL behavior, schema changes belong in a new Flyway migration; do not rely on `ddl-auto`.
- **Two Java codebases, two groupIds.** `com.posgateway.aml` (BACKEND) vs `com.hokeka.aml` (aml-microservice). They are not a Maven multi-module — each has its own pom and is built independently.
