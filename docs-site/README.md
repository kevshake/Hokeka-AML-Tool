# Hokeka AML — Install Docs Site

Browsable web app for the installation process map in [`docs/install/`](../docs/install/). Markdown is imported at **build time** from those source files — there is no duplicated prose in this package.

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

- **Live markdown** — all pages under `docs/install/*.md` (README + processes 01–06)
- **Full-text search** — client-side FlexSearch index across titles and body text
- **Catalog** — sort by install order, title, or audience; filter by audience and tag
- **Illustrations** — hero images in `public/illustrations/` on matching pages and the overview

## Product naming

| Term | Meaning |
|------|---------|
| **Console** | Operator dashboard (`FRONTEND/`) — not installed on PSP Edge |
| **Control Plane** | Cloud API stack (`BACKEND/` + `aml-microservice/`) on Hokeka VPS |
| **Edge Node** | PSP on-prem evaluation (`edge-host/` + `edge-engine/` + local Aerospike) |

Unsupported paths (on-prem full-BACKEND lease, `architecture-v2/`) are documented as out-of-scope in the source markdown only.

## Source of truth

Edit install runbooks in `docs/install/`. Rebuild or restart the dev server to pick up changes.
