# Hokeka AML — Documentation Site

Browsable web app for:

1. **Install suite** — process map in [`docs/install/`](../docs/install/) (markdown imported at build time).
2. **Management API** — OpenAPI-driven operator/admin reference at `/management-api`, generated from springdoc group `management` on the Control Plane.

## Prerequisites

- Node.js 18+
- npm (or pnpm/yarn)

## Local development

```bash
cd docs-site
npm install
npm run dev
```

Open **http://localhost:5173** (Vite picks the next free port if 5173 is taken).

## Production build

```bash
cd docs-site
npm install
npm run build
npm run preview   # optional — serve the built static site locally
```

Built assets land in `docs-site/dist/`. Deploy that folder to any static host (nginx, S3, Hostinger docroot, etc.).

## Features

- **Install markdown** — all pages under `docs/install/*.md` (README + processes 01–06)
- **Management API reference** — per-endpoint pages (method, servers, params, types, JSON examples, cURL/Python/JS/Go/Java/PHP/C#/Ruby, Try it panel)
- **Full-text search** — install docs + management endpoint search
- **Catalog** — sort by install order, title, or audience; filter by audience and tag
- **Illustrations** — hero images in `public/illustrations/` on matching pages and the overview

## Management OpenAPI

Regenerate `public/management-api/openapi.json` before release:

```bash
cd BACKEND
export MANAGEMENT_OPENAPI_EXPORT=true
export DATABASE_URL=jdbc:postgresql://localhost:5432/hokeka
export DATABASE_USERNAME=test
export DATABASE_PASSWORD=test
mvn test -Dtest=ManagementOpenApiExportIT -DfailIfNoSpecifiedTests=false
```

CI (`docs-site` job) runs the same export against a Postgres service, then `npm run build` verifies the spec (`scripts/verify-management-openapi.mjs`).

## Product naming

| Term | Meaning |
|------|---------|
| **Console** | Operator dashboard (`FRONTEND/`) — not installed on PSP Edge |
| **Control Plane** | Cloud API stack (`BACKEND/` + `aml-microservice/`) on Hokeka VPS |
| **Edge Node** | PSP on-prem evaluation (`edge-host/` + `edge-engine/` + local Aerospike) |

Unsupported paths (on-prem full-BACKEND lease, `architecture-v2/`) are documented as out-of-scope in the source markdown only.

## Source of truth

Edit install runbooks in `docs/install/`. Rebuild or restart the dev server to pick up changes.
