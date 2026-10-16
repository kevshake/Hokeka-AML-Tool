# Hokeka AML — System Glossary

Canonical names for the **single supported runtime topology**. Folder paths in parentheses.

**Installation processes:** [`docs/install/README.md`](install/README.md) — separates Console, Control Plane, Edge Node, packages CDN, local dev, and dual-post integration.

| Term | Meaning | Folder / artifact |
|------|---------|-------------------|
| **Console** | Operator SPA for PSP/platform admins — alerts, cases, edge fleet, rules, reports | `FRONTEND/` |
| **Control Plane** | Cloud Spring Boot API + Postgres + Kafka + supporting services; issues edge bundles, ingests transactions, raises compliance artifacts | `BACKEND/` (+ `aml-microservice/` for Aerospike-backed sanctions lookups) |
| **Edge Node** | Per-PSP on-premises deployment: local evaluation + local feature store + control-plane channel | `edge-host/` + `edge-engine/` + local Aerospike |
| **Edge Host** | Spring Boot JNI wrapper around the Rust evaluator; TLS API, bundle poll, metrics ship | `edge-host/` |
| **Edge Engine** | Rust rule kernel (`edge-jni` crate) loaded by Edge Host; sole intended hot-path evaluator | `edge-engine/` |
| **Rule Bundle** | Control-plane compiled, HSE-sealed rule IR pulled by Edge Host (`RuleBundlePoller`) | Produced by `BACKEND` `EdgeBundleService`; consumed by `edge-host` |
| **Metrics Report** | Aggregated edge counters POSTed to control plane (`MetricsShipper` → `EdgeMetricsIngestService`). v2: hourly, per-merchant / per-currency aggregates only | `edge-host/.../EdgeMetricsReport.java`, `BACKEND/.../EdgeMetricsRecord`; v2 schema `docs/contracts/metrics-report-v2.schema.json` |
| **Edge Site** | 1..N Edge Nodes of one PSP sharing one Edge Ledger and one Aerospike cluster; identified by `siteId` (assigned by the Control Plane, defaults to the node's `edgeId`) | `docs/EDGE_DATA_RESIDENCY_CONTRACT.md` §1 |
| **Edge Ledger** | The Edge Site's PostgreSQL store: evaluations (partitioned monthly), outbox, legal holds, config history, purge log, access log. System of record for transaction data, purged by PSP retention (min 5 years) | `edge-host/` (work package R1); contract §1, §8 |
| **Edge Tunnel** | Outbound WebSocket (TLS 1.3 mTLS, `wss://…/api/v1/edge/tunnel`) each Edge Node opens to the Control Plane; carries sealed, audited, read-only requests (`txn.search`, `txn.get`, …) so the Console can view transactions live without the cloud storing them | Contract §7; schemas `docs/contracts/tunnel/` |
| **Decision Record** | ID-and-outcome record of one Edge evaluation (`edgeAssessmentId`, `txnRef`, decision, score, rule ids, bundle, compliance flags); the only per-transaction data that leaves an Edge Site. No PAN hash, amount, currency, country, customer/device ids or free text | Contract §4; `docs/contracts/decision-record-v2.schema.json` |
| **Edge Config** | Sealed per-PSP settings document the Edge pulls from `GET /api/v1/edge/config` (ETag = `configVersion`): retention, support-access flag, cross-PSP opt-in, CTR thresholds/FX, limits, merchant blocklist, sanctions list version, tunnel settings | Contract §6; `docs/contracts/edge-config.schema.json` |
| **Dual-post** | **Superseded** (2026-10-16) by the Edge data residency contract. Was: pre-auth `POST /edge/evaluate` **and** post-auth `POST /api/v1/transactions/ingest`. The ingest path remains only until work package R12 | `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md` (superseded) → `docs/EDGE_DATA_RESIDENCY_CONTRACT.md` |

## Non-product paths (removed / archival)

| Term | Status |
|------|--------|
| **On-prem full-BACKEND lease mode** | **Removed** — `hokeka.auth.enabled` + `/onprem/auth/*` lease enrollment is not a supported product path. Use Edge Node. |
| **architecture-v2** | **Archival reference only** — not started by `docker-compose.prod.yml` or any supported install story. |

## Production compose units

`docker-compose.prod.yml` runs: Console (`frontend-prod`), Control Plane (`backend-prod`), `aml-ms-prod`, Postgres, Aerospike, Kafka, Redis, Neo4j, ClamAV, website. **No Edge Node** — PSPs install edge separately via [`docs/install/01-client-edge-node.md`](install/01-client-edge-node.md) / `edge-host/deploy/install.sh`.
