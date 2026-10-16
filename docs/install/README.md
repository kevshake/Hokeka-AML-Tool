# Hokeka AML — Installation Process Map

**Start here.** This index separates **who installs what** so operators never mix a PSP Edge Node install with the Console (dashboard) or Control Plane stack.

> **Browsable docs site:** Run the install documentation web app from [`docs-site/`](../../docs-site/) (`npm install && npm run dev`). It renders this folder's markdown with search, catalog sort/filter, and illustrations — see [`docs-site/README.md`](../../docs-site/README.md).

Canonical product names: [`docs/SYSTEM-GLOSSARY.md`](../SYSTEM-GLOSSARY.md).

Deep technical reference (secrets, compose internals, release pipeline details): [`docs/INSTALL.md`](../INSTALL.md).

---

## Who installs what

| Role | Installs | Where | Process doc |
|------|----------|-------|-------------|
| **PSP / client infrastructure engineer** | **Edge Node** — local transaction evaluation, Edge Ledger (PostgreSQL 16), Aerospike velocity store | PSP on-premises servers | [01 — Client Edge Node](01-client-edge-node.md) |
| **PSP / client integration engineer** | **API wiring** — PSP API nodes call `/edge/evaluate` (not a server) | PSP API / payment nodes | [`edge-transaction-evaluation.md`](../edge-transaction-evaluation.md) |
| **Hokeka platform ops** | **Control Plane** — API, Postgres, Kafka, aml-microservice, etc. | Hokeka Hostinger VPS | [03 — Control Plane](03-control-plane.md) |
| **Hokeka platform ops** | **Console** — operator SPA (alerts, cases, edge fleet, rules) | Same VPS as Control Plane (via `frontend-prod`) | [02 — Console](02-console-dashboard.md) |
| **Hokeka release ops** | **Edge artifact CDN** — packages.hokeka.com, signing, release workflow | Separate Hostinger VPS (or shared docroot) | [04 — Packages CDN & edge release](04-packages-cdn-and-edge-release.md) |
| **Hokeka engineer** | **Local full stack** — BACKEND + Console + infra for development | Developer laptop / CI | [05 — Local developer stack](05-local-developer-stack.md) |

**Console is never installed on the PSP edge box.** **Edge Node is never installed on Hokeka's Control Plane VPS** (PSPs install edge separately).

---

## Ordered process list

Follow these in order for a **new PSP go-live**:

| Step | Process | One-line purpose |
|------|---------|------------------|
| 1 | [03 — Control Plane](03-control-plane.md) | Hokeka brings up cloud API + data services on Hostinger |
| 2 | [02 — Console](02-console-dashboard.md) | Hokeka serves the operator dashboard (built into prod compose) |
| 3 | [04 — Packages CDN](04-packages-cdn-and-edge-release.md) | Hokeka publishes signed Edge artifacts clients download |
| 4 | [01 — Client Edge Node](01-client-edge-node.md) | PSP installs the on-prem evaluation node and its ledger; enroll from Console |
| 5 | [`edge-transaction-evaluation.md`](../edge-transaction-evaluation.md) | PSP wires API nodes to `/edge/evaluate`. The Edge ships decision records; the cloud raises alerts and cases from them |

**Fresh OS?** Run [07 — First-run bootstrap](07-first-run-bootstrap.md) (`scripts/install/hokeka-install.sh` / `hokeka-install.ps1`) before the role-specific steps above.

For **Hokeka-only internal work**, use [05 — Local developer stack](05-local-developer-stack.md) instead of steps 1–2 on a laptop.

---

## Process documents

| Doc | Audience | Scope |
|-----|----------|-------|
| [01-client-edge-node.md](01-client-edge-node.md) | PSP infra | Edge Node runbook: 5-year ledger sizing, install, activation, verify, upgrade |
| [02-console-dashboard.md](02-console-dashboard.md) | Hokeka ops / frontend dev | Console only — prod nginx path, Vite dev, **not** on client edge |
| [03-control-plane.md](03-control-plane.md) | Hokeka ops | VPS secrets, `docker-compose.prod.yml`, Flyway, health checks |
| [04-packages-cdn-and-edge-release.md](04-packages-cdn-and-edge-release.md) | Hokeka release ops | packages.hokeka.com layout, GPG signing, GitHub release workflow |
| [05-local-developer-stack.md](05-local-developer-stack.md) | Hokeka engineers | Java 21, Postgres, BACKEND dev profile, FRONTEND Vite |
| [07-first-run-bootstrap.md](07-first-run-bootstrap.md) | All installers | Idempotent OS bootstrap: Docker, Java/Node/Rust, `.env`, systemd; delegates to Edge/Compose |

---

## Deep-dive companions (not separate install processes)

| Document | Use when |
|----------|----------|
| [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) | Extended Edge sizing, network, security detail |
| [`docs/edge-install-troubleshooting.md`](../edge-install-troubleshooting.md) | Edge install failures by root cause |
| [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md) | `/edge/evaluate` request/response contract |
| [`docs/EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md) | Normative contract: what leaves the Edge, decision records, Edge config, Edge Tunnel, retention |
| [`docs/PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) | Cloud API reference (webhooks, auth, workflow) |
| [`docs/architecture/edge-channel-contract.md`](../architecture/edge-channel-contract.md) | Control Plane ↔ Edge wire protocol |
| [`edge-host/deploy/README.md`](../../edge-host/deploy/README.md) | Installer security model quick reference |

---

## Do **not** install (unsupported / archival)

| Path or mode | Why |
|--------------|-----|
| **On-prem full-BACKEND lease** (`hokeka.auth.enabled`, `/onprem/auth/*`) | **Removed** — not a product path. Use Edge Node. |
| **`architecture-v2/`** compose stacks | **Archival reference only** — not started by `docker-compose.prod.yml`. See [`architecture-v2/README.md`](../../architecture-v2/README.md). |
| **Lowercase `frontend/`** directory | **Orphan artifact** — not the Console. Product SPA is **`FRONTEND/`** (uppercase). See [`frontend/README.md`](../../frontend/README.md). |
| **`BACKEND/DEPLOYMENT.md` bare-metal systemd path** | **Superseded** for Control Plane — use Docker Compose per [03-control-plane.md](03-control-plane.md). Retain only for nginx/tuning reference. |
| **Sumsub or third-party KYC SDK installs** | Not part of this platform's supported install set. |
| **Edge on Hokeka VPS** | Edge belongs on PSP premises only. Control Plane Aerospike (`aml-ms-prod`) is cloud-side sanctions cache, not the Edge feature store. |

---

## Quick topology reminder

```
PSP PREMISES                          HOKEKA (Hostinger VPS)
────────────────                      ──────────────────────
PSP API nodes                         aml.hokeka.com → Console (frontend-prod :8088)
   │ TLS                                   api.hokeka.com → Control Plane (:2637)
   ▼
Edge Node :8443                       packages.hokeka.com → Edge release artifacts
   ├─ edge-host + edge-engine         edge.hokeka.com → Control Plane edge vhost (mTLS)
   ├─ Edge Ledger (PostgreSQL 16)
   │    transaction data STAYS HERE
   ├─ Aerospike (velocity buckets, bundle)
   │
   └── outbound TCP 443 only ──────►  decision records, metrics, bundle + config pulls
```

The Edge is the only evaluator. The cloud stores decision records (IDs, action, score, rule IDs), never transaction data, and raises alerts and cases from them. The Console will read transaction details live from the Edge over an Edge-opened tunnel (Edge tunnel client planned, R5; Console live pulls planned, R11). Contract: [`docs/EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md).
