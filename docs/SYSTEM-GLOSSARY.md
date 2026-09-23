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
| **Metrics Report** | Aggregated edge counters POSTed to control plane (`MetricsShipper` → `EdgeMetricsIngestService`) | `edge-host/.../EdgeMetricsReport.java`, `BACKEND/.../EdgeMetricsRecord` |
| **Dual-post** | Integrator contract: pre-auth `POST /edge/evaluate` (local only) **and** post-auth `POST /api/v1/transactions/ingest` (cloud compliance) | `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md` |

## Non-product paths (removed / archival)

| Term | Status |
|------|--------|
| **On-prem full-BACKEND lease mode** | **Removed** — `hokeka.auth.enabled` + `/onprem/auth/*` lease enrollment is not a supported product path. Use Edge Node. |
| **architecture-v2** | **Archival reference only** — not started by `docker-compose.prod.yml` or any supported install story. |

## Production compose units

`docker-compose.prod.yml` runs: Console (`frontend-prod`), Control Plane (`backend-prod`), `aml-ms-prod`, Postgres, Aerospike, Kafka, Redis, Neo4j, ClamAV, website. **No Edge Node** — PSPs install edge separately via [`docs/install/01-client-edge-node.md`](install/01-client-edge-node.md) / `edge-host/deploy/install.sh`.
