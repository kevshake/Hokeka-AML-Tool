# Process 01 — Client (PSP) Edge Node Installation

**Audience:** PSP infrastructure or platform engineer who installs an **Edge Node** on the PSP's own servers.

**Product term:** **Edge Node** = `edge-host/` + `edge-engine/` + local Aerospike + the **Edge Ledger** (PostgreSQL 16). See [`docs/SYSTEM-GLOSSARY.md`](../SYSTEM-GLOSSARY.md).

**This is not:** the Console or the Control Plane. Those are separate processes ([README](README.md)).

**Normative contract:** [`docs/EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md).

---

## What you are installing

The Edge Node is the only component that evaluates the PSP's transactions. It runs inside the PSP estate.

- PSP API nodes call **`POST /edge/evaluate`** over TLS on port **8443**. That is the only call they make. See [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).
- Every evaluation is written to the **Edge Ledger**, a PostgreSQL 16 database on your site. The ledger is the system of record for transaction data. Back it up.
- **Aerospike** holds only hourly velocity buckets (26 h TTL) and the rule bundle. It no longer stores transactions.
- The Edge sends **decision records** to the Control Plane: IDs, action, score and rule IDs. No PAN, amount or feature values. The Control Plane raises alerts and cases from them.
- The Edge also sends aggregate metrics and pulls the signed rule bundle.
- Every connection is opened by the Edge, outbound on TCP 443. You open no inbound port to the internet.

Planned, not built yet:

- The Edge will pull the sealed Edge config document (retention and other PSP settings) from `GET /api/v1/edge/config`. The Control Plane serves it today; the Edge consumer is planned (R2).
- The Edge will open an outbound WebSocket tunnel (WSS on 443) so the Console can read transaction details live from your ledger. The Control Plane side and its nginx vhost exist; the Edge tunnel client is planned (R5). Until then the Console cannot show ledger data.
- The Edge will purge ledger data older than your retention period (planned, R2). Until then the ledger keeps everything and grows.

---

## Prerequisites

### From Console (before touching the server)

1. A Hokeka operator creates the Edge Node in **Console → Edge Nodes** (Setup Wizard).
2. Collect from the wizard:
   - **Numeric PSP id** (for example `42`). Not a slug; a `--pspid` mismatch returns **403**.
   - **Edge id** (for example `acme-eu-1`).
   - **Enrollment code** (single use, time limited).
   - **Control Plane edge URL** (normally `https://edge.hokeka.com`).
3. Copy the **install command** from the wizard. Do not compose `--pspid` by hand.

Console installation is documented separately: [02 — Console](02-console-dashboard.md). You only need Console access for enrollment.

### Retention

A PSP admin sets retention in **Console → Settings → Data Retention**.

- Minimum and default: 5 years (FATF R.11). Maximum: 25 years.
- Changes go through maker-checker. A reduction takes effect 30 days after approval.
- The Control Plane publishes the setting in the sealed Edge config document. The Edge will read it and purge to it once R2 is built.
- It is not an installer setting. `--retention-days` is accepted and ignored with a warning.

### Ledger disk sizing (5 years)

Size the ledger disk for at least 5 years. `install.sh --peak-tps N` prints this estimate at the start of the install:

```
records = N × 86 400 × 365 × 5
disk    = records × 2 048 bytes ÷ 2^30 × 1.3   (GiB, 30% headroom)
```

| Peak TPS | Evaluations in 5 years | Ledger disk (approx.) |
|---------:|-----------------------:|----------------------:|
| 1 | 157 M | 390 GiB |
| 5 | 788 M | 1 953 GiB |
| 10 | 1 576 M | 3 909 GiB |
| 50 | 7 884 M | 19 548 GiB |
| 100 | 15 768 M | 39 097 GiB |

This is an upper bound. It assumes peak TPS for 24 hours a day, every day. 2 048 bytes per evaluation is an estimate (row, indexes, outbox row, Postgres overhead). Measure real volumes in a pilot before you buy storage. If you keep data longer than 5 years, scale the numbers by `years ÷ 5`. `install.ps1` has no `-PeakTps` option; use the formula above on Windows.

If the ledger disk fills, the node cannot record evaluations and answers **HOLD** (fail-closed).

### Server requirements (summary)

| Concern | Guidance |
|---------|----------|
| **Ledger disk** | See the sizing table above. Put the Postgres data volume on durable, backed-up storage. |
| **Aerospike** | Velocity buckets only (26 h TTL). The shipped data file size is enough; `--data-file-size` overrides it. |
| **OS** | Linux (container or native) or Windows Server (container only). Full matrix: [edge-client-install-guide §2.1](../edge-client-install-guide.md). |
| **Network inbound** | None from the internet. **8443/tcp** only from your own PSP API nodes. The API binds to `127.0.0.1` by default; use `--bind` and `--allow-subnet` to open it to your subnet. |
| **Network outbound** | **TCP 443** to `edge.hokeka.com` (ongoing, mTLS). **TCP 443** to `packages.hokeka.com` (install and upgrade only). No other egress. No TLS interception on the Control Plane path. |
| **Access** | Root (Linux) or Administrator (Windows). |
| **Backup** | Back up the **Edge Ledger** (the PSP's multi-year system of record) and **`/opt/hokeka/data/`** (node identity keys; loss requires re-enrollment). |

More sizing, disk and network detail: [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) §2–3.

---

## Choose deployment mode

| Mode | Command flag | Ledger | Best for |
|------|--------------|--------|----------|
| **Container** (default) | *(none)* | `ledger` service (`postgres:16-alpine`) on the private network, no published port | Almost all estates |
| **Native** | `--native` | PostgreSQL 16 installed from the distro or PGDG, bound to `127.0.0.1`, port 5432 firewalled | Linux estates that prohibit containers |
| **Windows** | `install.ps1` | `ledger` container, as in container mode | Windows Server (container path only) |

### Multi-node Edge Site (shared ledger)

Several nodes of one PSP can share one ledger and one Aerospike cluster. That group is an **Edge Site**.

- Linux: `--ledger-url jdbc:postgresql://site-db:5432/hokeka_ledger --ledger-user <user> --site-id <site>`
- Windows: `-LedgerUrl jdbc:postgresql://site-db:5432/hokeka_ledger -LedgerUser <user> -SiteId <site>`
- Put the database password in the `HOKEKA_LEDGER_PASSWORD` environment variable. The installers never take it on the command line.
- With a shared ledger the installer does not start a local `ledger` service.
- Without `--site-id` the node is a single-node site whose site id is its edge id.

On a **fresh Linux or Windows host**, install prerequisites first with the unified bootstrap ([07 — First-run bootstrap](07-first-run-bootstrap.md)):

```bash
sudo ./scripts/install/hokeka-install.sh --role edge --yes -- \
  --pspid <PSP_ID> --edgeid <EDGE_ID> --controlplane <URL> --enrollment-code <CODE>
```

---

## Installation steps

### Step 1 — Download and review the installer

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh -o install.sh
less install.sh   # read before running as root
```

Windows: get `install.ps1` from the same origin or from Hokeka support.

### Step 2 — Run install (use wizard-generated values)

**Linux — container (recommended):**

```bash
sudo bash install.sh \
  --pspid 42 \
  --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234 \
  --bind 10.0.0.5 \
  --allow-subnet 10.0.0.0/24 \
  --peak-tps 20
```

**Linux — native:**

```bash
sudo bash install.sh --native \
  --pspid 42 --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234
```

**Windows (Administrator PowerShell):**

```powershell
.\install.ps1 -PspId 42 -EdgeId acme-eu-1 `
  -ControlPlane https://edge.hokeka.com -EnrollmentCode ABCD-1234
```

**Production change control — pin the version:**

```bash
sudo bash install.sh --version 1.2.3 ...
```

Other flags: `--channel beta`, `--repo-url URL`, `--offline`, `--peak-tps N`, `--tls-client-auth need`, `--data-file-size 16G`, `--ledger-url`, `--ledger-user`, `--site-id`.

### Step 3 — What the installer does

1. Detects distro, package manager, CPU architecture and libc.
2. Downloads artifacts and verifies the **GPG-signed** `SHA256SUMS` against a key **pinned** in the script.
3. Installs prerequisites: a container runtime, or Temurin JRE 25 + Aerospike CE + PostgreSQL 16 in native mode.
4. Isolates the databases. Container mode: Aerospike and the ledger have no published port. Native mode: both bind to `127.0.0.1`, and ports 3000 and 5432 are firewalled.
5. Generates a random ledger password into `secrets/edge-ledger.pass` (mode 600) and reuses it on re-runs.
6. Generates a self-signed TLS keystore (mode 600). The engine **refuses to start without TLS**.
7. Writes `.env` (container) or `/opt/hokeka/config/edge.env` (native), mode 600, including `EDGE_LEDGER_URL`, `EDGE_LEDGER_USER`, `EDGE_LEDGER_PASSWORD`, `EDGE_LEDGER_FAIL_CLOSED=true` and `EDGE_SITE_ID`.
8. Starts the service and checks `https://127.0.0.1:8443/actuator/health`.

The edge runs its own ledger schema migrations at start-up. If the ledger is down, the node still starts, retries the connection, and answers **HOLD** with `failClosedCode = LEDGER_UNAVAILABLE` until the ledger is writable.

> On a **signature failure**, stop. Do not use `--insecure-skip-signature`. Contact Hokeka.

Installer internals: [`docs/INSTALL.md`](../INSTALL.md) §2.2, [`edge-host/deploy/README.md`](../../edge-host/deploy/README.md).

---

## Post-installation

### 1. Replace the TLS certificate (required for production)

The installer creates a **self-signed** certificate. Replace it with a PKCS#12 from your CA:

```bash
keytool -importkeystore \
  -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \
  -destkeystore /opt/hokeka/secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge
# Update EDGE_TLS_KEYSTORE_PASSWORD in .env, then restart the service / docker compose
```

For mutual TLS from your API nodes, install with `--tls-client-auth need` and configure the client-CA truststore.

### 2. Portal approval and pinned keys

Until a Hokeka platform admin **approves** the node in the Console:

- `GET /edge/status` returns `"authorization": "unauthorized"`
- `POST /edge/evaluate` returns **HOLD** (fail-closed)
- No rule bundle is served

After approval, set in `.env`:

```bash
CONTROLPLANE_ED25519_PUBLIC_KEY=<from Console>
CONTROLPLANE_X25519_PUBLIC_KEY=<from Console>
```

Put the issued mTLS client certificate at `/opt/hokeka/secrets/edge-client.p12`.

### 3. Verify status

```bash
curl -sk https://127.0.0.1:8443/edge/status | jq
```

| Field | Required |
|-------|----------|
| `authorization` | Not `unauthorized` |
| `evaluator` | **`native`** (not `fallback-java-interpreter`) |
| `featureStore` | Not `unavailable` (velocity rules need Aerospike) |
| `ledger.state` | `OK` (`DOWN` or `NOT_CONFIGURED` means every evaluation answers HOLD) |
| `ledger.outboxBacklog` | Small and falling (decision records waiting to ship) |
| health | `UP` |

### 4. Test evaluate

```bash
curl -sk https://127.0.0.1:8443/edge/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"txn_id":"test-1","pan_hash":"<hash>","amount_cents":1000,
       "currency":"KES","country_code":"KE","mcc":"5411","merchant_id":"m-1"}' | jq
```

Field names are **`snake_case`**. See [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).

`txn_id` must not look like a card number. A 13–19-digit value that passes the Luhn check gets **422** `TXN_REF_LOOKS_LIKE_PAN`, and nothing is evaluated or stored.

### 5. Wire your API nodes

Your API nodes call `/edge/evaluate` before authorisation and act on the decision. Nothing else is needed for cloud alerts and cases: the Edge ships a decision record for each evaluation, and the Control Plane raises alerts and cases from it.

The older cloud ingest endpoint (`POST /api/v1/transactions/ingest`) still works for existing integrations. It stores transaction data in the cloud, which the residency model forbids, and its removal is planned (R12). Do not build new integrations on it. The superseded contract is [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](../EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

### Hokeka AI recommendation

The installer does **not** install a local AI service. Every Hokeka AI recommendation is produced on the Control Plane. Edge Nodes never hold AI credentials.

Today, for borderline decisions (`ALERT` or `HOLD`), the Edge asks the Control Plane for a Hokeka AI recommendation over the mTLS channel (`POST /api/v1/edge/decision`). That request carries the evaluation's feature values. It is the one path where transaction features leave the Edge today. Its removal is planned (R10c); after that the recommendation will use decision records only. The recommendation is advisory: it never changes the action or score the Edge returned.

---

## Operations

### Day-to-day

```bash
systemctl status hokeka-edge          # native
journalctl -u hokeka-edge -f
docker compose ps && docker compose logs -f edge   # container
docker compose logs -f ledger                       # container, local ledger
```

### Monitor

| Signal | Action |
|--------|--------|
| `evaluator` ≠ `native` | Native core failed to load. Check `/opt/hokeka/lib/`. |
| `featureStore: unavailable` | Aerospike is down. Velocity rules are disabled. |
| Evaluations answer HOLD with `LEDGER_UNAVAILABLE` | The ledger is down or full. Check the `ledger` service (or PostgreSQL 16) and its disk. |
| Ledger disk usage | Compare with the sizing table. Until purge is built (R2) usage only grows. |
| `authorization` change | The node was de-authorised. All traffic answers HOLD. |
| Certificate expiry | Server certificate and mTLS client certificate. |

### Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --version 1.3.0 ...
```

Re-running is idempotent. Identity in `/opt/hokeka/data/`, the ledger password and the ledger data are kept. Roll back by re-installing a prior `--version`. There is no automatic canary: upgrade one node, verify step 3, then the rest.

Artifacts come from [04 — Packages CDN](04-packages-cdn-and-edge-release.md).

---

## Uninstall

```bash
# native
sudo systemctl disable --now hokeka-edge
sudo rm /etc/systemd/system/hokeka-edge.service && sudo systemctl daemon-reload

# container (keeps volumes, including the ledger)
cd /opt/hokeka && docker compose down
```

`docker compose down -v` also deletes the `ledger-data` volume, which holds the PSP's transaction records. Back up the ledger and `/opt/hokeka/data/` first, and only delete them when your retention obligations allow it.

---

## Troubleshooting

| Symptom | Doc |
|---------|-----|
| Install, signature, ledger or activation failures | [`docs/edge-install-troubleshooting.md`](../edge-install-troubleshooting.md) |
| Quick symptom table | [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) §8 |

Collect for support: `/edge/status` JSON, the last hour of service logs, the installed version, `uname -a`, the edge id.

---

## Related

- [Installation process map](README.md)
- [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) — extended client guide
- [`docs/INSTALL.md`](../INSTALL.md) §2 — Edge technical reference
- [`docs/EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md) — what data leaves the Edge, and how
- [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md) — `/edge/evaluate` contract
