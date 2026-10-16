# Hokeka Edge — Client Installation Guide

**Audience:** the PSP's infrastructure or platform engineer who installs the Hokeka Edge Node on
their own servers.

**What you are installing.** A fraud and AML decisioning node that runs inside your estate. It is
the only component that evaluates your transactions. Your API nodes call it over TLS, and it answers
ALLOW / ALERT / HOLD / BLOCK in line with the authorisation. It has two local databases that only
the engine can reach:

- the **Edge Ledger** (PostgreSQL 16): every evaluation, the decision-record outbox, legal holds and
  audit tables. It is your system of record for transaction data;
- **Aerospike**: hourly velocity buckets (26 h TTL) and the active rule bundle. Nothing else.

**What leaves your network.** All traffic is outbound on TCP 443, inside mTLS, opened by the Edge:

- **decision records**, one per evaluation: IDs, action, score, rule IDs and compliance flags. No
  PAN, card hash, amount, currency, country, names or feature values;
- hourly **aggregate metrics**;
- the signed **rule bundle**, pulled down.

The full field-by-field rules are in [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md) §2.

There is no exception. The former Hokeka AI recommendation request, which sent the evaluation's
feature values for `ALERT` and `HOLD` decisions, was removed in R10c.

Planned, not built yet:

- **Edge Tunnel (R5).** The Edge will open an outbound WebSocket (WSS, TCP 443, mTLS) to
  `edge.hokeka.com`. The Console will use it to read transaction details live from your ledger, and
  each read will be audited on both sides. The Control Plane server and its nginx vhost exist; the
  Edge client does not. You will not need to open any port for it.
- **Edge config and purge (R2).** The Edge will pull your retention and other settings from the
  sealed Edge config document and purge ledger data older than your retention period. Until then
  the ledger keeps every evaluation.

### The client documentation set

| Document | Covers |
|---|---|
| **This guide** | Requirements, sizing, network, installation, verification, hardening, upgrades |
| [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md) | How to send transactions and how decisions are resolved from rules. **Read it before wiring your API nodes.** |
| [`edge-install-troubleshooting.md`](edge-install-troubleshooting.md) | Installation failure modes grouped by root cause |
| [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md) | What data may leave the Edge, and how |

> **Internal reader:** install **process map**: [`docs/install/README.md`](install/README.md).
> Edge process entry: [`docs/install/01-client-edge-node.md`](install/01-client-edge-node.md).
> Deep technical reference: [`docs/INSTALL.md`](INSTALL.md). This guide is the client-facing deep dive.

---

## 1. Choose your deployment mode

| | **Container** (default) | **Native** (`--native`) |
|---|---|---|
| What runs | Engine, Aerospike and the ledger (`postgres:16-alpine`) as containers on a private bridge network | Temurin JRE 25, Aerospike CE and PostgreSQL 16 on the host; engine as a systemd service |
| Platforms | Linux, Windows Server | **Linux only** |
| DB isolation | Aerospike and the ledger have **no published port** | Both bound to `127.0.0.1`; ports 3000 and 5432 firewalled |
| Installed for you | Container runtime, config, TLS, ledger password | JRE, Aerospike, PostgreSQL 16, config, TLS, ledger role and database, systemd unit |
| Best for | Almost everyone; the same stack on every platform | Estates whose policy forbids containers |

Choose **container** unless container runtimes are prohibited.

### 1.1 Edge Site: several nodes, one ledger

An **Edge Site** is one or more nodes of the same PSP that share one ledger and one Aerospike
cluster. A single node is a site whose site id is its edge id. For a multi-node site, run one shared
PostgreSQL 16 database and point every node at it:

```bash
export HOKEKA_LEDGER_PASSWORD='<password>'   # never passed on the command line
sudo -E bash install.sh ... \
  --ledger-url jdbc:postgresql://site-db:5432/hokeka_ledger \
  --ledger-user hokeka_edge \
  --site-id acme-eu
```

On Windows: `-LedgerUrl`, `-LedgerUser`, `-SiteId`, with the password in
`$env:HOKEKA_LEDGER_PASSWORD`. With `--ledger-url` the installer does not start a local ledger.

---

## 2. Minimum system requirements

### 2.1 Supported operating systems

| OS | Minimum version | Container mode | Native mode | Notes |
|---|---|---|---|---|
| Ubuntu | 22.04 LTS | ✅ | ✅ | Best-tested path |
| Debian | 12 (Bookworm) | ✅ | ✅ | |
| RHEL / Rocky / AlmaLinux | 9 | ✅ | ✅ | PostgreSQL 16 from the PGDG repository |
| SUSE Linux Enterprise / openSUSE | 15 SP5 | ✅ | ✅ | `zypper` |
| Arch Linux | rolling | ✅ | ⚠️ | Native works; Aerospike package is not vendor-provided |
| **Alpine Linux** | 3.19 | ✅ | ✅ x86_64 only | musl-linked core; see the note below |
| Windows Server | 2019 / 2022 | ✅ | ❌ | Aerospike has no native Windows build |
| Windows 10 / 11 | 21H2 | ✅ | ❌ | Evaluation only, not production |

> ### Alpine / musl
> A glibc shared library cannot load on musl, so a **separate musl-linked core** is published:
> `libedge_engine-linux-x86_64-musl.so`. The installer detects your C library and downloads the
> matching one.
>
> **musl on x86_64 supports both modes.** On Alpine, `--native` uses Alpine's OpenJDK 21 (Adoptium
> publishes no apk repository; 21 meets the engine's floor). Aerospike has no Alpine package, so the
> installer stages the config and points you at `aerospike.com/download` for the server. Container
> mode avoids that step.
>
> **musl on ARM is refused**, because no musl/aarch64 core is published. Use container mode there.

**CPU architecture:** `x86_64` or `aarch64`. Both are built on native runners with the Java↔Rust
boundary executed.

### 2.2 Retention

A PSP admin sets retention in **Console → Settings → Data Retention**:

- minimum and default **5 years** (FATF R.11), maximum **25 years**;
- every change goes through maker-checker (a second admin approves);
- an increase takes effect on approval; a reduction takes effect 30 days after approval.

Retention is not an installer setting. `--retention-days` is accepted and ignored with a warning.
The Control Plane publishes the value in the sealed Edge config document. The Edge consumer and the
purge job are planned (R2); until then the ledger keeps all data and only grows.

### 2.3 Ledger disk: size for 5 years

The ledger disk is the dominant cost. `install.sh --peak-tps N` prints this estimate before it
installs anything:

```
evaluations = N × 86 400 × 365 × 5
disk (GiB)  = evaluations × 2 048 bytes ÷ 2^30 × 1.3      (30% headroom)
```

| Peak TPS | Evaluations in 5 years | Ledger disk (approx.) |
|---:|---:|---:|
| 1 | 157 M | 390 GiB |
| 5 | 788 M | 1 953 GiB |
| 10 | 1 576 M | 3 909 GiB |
| 50 | 7 884 M | 19 548 GiB |
| 100 | 15 768 M | 39 097 GiB |

Read these numbers with care:

1. **They are an upper bound.** The formula assumes your peak TPS for 24 hours a day, every day.
   Use your real daily volume if you know it: `disk ≈ daily evaluations × 1 825 × 2 048 B × 1.3`.
2. **2 048 bytes per evaluation is an estimate**, not a measurement. It covers the row (IDs, typed
   columns, decision, about 0.5 KiB of features), its share of the indexes, the outbox row and
   Postgres overhead. Measure it in your pilot before you commit to storage.
3. **Longer retention scales linearly.** For 10 years, double the numbers.
4. **A full disk stops recording.** The node then answers **HOLD** with
   `failClosedCode = LEDGER_UNAVAILABLE` (fail-closed). Alert on disk usage.

`install.ps1` has no `-PeakTps` option. On Windows, use the formula above.

### 2.4 Aerospike and RAM

Aerospike now holds only velocity buckets (hourly counters per card, 26 h TTL) and the rule bundle.
Its primary index costs 64 bytes of RAM per record:

```
velocity records ≤ min(TPS × 93 600, active cards × 26)     (26 hours of buckets)
index RAM        = velocity records × 64 bytes
```

At 100 TPS sustained that is at most about 9.4 M records and 0.6 GB of index RAM. The shipped data
file is `filesize 4G`, which holds about 8 M records at an assumed 512 bytes each (about 90 TPS
sustained). Above that, raise it with `--data-file-size 16G`. The 512-byte figure is not measured.

Starting points for host size (estimates, not benchmarks; validate in the pilot):

| Tier | Sustained TPS | RAM | vCPU | Ledger disk (5 y, at that TPS all day) |
|---|---|---|---|---|
| **Evaluation** | < 5 | 8 GB | 2 | see §2.3 |
| **Small** | 10 | 16 GB | 4 | ~3.8 TiB |
| **Medium** | 50 | 32 GB | 8 | ~19 TiB |
| **Large** | 100+ | 64 GB | 16 | ~38 TiB+ |

RAM covers the JVM heap, PostgreSQL shared buffers and page cache, Aerospike and the OS. The ledger
needs fast storage: it is written on the authorisation path.

### 2.5 Software prerequisites

The installer provisions all of these. The table is what to expect, and what to pre-stage in an
air-gapped estate.

| Component | Version | Mode | Notes |
|---|---|---|---|
| Java runtime | **Temurin JRE 25** | Native | Container mode uses `eclipse-temurin:25-jre`. Java 21+ is the floor: the engine serves each transaction on a virtual thread |
| PostgreSQL | **16** | Both | Container: `postgres:16-alpine` (`POSTGRES_TAG`). Native: distro package, or PGDG where the distro has no 16. scram-sha-256 auth |
| Aerospike Server CE | 6.4 | Both | Namespace `hokeka`, `replication-factor 1` (single node) |
| Container runtime | Docker 24+ / Podman 4+, Compose 2.20+ | Container | Installed via `get.docker.com` if absent; Docker Desktop via `winget` on Windows |
| systemd | any current | Native | The unit is `hokeka-edge.service` |
| `curl` or `wget` | any | Both | Artifact download |
| `gnupg` | 2.2+ | Both | **Required**: release signature checks are fail-closed |
| glibc | 2.31+ | Native | See the musl note in §2.1 |

Aerospike wants `proto-fd-max 15000`, so the file-descriptor limit for its user must not be lower.

### 2.6 Published native cores

| Artifact | Platform | JNI boundary executed on |
|---|---|---|
| `libedge_engine-linux-x86_64.so` | glibc, x86_64 | native x86_64 runner |
| `libedge_engine-linux-aarch64.so` | glibc, ARM64 | native ARM runner |
| `libedge_engine-linux-x86_64-musl.so` | musl, x86_64 | Alpine container |
| `edge_engine-windows-x86_64.dll` | Windows | native Windows runner |

Every core has its Java↔Rust crossing run on its own platform before release.

### 2.7 Disk layout and permissions

| Path | Contents | Mode |
|---|---|---|
| `/opt/hokeka/bin/` | `edge-host.jar` | 640 |
| `/opt/hokeka/lib/` | `libedge_engine.so` | 644 |
| `/opt/hokeka/config/edge.env` (native) or `.env` (container) | Identity, Control Plane URL, enrollment code, `EDGE_LEDGER_*` | **600** |
| `secrets/` | TLS keystore, mTLS client cert, `edge-ledger.pass` | **600**, mounted read-only |
| `/opt/hokeka/data/` | `edge-identity.json`, the node's private keys | **600**, the only writable mount |
| Ledger data | Container: volume `ledger-data`. Native: the PostgreSQL 16 data directory | — |
| Aerospike data file | `/opt/aerospike/data/hokeka.dat` | — |

Back up two things:

- **The Edge Ledger.** It is the PSP's multi-year record of every evaluation. Use your normal
  PostgreSQL backup method (for example `pg_dump` or a physical backup) and test restores.
- **`/opt/hokeka/data/`.** It holds the node's pinned X25519/Ed25519 identity. Lose it and the node
  must be re-enrolled. Never recreate it by hand.

Put the ledger and the Aerospike data file on separate SSD/NVMe volumes.

---

## 3. Network requirements

### 3.1 Inbound

Nothing from the internet. The only listener is the transaction API:

| Port | Source | Purpose |
|---|---|---|
| **8443/tcp** | Your PSP API nodes **only** | Transaction API, TLS 1.3 only |

It binds to `127.0.0.1` by default. If your API nodes are on other hosts, bind the private-network
address and restrict the source:

```bash
--bind 10.0.0.5 --allow-subnet 10.0.0.0/24
```

**Never expose 8443 to the internet.** It is an internal decisioning API.

### 3.2 Outbound: TCP 443 only

| Destination | Port | Purpose | Required |
|---|---|---|---|
| `edge.hokeka.com` | 443 | Rule bundle pull, metrics, decision records (mTLS). Later also the Edge Tunnel (WSS, planned R5) | **Yes, ongoing** |
| `packages.hokeka.com` | 443 | Artifact download | Install and upgrade only |
| `registry.hokeka.com` | 443 | Container image pull | Install and upgrade, container mode |
| Distro mirrors, `get.docker.com`, `packages.adoptium.net`, PostgreSQL PGDG | 443 | Prerequisites | Install only |

Cadence: rule bundle poll every **15 s** (`EDGE_BUNDLE_POLL_INTERVAL`), metrics every **60 s**
(`EDGE_METRICS_INTERVAL`), decision-record outbox drained every **2 s**
(`EDGE_ASSESSMENT_SHIP_INTERVAL`). Records stay in the ledger outbox until the Control Plane
acknowledges them, so an outage or a restart loses nothing.

For restricted-egress estates, mirror the artifacts and use `--repo-url https://your-mirror/edge`,
or install offline with `--offline`.

### 3.3 Never exposed

- **Aerospike** ports 3000–3003. Container mode publishes no port; native mode binds loopback and
  firewalls 3000. **Aerospike Community Edition has no authentication**, so network isolation is
  its access control.
- **PostgreSQL** port 5432. Container mode publishes no port; native mode binds `127.0.0.1` and
  denies 5432 from the network (ufw or iptables). The ledger role also needs its scram-sha-256
  password. A shared site database (§1.1) must be reachable only from that site's Edge Nodes.

### 3.4 TLS and proxies

The transaction API is **TLS 1.3 only**, terminated inside the engine. There is no plaintext
listener. The engine **refuses to start without a keystore**.

A TLS-intercepting proxy on the outbound path breaks the Control Plane channel: it is mTLS with a
pinned server identity. Exempt `edge.hokeka.com` from interception. The planned tunnel uses an HTTP/1.1
`Upgrade`, so the proxy must also pass WebSocket upgrades to that host.

---

## 4. Pre-installation checklist

- [ ] Ledger disk sized for 5 years (§2.3) on a separate, backed-up SSD/NVMe volume
- [ ] Host meets the tier's RAM and CPU (§2.4)
- [ ] OS on the supported list; **not** musl on ARM if using `--native`
- [ ] Outbound TCP 443 to `edge.hokeka.com` open and **not** TLS-intercepted; no other egress needed after install
- [ ] Inbound 8443 reachable from your API nodes and nowhere else
- [ ] Root or Administrator access
- [ ] From the Hokeka portal: your **numeric** PSP id, your edge id, and a fresh **enrollment code**
- [ ] Your CA-issued server certificate (the self-signed one is not for production, §6.1)
- [ ] Backups cover the ledger and `/opt/hokeka/data/`
- [ ] For a multi-node site: the shared PostgreSQL 16 database, its user, and `HOKEKA_LEDGER_PASSWORD`

> **`--pspid` takes the numeric portal id** (for example `42`), not a name or slug. Activation
> compares it with the enrollment code's owner and returns **403** on mismatch. Copy the command from
> the portal's setup wizard.

---

## 5. Installation

### 5.1 Linux — container mode (recommended)

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh -o install.sh

# Read it before running it as root.
less install.sh

sudo bash install.sh \
  --pspid 42 \
  --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234 \
  --bind 10.0.0.5 \
  --allow-subnet 10.0.0.0/24 \
  --peak-tps 20
```

### 5.2 Linux — native mode

```bash
sudo bash install.sh --native \
  --pspid 42 --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234
```

### 5.3 Windows Server (Administrator PowerShell)

```powershell
.\install.ps1 -PspId 42 -EdgeId acme-eu-1 `
  -ControlPlane https://edge.hokeka.com -EnrollmentCode ABCD-1234
```

### 5.4 Pinning a version and other flags

For production change control, pin a version:

```bash
sudo bash install.sh --version 1.2.3 ...
```

Other flags: `--channel beta`, `--repo-url URL`, `--offline`, `--data-dir PATH`,
`--tls-client-auth need`, `--peak-tps N`, `--data-file-size 16G`, `--ledger-url URL`,
`--ledger-user USER`, `--site-id ID`, `--edge-image REF` (override the container image, default
`registry.hokeka.com/hokeka/edge-engine:0.1.0`, for a private mirror or a pinned digest).

### 5.5 What the installer does

1. Detects distro, package manager, CPU architecture and C library.
2. Downloads `SHA256SUMS`, **verifies its GPG signature against a pinned key**, then downloads and
   checksum-verifies each artifact. Any mismatch deletes the file and aborts.
3. Installs prerequisites: a container runtime, or JRE + Aerospike + PostgreSQL 16.
4. Isolates the databases (no published ports, or loopback + firewall).
5. Generates the ledger password into `secrets/edge-ledger.pass` (mode 600), or reads
   `HOKEKA_LEDGER_PASSWORD` for a shared database. Re-runs reuse the existing password.
6. Native mode: creates the ledger role and the `hokeka_ledger` database.
7. Generates a self-signed EC P-384 TLS keystore, mode 600.
8. Writes `.env` or `/opt/hokeka/config/edge.env`, mode 600, with `EDGE_LEDGER_URL`,
   `EDGE_LEDGER_USER`, `EDGE_LEDGER_PASSWORD`, `EDGE_LEDGER_FAIL_CLOSED=true` and `EDGE_SITE_ID`.
9. Stages the native core into `/opt/hokeka/lib`.
10. Starts the service and health-checks it.

At start-up the engine creates its ledger schema (`edge_ledger`, `EDGE_LEDGER_SCHEMA`) with its own
migrations and creates monthly partitions ahead of time. If the ledger is down at boot, the node
starts anyway, keeps retrying, and answers HOLD until the ledger is writable.

> **On signature failure, stop.** It means the checksum manifest was not signed by the Hokeka
> release key. Do not retry with `--insecure-skip-signature`; contact Hokeka. Nothing has been
> installed at that point.

---

## 6. Post-installation

### 6.1 Replace the TLS certificate — required for production

The installer generates a **self-signed** certificate so the engine can start. Replace it with one
from your CA, keeping the path and alias:

```bash
keytool -importkeystore \
  -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \
  -destkeystore /opt/hokeka/secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge

# update EDGE_TLS_KEYSTORE_PASSWORD in .env, then restart
```

For mutual TLS from your API nodes, install with `--tls-client-auth need` and point
`SERVER_SSL_TRUST_STORE` / `SERVER_SSL_TRUST_STORE_PASSWORD` at your client-CA truststore. We
recommend this for production: only holders of a certificate you issued can submit transactions.

### 6.2 Activate the node — it is fail-closed until you do

On first boot the node generates its key pairs and enrolls. **Until a Hokeka admin approves it:**

- `GET /edge/status` reports `"authorization": "unauthorized"`
- `POST /edge/evaluate` returns **HOLD**; an unapproved node never issues an ALLOW
- no rule bundle is served (403)

Approve it in the portal, then add the pinned Control Plane keys to your `.env`:

```bash
CONTROLPLANE_ED25519_PUBLIC_KEY=<from the portal>   # verifies every rule bundle
CONTROLPLANE_X25519_PUBLIC_KEY=<from the portal>    # encrypts metrics and decision records
```

and place the issued mTLS client certificate at `/opt/hokeka/secrets/edge-client.p12`. Without the
pinned keys the node accepts no bundles and ships nothing.

### 6.3 Verify

```bash
curl -sk https://127.0.0.1:8443/edge/status | jq
```

| Field | Required value | If wrong |
|---|---|---|
| `authorization` | not `unauthorized` | Node not yet approved in the portal (§6.2) |
| `evaluator` | **`native`** | `fallback-java-interpreter` means the Rust core did not load; see §8 |
| `featureStore` | not `unavailable` | Aerospike unreachable; **velocity rules cannot fire** |
| `ledger.state` | **`OK`** | `DOWN` or `NOT_CONFIGURED`: every evaluation answers HOLD. Check `docker compose ps ledger` or `systemctl status postgresql` (`postgresql-16` on RHEL family) and the `EDGE_LEDGER_*` values; `ledger.reason` says why |
| `ledger.outboxBacklog` | small and falling | Decision records are not reaching the Control Plane; check the channel (§3) |
| health | `UP` | `systemctl status hokeka-edge` / `docker compose ps` |

> **`featureStore: unavailable` is not a benign warning.** The engine keeps deciding on
> caller-supplied features, so it looks healthy from outside, but velocity rules cannot fire. Alert
> on this field.

### 6.4 Send a test transaction

```bash
curl -sk https://127.0.0.1:8443/edge/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"txn_id":"test-1","pan_hash":"<hash>","amount_cents":1000,
       "currency":"KES","country_code":"KE","mcc":"5411","merchant_id":"m-1"}' | jq
```

An approved node returns a decision and writes it to the ledger. A node that returns HOLD for
everything is unapproved, or its ledger is down.

> **Field names are `snake_case` and are matched literally.** `pan_hash`, not `panHash`. A misnamed
> field is not an error; it is a feature the rules never see. See
> [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md) before wiring your API nodes.

> **`txn_id` must not look like a card number.** A 13–19-digit value that passes the Luhn check is
> refused with **422** `TXN_REF_LOOKS_LIKE_PAN`, and nothing is evaluated or stored.

---

## 7. Operating the node

### 7.1 Day-to-day

```bash
systemctl status hokeka-edge        # native
journalctl -u hokeka-edge -f
docker compose ps                   # container
docker compose logs -f edge
docker compose logs -f ledger
```

### 7.2 What to monitor

| Signal | Why |
|---|---|
| `evaluator` ≠ `native` | Degraded to the slower Java path |
| `featureStore` = `unavailable` | Velocity rules cannot fire (§6.3) |
| HOLD with `LEDGER_UNAVAILABLE` | The ledger is down, full or too slow to write |
| Ledger disk usage | Grows until purge is built (R2); compare with §2.3 |
| `authorization` change | Node de-authorised centrally; it will HOLD |
| Aerospike disk vs `filesize` | Eviction begins when the file fills |
| Decision latency p99 | It is on the authorisation path |
| Certificate expiry | Server certificate and mTLS client certificate |

### 7.3 Restart behaviour

The verified rule bundle is kept on your premises: Aerospike set `rules` / key `active`, and
`rule-bundle.ir` beside the node identity (`EDGE_RULE_BUNDLE_FILE` overrides the path). Start-up
loads that file even when Aerospike is down, so a restarted node enforces rules at once. A Control
Plane outage does not stop an enrolled node from deciding, and decision records wait in the ledger
outbox until the Control Plane is back.

### 7.4 Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --native --version 1.3.0
```

Re-running is idempotent. The TLS keystore, `edge.env`, the ledger password, the ledger data and
the pinned node identity are kept. Only the jar and native core are replaced, then the service
restarts. Ledger schema migrations run at start-up.

**Rolling back** means re-installing an earlier `--version`. There is no automatic revert and no
canary, so upgrade one node first and verify §6.3 before the rest.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Install aborts: signature verification failed | Manifest not signed by the Hokeka key | **Stop.** Contact Hokeka. Do not bypass |
| Install aborts: "no pinned release signing key" | Unofficial or older installer build | Get an official installer from Hokeka |
| `--ledger-url needs the database password in HOKEKA_LEDGER_PASSWORD` | Shared ledger without a password | Export `HOKEKA_LEDGER_PASSWORD` and run with `sudo -E` |
| Engine refuses to start, TLS error | Missing keystore or password | By design. Check `/opt/hokeka/secrets/` and `EDGE_TLS_KEYSTORE_PASSWORD` |
| `evaluator: fallback-java-interpreter` | Native core absent or unloadable | Check `/opt/hokeka/lib/libedge_engine.so`; on musl see §2.1; check the arch |
| `featureStore: unavailable` | Aerospike down or unreachable | `docker compose ps aerospike`; check the data file has not filled |
| Everything returns HOLD | Node not approved, or ledger down | Approve in the portal (§6.2); check the ledger service and disk |
| No rule bundles arriving | Missing pinned keys, missing mTLS cert, or TLS interception | §3.4 and §6.2 |
| 403 during activation | `--pspid` wrong or not numeric | Use the numeric portal id (§4) |
| 422 `TXN_REF_LOOKS_LIKE_PAN` | `txn_id` is a card number | Send your own transaction reference, never the PAN |

Collect for a support ticket: `/edge/status` output, `journalctl -u hokeka-edge --since -1h` (or
`docker compose logs`), the installed version, distro and arch (`uname -a`), and your edge id.
More: [`edge-install-troubleshooting.md`](edge-install-troubleshooting.md).

---

## 9. Uninstall

```bash
# native
sudo systemctl disable --now hokeka-edge
sudo rm /etc/systemd/system/hokeka-edge.service && sudo systemctl daemon-reload

# container (keeps the volumes, including the ledger)
cd /opt/hokeka && docker compose down
```

The ledger holds records you must keep for your retention period. `docker compose down -v` deletes
the `ledger-data` volume. Back up the ledger and `/opt/hokeka/data/` before you remove anything, and
only delete ledger data when your retention obligations allow it.

---

## 10. Security summary

| Guarantee | Enforced by | Depends on you |
|---|---|---|
| Transaction data stays in your estate | Only decision records (IDs, action, score, rule IDs) and aggregates are shipped | — |
| No inbound connection from Hokeka | The Edge opens every connection, outbound on 443 | Not opening ports yourself |
| No plaintext transaction API | TLS 1.3 in-process; the engine refuses to start without a keystore | Replacing the self-signed cert (§6.1) |
| The databases are reachable only by the engine | No published ports / loopback + firewall; ledger password | Not publishing 3000 or 5432; isolating a shared site database |
| Only your API nodes can submit transactions | `--bind` + `--allow-subnet`, and mTLS with `--tls-client-auth need` | Configuring these |
| Rules cannot be forged or replayed | Ed25519 signature and replay guard on every bundle | Pinning the Control Plane keys (§6.2) |
| An unapproved node cannot approve itself | Fail-closed HOLD until portal approval | — |
| No decision without a record | Fail-closed HOLD when the ledger cannot be written (`EDGE_LEDGER_FAIL_CLOSED=true`) | Sizing and monitoring the ledger disk |
| Artifacts cannot be tampered with in transit | GPG-signed checksums, verified against a pinned key | Not using `--insecure-skip-signature` |
