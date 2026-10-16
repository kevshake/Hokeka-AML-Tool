# Hokeka simulation harness

An end-to-end simulator. Synthetic PSPs drive the real Control Plane (`BACKEND`, `production`
profile), the aml-microservice and a real Edge Node (`edge-host` + Rust `edge-engine` + Aerospike)
through their public APIs. Each scenario asserts the expected behaviour and records request and
response evidence.

- **Language:** Python 3.10+, standard library only. Run it with `py` on Windows or `python3` elsewhere.
- **Extra tools:** `docker`, `java` 21, `node` (only for the mTLS front-end stand-in), plus `openssl`
  and `keytool` (Git Bash ships openssl).

## Layout

| Path | Purpose |
|---|---|
| `stack/docker-compose.sim.yml` | Data services, compose project `hokeka-sim`. All host ports are bound to 127.0.0.1: Postgres 55433, Kafka 59092, Redis 56379, Neo4j 57687/57474, Aerospike 53000 (namespaces `aml_cache` + `hokeka`). |
| `stack/gen-secrets.py` | Generates the sim-only secrets once into `results/sim-secrets.env`: JWT, AES key, HMAC keys, password-reset pepper, seed admin password, and long-term HSE-1 edge keys via openssl. |
| `stack/seed-watchlist.py` | Loads a synthetic sanctions/PEP watchlist into aml-microservice. Run it **before** the Control Plane: the production profile refuses to boot on an empty watchlist. |
| `stack/start-aml-ms.sh`, `stack/start-control-plane.sh` | Run the built jars against the sim services. Logs go to `results/logs/`. |
| `stack/db_workarounds.py` | Detects schema defects in a fresh Flyway database. `--apply` patches them **in the sim database only** so the run can continue; each defect is reported as a failure by scenario A3. |
| `stack/gen_certs.py` | Sim PKI: CA, front-end server certificate, edge mTLS client certificate and edge TLS keystore. |
| `stack/mtls-proxy.mjs` | Stands in for the nginx edge front end. It terminates TLS 1.3, verifies the client certificate, strips any inbound `X-SSL-Client-*`, and forwards to the Control Plane with `X-SSL-Client-Verify`/`-S-DN` and `X-Forwarded-Proto: https`. |
| `fixtures.py` | Three PSP profiles (card acquirer, mobile money, VASP), their merchants and MCCs, and the synthetic watchlist. |
| `simlib.py` | HTTP client (one cookie session per user), scenario runner and result writers. |
| `sim.py` | Seeding plus all scenarios. |
| `results/sim-results.{json,md}` | Latest summary. Per-run raw HTTP evidence is in `results/runs/<runId>/http.jsonl` (gitignored). |

## Start the stack

Run from the repo root in Git Bash. You need about 6 GB of free RAM: the Control Plane uses a 3 GB
heap, plus Kafka, Neo4j and the Edge Host.

```bash
docker compose -f scripts/sim/stack/docker-compose.sim.yml up -d
mvn -q -f BACKEND/pom.xml package -DskipTests
mvn -q -f aml-microservice/pom.xml package -DskipTests
mvn -q -f edge-host/pom.xml package -DskipTests
# Native core. Without VS Build Tools, use the GNU toolchain (WinLibs/MinGW gcc on PATH):
(cd edge-engine && CARGO_TARGET_DIR=target-gnu cargo +stable-x86_64-pc-windows-gnu build --release -p edge-jni)

py scripts/sim/stack/gen-secrets.py
bash scripts/sim/stack/start-aml-ms.sh &            # wait for :8091/internal/v1/aml/health
py scripts/sim/stack/seed-watchlist.py
bash scripts/sim/stack/start-control-plane.sh &     # Flyway runs on first boot; wait for "Started AmlFraudDetectorApplication"
py scripts/sim/stack/db_workarounds.py --apply      # after Flyway; reports and patches fresh-schema defects
py scripts/sim/stack/gen_certs.py base
node scripts/sim/stack/mtls-proxy.mjs scripts/sim/results/edge/pki 8444 > scripts/sim/results/logs/mtls-proxy.log 2>&1 &
```

## Run the simulator

```bash
py scripts/sim/sim.py                    # everything; a fresh run id each time, so it is rerunnable
py scripts/sim/sim.py --only A B C D     # scenario groups by id prefix (seeding always runs)
py scripts/sim/sim.py --no-edge --volume 500 --threads 32
```

Exit code 0 means no scenario ended FAIL or ERROR. WARN marks a non-blocking deviation.

The simulator launches the Edge Host itself. It requests and approves the node, generates the
edge certificate, starts `java -jar edge-host` with the enrollment code and pinned Control Plane
keys, waits for ACTIVE, then drives `/edge/evaluate`, bundle pull, metrics and the dual-post. Pass
`--keep-edge` to leave it running.

## Stop / reset

```bash
# stop the java/node processes (the simulator stops its Edge Host unless --keep-edge)
taskkill //F //IM java.exe   # or kill the individual PIDs; results/edge.pid holds the Edge Host PID
docker compose -f scripts/sim/stack/docker-compose.sim.yml down -v   # -v wipes the DB for a from-scratch Flyway run
```

## Scenario groups

- **S0:** seed PSPs, users per role, the per-PSP PSP_USER role and merchants.
- **A:** health and actuator exposure, startup warnings, fresh-schema defects.
- **B:** login, session, logout, require-ssl, bearer contract, default credentials, password reset and change.
- **C:** RBAC (PSP users vs platform endpoints) and tenant isolation (PSP A vs PSP B: reads, ingest, billing, edge).
- **D:** invite-only onboarding, API keys, settings and signal taxonomy, billing and invoices.
- **R:** visual-builder rule JSON, maker/checker approval, live effect, disable and versions.
- **T:** volume ingest (latency, false-positive rate), idempotency and validation, plus typologies:
  structuring, rapid movement, velocity bursts, z-score outlier, high-risk MCC, tenant-scoped
  blacklist, sanctions exact/fuzzy/org/PEP and merchant onboarding screening, tipping-off.
- **L:** alerts (keyset pagination, priority, resolve), cases (create, assign, escalate, decide,
  timeline), SAR workflow and XML, CTR/LCTR CSV+PDF, scheme pack JSON/CSV/PDF, report engine,
  console sync and async exports.
- **AI:** no LAYA key leads to graceful shadow fallback. Every PSP-facing response is scanned for provider names.
- **E:** edge enrollment, bundle pull and signature, native core, evaluation, metrics (HSE-1),
  dual-post correlation by `edgeAssessmentId`, mTLS negatives, AI fallback at the edge, suspend fail-closed, revoke.

## Deliberate sim-only deviations from production

- There is no nginx. `mtls-proxy.mjs` plays its edge role, and the simulator sends
  `X-Forwarded-Proto: https` from loopback, so `security.require-ssl` stays on.
- Rate limits are raised (`RATE_LIMIT_*`) so volume ingest is not throttled.
- Kafka auto-creates topics (production disables this). Missing topic provisioning shows up in the CP log.
- Neo4j runs without the GDS plugin, so the scheduled GDS jobs log errors. ClamAV and adverse media are disabled.
- Blacklist entries are inserted with SQL, because no blacklist REST API exists (scenario T8 reports this).
