# Hokeka AML — Installation Process Map

**Start here.** This index separates **who installs what** so operators never mix a PSP Edge Node install with the Console (dashboard) or Control Plane stack.

Canonical product names: [`docs/SYSTEM-GLOSSARY.md`](../SYSTEM-GLOSSARY.md).

Deep technical reference (secrets, compose internals, release pipeline details): [`docs/INSTALL.md`](../INSTALL.md).

---

## Who installs what

| Role | Installs | Where | Process doc |
|------|----------|-------|-------------|
| **PSP / client infrastructure engineer** | **Edge Node** — local transaction evaluation + Aerospike feature store | PSP on-premises servers | [01 — Client Edge Node](01-client-edge-node.md) |
| **PSP / client integration engineer** | **Dual-post API wiring** — `/edge/evaluate` + cloud ingest (not a server) | PSP API / payment nodes | [06 — PSP API dual-post](06-psp-api-dual-post-integration.md) |
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
| 4 | [01 — Client Edge Node](01-client-edge-node.md) | PSP installs on-prem evaluation node; enroll from Console |
| 5 | [06 — PSP API dual-post](06-psp-api-dual-post-integration.md) | PSP wires API nodes: pre-auth edge + post-auth cloud ingest |

For **Hokeka-only internal work**, use [05 — Local developer stack](05-local-developer-stack.md) instead of steps 1–2 on a laptop.

---

## Process documents

| Doc | Audience | Scope |
|-----|----------|-------|
| [01-client-edge-node.md](01-client-edge-node.md) | PSP infra | Complete Edge Node runbook: sizing, install, activation, verify, upgrade |
| [02-console-dashboard.md](02-console-dashboard.md) | Hokeka ops / frontend dev | Console only — prod nginx path, Vite dev, **not** on client edge |
| [03-control-plane.md](03-control-plane.md) | Hokeka ops | VPS secrets, `docker-compose.prod.yml`, Flyway, health checks |
| [04-packages-cdn-and-edge-release.md](04-packages-cdn-and-edge-release.md) | Hokeka release ops | packages.hokeka.com layout, GPG signing, GitHub release workflow |
| [05-local-developer-stack.md](05-local-developer-stack.md) | Hokeka engineers | Java 21, Postgres, BACKEND dev profile, FRONTEND Vite |
| [06-psp-api-dual-post-integration.md](06-psp-api-dual-post-integration.md) | PSP integration | Wire evaluate + ingest; webhooks; enrollment from Console |

---

## Deep-dive companions (not separate install processes)

| Document | Use when |
|----------|----------|
| [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) | Extended Edge sizing, network, security detail |
| [`docs/edge-install-troubleshooting.md`](../edge-install-troubleshooting.md) | Edge install failures by root cause |
| [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md) | `/edge/evaluate` request/response contract |
| [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](../EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md) | Normative dual-post contract |
| [`docs/PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) | Cloud API reference (ingest, webhooks, auth) |
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
   ├─ edge-host + edge-engine
   └─ local Aerospike (feature store)
      txn data STAYS HERE
```

Dual-post: Edge alone does **not** create cloud alerts or cases — see [06-psp-api-dual-post-integration.md](06-psp-api-dual-post-integration.md).
