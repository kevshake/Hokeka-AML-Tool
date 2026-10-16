# Hokeka AML — System Glossary

Canonical names for the **single supported runtime topology**. Folder paths in parentheses.

**Installation processes:** [`docs/install/README.md`](install/README.md) — separates Console, Control Plane, Edge Node, packages CDN and local dev.

**Data residency:** the Edge Node is the only evaluator, and the cloud never stores transaction data. Normative rules: [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md). Work packages (R1…R15): [`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md).

| Term | Meaning | Folder / artifact |
|------|---------|-------------------|
| **Console** | Operator SPA for PSP and platform admins: alerts, cases, edge fleet, rules, reports, data settings (retention, support access, cross-PSP opt-in). Live transaction views from the Edge are planned (R11) | `FRONTEND/` |
| **Control Plane** | Cloud Spring Boot API + Postgres + Kafka + supporting services. Issues rule bundles and Edge Config, receives decision records and aggregate metrics, raises alerts, cases and SAR/STR/CTR workflow by transaction id, hosts the Edge Tunnel server. The legacy cloud ingest path still stores transactions until R12 deletes it | `BACKEND/` (+ `aml-microservice/` for Aerospike-backed sanctions lookups) |
| **Edge Node** | One PSP on-premises `edge-host` process (+ `edge-engine`), identified by `edgeId` (its mTLS certificate CN). Evaluates every transaction, records it on the Edge Ledger, keeps velocity in Aerospike, talks to the Control Plane over outbound mTLS on 443 | `edge-host/` + `edge-engine/` + Postgres 16 ledger + Aerospike |
| **Edge Host** | Spring Boot JNI wrapper around the Rust evaluator: TLS API, bundle poll, ledger, outbox shipper, metrics ship | `edge-host/` |
| **Edge Engine** | Rust rule kernel (`edge-jni` crate) loaded by Edge Host; the hot-path evaluator | `edge-engine/` |
| **Edge Site** | 1..N Edge Nodes of one PSP that share one Edge Ledger and one Aerospike cluster. Identified by `siteId`, assigned by the Control Plane (`edge_nodes.site_id`, V263); a single-node site's `siteId` defaults to its `edgeId` | `EDGE_SITE_ID`; contract §1 |
| **Edge Ledger** | The Edge Site's PostgreSQL 16 database: `edge_evaluations` (partitioned by month), `edge_txn_refs`, `edge_outbox`, legal holds, config history, purge log, access log. System of record for transaction data. Retention is set per PSP (minimum and default 5 years, maximum 25). The purge job is planned (R2) | `edge-host` `com.hokeka.edge.ledger`, migrations `db/ledger`; contract §1, §8 |
| **Edge Tunnel** | Outbound WebSocket (TLS 1.3 mTLS, `wss://edge.hokeka.com/api/v1/edge/tunnel`, subprotocol `hokeka-tunnel.v1`) each Edge Node opens to the Control Plane. Carries sealed, audited, read-only requests (`txn.search`, `txn.get`, …) so the Console can view transactions without the cloud storing them. Cloud server built (R4); Edge client planned (R5) | `BACKEND` `com.posgateway.aml.edge.tunnel`, `BACKEND/nginx/edge-tunnel.conf`; contract §7; schemas `docs/contracts/tunnel/` |
| **Decision Record** | ID-and-outcome record of one Edge evaluation (`edgeAssessmentId`, `txnRef`, decision, score, rule ids, bundle, compliance flags). The only per-transaction data that leaves an Edge Site. No PAN hash, amount, currency, country, customer or device ids, or free text. Sent sealed to `POST /api/v1/edge/assessments` | Contract §4; `docs/contracts/decision-record-v2.schema.json`; cloud table `assessments` (V262) |
| **Edge Config** | Sealed per-PSP settings document served at `GET /api/v1/edge/config` (ETag = `configVersion`): retention, support-access flag, cross-PSP opt-in, CTR thresholds and FX, limits, merchant blocklist, sanctions list version, tunnel settings. Served today (R3); the Edge consumer is planned (R2) | Contract §6; `docs/contracts/edge-config.schema.json`; `BACKEND` `com.posgateway.aml.service.residency` |
| **Rule Bundle** | Control-plane compiled, HSE-sealed rule IR pulled by Edge Host (`RuleBundlePoller`) | Produced by `BACKEND` `EdgeBundleService`; consumed by `edge-host` |
| **Metrics Report** | Aggregated edge counters POSTed to the Control Plane (`MetricsShipper` → `EdgeMetricsIngestService`). v1 is sent today. v2 (hourly, per-merchant and per-currency aggregates only) is specified, not yet sent | `edge-host/.../EdgeMetricsReport.java`, `BACKEND/.../EdgeMetricsRecord`; v2 schema `docs/contracts/metrics-report-v2.schema.json` |
| **Support access** | PSP setting that lets Hokeka staff pull that PSP's Edge data. Off by default. Each pull needs a reason and is audited on both sides | `psps.support_access_enabled` (V261); contract §9 |
| **Dual-post** | **Retired term.** It meant calling `POST /edge/evaluate` and also `POST /api/v1/transactions/ingest`. Do not use it for new work. The ingest endpoint still runs until R12 deletes it | [`EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md) (superseded stub) |

## Non-product paths (removed / archival)

| Term | Status |
|------|--------|
| **On-prem full-BACKEND lease mode** | **Removed** — `hokeka.auth.enabled` + `/onprem/auth/*` lease enrollment is not a supported product path. Use Edge Node. |
| **architecture-v2** | **Archival reference only** — not started by `docker-compose.prod.yml` or any supported install story. |

## Production compose units

`docker-compose.prod.yml` runs: Console (`frontend-prod`), Control Plane (`backend-prod`), `aml-ms-prod`, Postgres, Aerospike, Kafka, Redis, Neo4j, ClamAV, website. **No Edge Node** — PSPs install edge separately via [`docs/install/01-client-edge-node.md`](install/01-client-edge-node.md) / `edge-host/deploy/install.sh`.
