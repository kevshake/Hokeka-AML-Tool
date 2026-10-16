# Management API documentation

Operator and tenant administration for the Hokeka Control Plane (Console session API).

## Where to read it

- **Docs site:** `docs-site/` — route `/management-api` (OpenAPI-driven reference, search, code samples, Try it panel).
- **OpenAPI JSON:** generated from springdoc group `management` at `docs-site/public/management-api/openapi.json`.
- **Machine index:** `docs-site/public/management-api/llms.txt` (Flagright-style endpoint list).

## Regenerate OpenAPI locally

```bash
# Postgres required (example: docker run -p 5433:5432 -e POSTGRES_PASSWORD=test -e POSTGRES_USER=test -e POSTGRES_DB=hokeka postgres:16-alpine)
cd BACKEND
export DATABASE_URL=jdbc:postgresql://localhost:5433/hokeka
export DATABASE_USERNAME=test
export DATABASE_PASSWORD=test
export MANAGEMENT_OPENAPI_EXPORT=true
mvn test -Dtest=ManagementOpenApiExportIT -DfailIfNoSpecifiedTests=false
```

## Scope

Documented paths include users, roles, permissions, PSPs, platform admin (`/admin/**`), edge nodes, webhooks, rules, audit logs, console export jobs, and settings. Operator-only Hokeka AI admin (`/ai/**` engine and PSP AI settings) is **excluded** from the public Management OpenAPI export (Console-only). Transaction ingest, screening, cases, and alerts are **not** part of the Management API (see PSP integration guide).

Role matrix: [CONSOLE_ROLE_MATRIX.md](../CONSOLE_ROLE_MATRIX.md).
