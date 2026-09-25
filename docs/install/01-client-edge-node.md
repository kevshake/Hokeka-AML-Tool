# Process 01 — Client (PSP) Edge Node Installation

**Audience:** PSP infrastructure / platform engineer installing an **Edge Node** on the client's own servers.

**Product term:** **Edge Node** = `edge-host/` + `edge-engine/` + local Aerospike (`docs/SYSTEM-GLOSSARY.md`).

**This is not:** Console, Control Plane, or API dual-post wiring — those are separate processes ([README](README.md)).

---

## What you are installing

A self-contained fraud-decisioning node inside the PSP estate:

- PSP API nodes call **`POST /edge/evaluate`** over TLS on port **8443**.
- Transaction and velocity history live in **local Aerospike** — not on Hokeka's servers.
- Only **aggregate metrics** and **signed rule-bundle pulls** cross the outbound mTLS channel to the Control Plane.

> **Dual-post reminder:** Edge evaluation alone does **not** create cloud alerts, cases, SAR filings, or webhooks. After go-live, the integration team must complete [06 — PSP API dual-post integration](06-psp-api-dual-post-integration.md).

---

## Prerequisites

### From Console (before touching the server)

1. A Hokeka operator creates the Edge Node in **Console → Edge Nodes** (Setup Wizard).
2. Collect from the wizard:
   - **Numeric PSP id** (e.g. `42`) — not a slug; `--pspid` mismatch returns **403**.
   - **Edge id** (e.g. `acme-eu-1`).
   - **Enrollment code** (single-use, time-limited).
   - **Control Plane edge URL** (typically `https://edge.hokeka.com`).
3. Copy the **install command** from the wizard — do not hand-compose `--pspid`.

Console installation is documented separately: [02 — Console](02-console-dashboard.md). You only need Console access for enrollment, not to install Console on this box.

### Server requirements (summary)

| Concern | Guidance |
|---------|----------|
| **Sizing** | RAM driven by Aerospike index (~64 B/record × retention × TPS). See tier table in [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) §2.3. |
| **Retention** | Default 90 days; set `--retention-days` at install — drives RAM and disk. |
| **OS** | Linux (container or native) or Windows Server (container only). Full matrix: edge-client-install-guide §2.1. |
| **Network inbound** | **8443/tcp** from PSP API nodes only — never expose to the internet. |
| **Network outbound** | **443** to `edge.hokeka.com` (ongoing, mTLS); **443** to `packages.hokeka.com` (install/upgrade only). No TLS interception on the control-plane path. |
| **Access** | Root (Linux) or Administrator (Windows). |
| **Backup** | Plan to back up **`/opt/hokeka/data/`** — holds node identity keys; loss requires re-enrollment. |

Extended sizing, disk layout, and network detail: [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) §2–3.

---

## Choose deployment mode

| Mode | Command flag | Best for |
|------|--------------|----------|
| **Container** (default) | *(none)* | Almost all estates; identical stack everywhere |
| **Native** | `--native` | Linux estates that prohibit containers |
| **Windows** | `install.ps1` | Windows Server — container path only (no native Aerospike on Windows) |

---

## Installation steps

### Step 1 — Download and review the installer

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh -o install.sh
less install.sh   # read before running as root
```

Windows: obtain `install.ps1` from the same origin or from Hokeka support.

### Step 2 — Run install (use wizard-generated values)

**Linux — container (recommended):**

```bash
sudo bash install.sh \
  --pspid 42 \
  --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234 \
  --bind 10.0.0.5 \
  --allow-subnet 10.0.0.0/24
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

**Production change control — pin version:**

```bash
sudo bash install.sh --version 1.2.3 ...
```

Useful flags: `--channel beta`, `--repo-url URL`, `--offline`, `--retention-days N`, `--peak-tps N`, `--tls-client-auth need`, `--data-file-size 256G`.

### Step 3 — What the installer does (verify expectations)

1. Detects distro, package manager, CPU arch.
2. Downloads artifacts; verifies **GPG-signed** `SHA256SUMS` against a **pinned key** in the script.
3. Installs prerequisites (container runtime, or Temurin JRE 25 + Aerospike CE).
4. Hardens Aerospike (no published port in container mode; loopback + firewall in native).
5. Generates self-signed TLS keystore (mode 600); engine **refuses to start without TLS**.
6. Writes `/opt/hokeka/config/edge.env` (mode 600).
7. Starts service; health-checks `https://127.0.0.1:8443/actuator/health`.

> On **signature failure**, stop — do not use `--insecure-skip-signature`. Contact Hokeka.

Installer internals: [`docs/INSTALL.md`](../INSTALL.md) §2.2, [`edge-host/deploy/README.md`](../../edge-host/deploy/README.md).

---

## Post-installation

### 1. Replace TLS certificate (required for production)

The installer creates a **self-signed** cert. Replace with your CA-issued PKCS#12:

```bash
keytool -importkeystore \
  -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \
  -destkeystore /opt/hokeka/secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge
# Update EDGE_TLS_KEYSTORE_PASSWORD in .env, then restart the service / docker compose
```

For mutual TLS from API nodes: install with `--tls-client-auth need` and configure client-CA truststore.

### 2. Portal approval and pinned keys

Until a Hokeka platform admin **approves** the node in Console:

- `GET /edge/status` → `"authorization": "unauthorized"`
- `POST /edge/evaluate` → **HOLD** (fail-closed)
- No rule bundles served

After approval, set in `.env`:

```bash
CONTROLPLANE_ED25519_PUBLIC_KEY=<from Console>
CONTROLPLANE_X25519_PUBLIC_KEY=<from Console>
```

Place issued mTLS client certificate at `/opt/hokeka/secrets/edge-client.p12`.

### 3. Verify status

```bash
curl -sk https://127.0.0.1:8443/edge/status | jq
```

| Field | Required |
|-------|----------|
| `authorization` | Not `unauthorized` |
| `evaluator` | **`native`** (not `fallback-java-interpreter`) |
| `featureStore` | Not `unavailable` (velocity rules need local history) |
| health | `UP` |

### 4. Test evaluate

```bash
curl -sk https://127.0.0.1:8443/edge/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"txn_id":"test-1","pan_hash":"<hash>","amount_cents":1000,
       "currency":"KES","country_code":"KE","mcc":"5411","merchant_id":"m-1"}' | jq
```

Field names are **`snake_case`** — see [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).

### 5. Wire API nodes (separate process)

Complete [06 — PSP API dual-post integration](06-psp-api-dual-post-integration.md) so cloud alerts/cases exist.

### JEV AI (Edge → Control Plane only)

Edge Nodes **never** hold `OPENROUTER_API_KEY` or call OpenRouter. Borderline pre-auth decisions (`ALERT`/`HOLD`) may request a JEV advisory via `POST /api/v1/edge/decision` over the existing mTLS channel.

| Mode | Behaviour |
|------|-----------|
| `aiInlineMode` **OFF** (default) | Edge returns deterministic rules immediately; Control Plane runs JEV asynchronously for alerts/cases |
| `aiInlineMode` **ON** | Edge waits up to `aiInlineBudgetMs` (PSP setting, default 500ms) for JEV; falls back to rules on timeout/error |

Configure inline mode per PSP in Console → Settings → JEV AI (Platform Admin).

---

## Operations

### Day-to-day

```bash
systemctl status hokeka-edge          # native
journalctl -u hokeka-edge -f
docker compose ps && docker compose logs -f edge   # container
```

### Monitor

| Signal | Action |
|--------|--------|
| `evaluator` ≠ `native` | Native core failed to load — investigate `/opt/hokeka/lib/` |
| `featureStore: unavailable` | Aerospike down — velocity rules silently disabled |
| `authorization` change | Node de-authorised — all traffic HOLD |
| Cert expiry | Server + mTLS client certs |

### Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --version 1.3.0 ...
```

Re-run is idempotent; identity in `/opt/hokeka/data/` is preserved. Roll back by re-installing a prior `--version`. No automatic canary — upgrade one node first, verify §3, then fleet.

Artifacts come from [04 — Packages CDN](04-packages-cdn-and-edge-release.md).

---

## Uninstall

```bash
# native
sudo systemctl disable --now hokeka-edge
sudo rm /etc/systemd/system/hokeka-edge.service && sudo systemctl daemon-reload

# container
cd /opt/hokeka && docker compose down -v
```

Back up `/opt/hokeka/data/` before removal if the node may be restored.

---

## Troubleshooting

| Symptom | Doc |
|---------|-----|
| Install / signature / activation failures | [`docs/edge-install-troubleshooting.md`](../edge-install-troubleshooting.md) |
| Quick symptom table | [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) §8 |

Collect for support: `/edge/status` JSON, last hour of service logs, installed version, `uname -a`, edge id.

---

## Related

- [Installation process map](README.md)
- [`docs/edge-client-install-guide.md`](../edge-client-install-guide.md) — extended client guide
- [`docs/INSTALL.md`](../INSTALL.md) §2 — canonical edge technical reference
- [06 — Dual-post integration](06-psp-api-dual-post-integration.md)
