# Hokeka Edge — Client Installation Guide

**Audience:** the PSP's infrastructure or platform engineer installing the Hokeka Edge engine on
their own servers.

**What you are installing.** A self-contained fraud-decisioning node that runs entirely inside your
estate. Your API nodes call it over TLS; it answers ALLOW / ALERT / HOLD / BLOCK in-line with the
authorisation. It keeps its own transaction and velocity history in a local Aerospike instance that
nothing but the engine can reach.

**What leaves your network.** Aggregate counts only. Transactions, PANs and velocity history never
leave your estate. The outbound channel carries two things: signed rule bundles pulled *down*, and
aggregate metrics pushed *up*, both inside mTLS.

### The client documentation set

| Document | Covers |
|---|---|
| **This guide** | Requirements, sizing, network, installation, verification, hardening, upgrades |
| `docs/edge-transaction-evaluation.md` | How to send transactions and how decisions are resolved from rules — **read before wiring your API nodes** |
| `docs/edge-install-troubleshooting.md` | Installation failure modes grouped by root cause |

> **Internal reader:** install **process map** — [`docs/install/README.md`](install/README.md).
> Edge process entry: [`docs/install/01-client-edge-node.md`](install/01-client-edge-node.md).
> Deep technical reference: `docs/INSTALL.md`. This guide is the client-facing deep dive.

---

## 1. Choose your deployment mode

| | **Container** (default) | **Native** (`--native`) |
|---|---|---|
| What runs | Engine + Aerospike as containers on a private bridge network | Temurin JRE 25 + Aerospike CE installed on the host, engine as a systemd service |
| Platforms | Linux, Windows Server | **Linux only** |
| DB isolation | Aerospike has **no published port** at all | Aerospike bound to `127.0.0.1` + firewalled |
| Prerequisites installed for you | Container runtime, Aerospike, config, TLS | JRE, Aerospike, config, TLS, systemd unit |
| Best for | Almost everyone; identical stack on every platform | Estates whose policy forbids containers |

Choose **container** unless container runtimes are prohibited. It is the same stack everywhere, which
makes support tractable.

---

## 2. Minimum system requirements

### 2.1 Supported operating systems

| OS | Minimum version | Container mode | Native mode | Notes |
|---|---|---|---|---|
| Ubuntu | 22.04 LTS | ✅ | ✅ | Best-tested path |
| Debian | 12 (Bookworm) | ✅ | ✅ | |
| RHEL / Rocky / AlmaLinux | 9 | ✅ | ✅ | |
| SUSE Linux Enterprise / openSUSE | 15 SP5 | ✅ | ✅ | `zypper` |
| Arch Linux | rolling | ✅ | ⚠️ | Native works; Aerospike package is not vendor-provided |
| **Alpine Linux** | 3.19 | ✅ | ✅ x86_64 only | musl-linked core; see the note below |
| Windows Server | 2019 / 2022 | ✅ | ❌ | Aerospike has no native Windows build |
| Windows 10 / 11 | 21H2 | ✅ | ❌ | Evaluation only, not production |

> ### Alpine / musl
> A glibc shared library cannot load on musl, so a **separate musl-linked core** is published:
> `libedge_engine-linux-x86_64-musl.so`. The installer detects your C library and downloads the
> matching one automatically — you do not select it.
>
> **musl on x86_64 supports both modes.** On Alpine, `--native` uses Alpine's OpenJDK 21 (Adoptium
> publishes no apk repository; 21 meets the engine's floor). Aerospike has no Alpine package, so the
> installer stages the config and points you at `aerospike.com/download` for the server itself —
> container mode avoids that step entirely and is still the easier path.
>
> **musl on ARM is refused**, because no musl/aarch64 core is published. Use container mode there.

**CPU architecture:** `x86_64` or `aarch64`. Both are built on native runners with the Java↔Rust
boundary actually executed, so both are validated end to end.

### 2.2 How to size the box

The dominant cost is **Aerospike's primary index, which lives in RAM** at a fixed **64 bytes per
record**. Everything else is small by comparison. Two record populations matter:

```
transaction records = TPS × 86,400 × retention_days
velocity records    ≈ active_cards × 24          (hourly buckets, 26 h TTL)

index RAM = (transaction records + velocity records) × 64 bytes
```

**Retention is the lever that matters.** It multiplies your record count linearly, so halving
retention halves the RAM requirement. Decide retention before you size the machine, not after.

### 2.3 Sizing tiers

Worked from the formula above at **30-day retention**. Find the row matching your sustained
authorisation rate and add headroom for your peak.

| Tier | Sustained TPS | Records @30d | Index RAM | **Total RAM** | vCPU | Disk for the data file |
|---|---|---|---|---|---|---|
| **Evaluation** | < 5 | ~13 M | ~0.8 GB | **4 GB** | 2 | 16 GB (the shipped default) |
| **Small** | 10 | ~26 M | ~1.7 GB | **8 GB** | 4 | 32 GB |
| **Medium** | 50 | ~130 M | ~8.3 GB | **32 GB** | 8 | 128 GB |
| **Large** | 200 | ~518 M | ~33 GB | **64 GB** | 16 | 512 GB |

Total RAM = index + JVM heap + Aerospike overhead + OS, rounded to a sane instance size.

> **Two things to check rather than trust.**
>
> 1. **These are structural estimates, not benchmarks.** The record counts and the 64-bytes-per-record
>    index cost are exact; the *disk* column assumes an average on-device record of ~512 bytes, which
>    has not been measured against production traffic. Validate disk sizing against your own volumes
>    during the pilot before committing to a tier.
> 2. **The shipped `filesize 16G` only covers the Evaluation tier.** `aerospike/aerospike.conf`
>    ships with a 16 GB data file. At ~512 B/record that is roughly 33 M records — about 13 TPS at
>    30-day retention. **If you are Small or above you must raise `filesize` before go-live**, or
>    Aerospike will begin evicting data once the file fills.

### 2.4 Retention

**Default: 90 days.** One flag drives everything:

```bash
sudo ./install.sh --retention-days 90 --peak-tps 50 ...
```

`--retention-days` sets **both** the edge's retention and the Aerospike namespace `default-ttl`, so
the database and the engine cannot drift apart. `--peak-tps` sizes the data file from throughput ×
retention (with 30% headroom) and reports the RAM the primary index will need before you commit to
the host. `--data-file-size 256G` overrides the calculation outright.

Confirm the retention your compliance obligations require before installing — it drives RAM and disk
linearly. At 50 TPS the difference between 30 and 90 days is roughly 8 GB of index RAM versus 25 GB.

> **If you are upgrading a node installed before this was unified:** the two values disagreed
> (`default-ttl 30d` in the namespace, 90 days in the engine). Effective retention was 90 days —
> every record is written with an explicit TTL, which overrides the namespace default — but capacity
> planning based on the config file was wrong by 3×. Re-run the installer with an explicit
> `--retention-days` to bring them into line.

### 2.5 Software prerequisites

The installer provisions all of these; the table is what to expect, and what to pre-stage in an
air-gapped estate.

| Component | Version | Mode | Notes |
|---|---|---|---|
| Java runtime | **Temurin JRE 25** | Native | Container mode uses `eclipse-temurin:25-jre` internally. Java 21+ is a hard floor — the engine serves each transaction on a **virtual thread** |
| Aerospike Server CE | 6.4 | Both | Namespace `hokeka`, `replication-factor 1` (single node) |
| Container runtime | Docker 24+ / Podman 4+ | Container | Installed via `get.docker.com` if absent; Docker Desktop via `winget` on Windows |
| systemd | any current | Native | The unit is `hokeka-edge.service` |
| `curl` or `wget` | any | Both | For artifact download |
| `gnupg` | 2.2+ | Both | **Required** — release signature verification is fail-closed |
| glibc | 2.31+ | Native | See the musl warning in §2.1 |

Aerospike also wants `proto-fd-max 15000`, so ensure the file-descriptor limit is not lower than
that for its user.

### 2.6 Published native cores

| Artifact | Platform | JNI boundary executed on |
|---|---|---|
| `libedge_engine-linux-x86_64.so` | glibc, x86_64 | native x86_64 runner |
| `libedge_engine-linux-aarch64.so` | glibc, ARM64 | native ARM runner |
| `libedge_engine-linux-x86_64-musl.so` | musl, x86_64 | Alpine container |
| `edge_engine-windows-x86_64.dll` | Windows | native Windows runner |

Every core has its Java↔Rust crossing exercised on its own platform before release — a core whose
JNI boundary has never run is exactly how a silent fallback to the slow Java interpreter ships
unnoticed.

### 2.7 Disk layout and permissions

| Path | Contents | Mode |
|---|---|---|
| `/opt/hokeka/bin/` | `edge-host.jar` | 640 |
| `/opt/hokeka/lib/` | `libedge_engine.so` | 644 |
| `/opt/hokeka/config/edge.env` | Identity, control-plane URL, enrollment code | **600** |
| `/opt/hokeka/secrets/` | TLS keystore, mTLS client cert | **600**, mounted read-only |
| `/opt/hokeka/data/` | `edge-identity.json` — the node's private keys | **600**, the only writable mount |
| Aerospike data file | `/opt/aerospike/data/hokeka.dat` | — |

**`/opt/hokeka/data/` is the one directory you must back up and must never recreate.** It holds the
node's pinned X25519/Ed25519 identity. Lose it and the node must be re-enrolled from the portal.

Use a separate volume for the Aerospike data file, sized per §2.3. Prefer SSD/NVMe — it is on the
authorisation path.

---

## 3. Network requirements

### 3.1 Inbound

| Port | Source | Purpose |
|---|---|---|
| **8443/tcp** | Your PSP API nodes **only** | Transaction API, TLS 1.3 only |

Bound to `127.0.0.1` by default. If your API nodes are on other hosts, bind the private-network
address and restrict the source:

```bash
--bind 10.0.0.5 --allow-subnet 10.0.0.0/24
```

**Never expose 8443 to the internet.** It is an internal decisioning API, not a public endpoint.

### 3.2 Outbound

| Destination | Port | Purpose | Required |
|---|---|---|---|
| `edge.hokeka.com` | 443 | Rule bundle pull + aggregate metrics push (mTLS) | **Yes, ongoing** |
| `packages.hokeka.com` | 443 | Artifact download | Install/upgrade only |
| `registry.hokeka.com` | 443 | Container image pull | Install/upgrade, container mode |
| Distro mirrors, `get.docker.com`, `packages.adoptium.net` | 443 | Prerequisites | Install only |

Poll cadence: rule bundles every **15 s**, metrics every **60 s**, 20 s request timeout.

For restricted-egress estates, mirror the artifacts and point the installer at them with
`--repo-url https://your-mirror/edge`, or install fully offline with `--offline`.

### 3.3 Never exposed

Aerospike's ports — **3000** (service), 3001 (fabric), 3002 (heartbeat), 3003 (info) — must not be
reachable by anything but the engine. Container mode enforces this by publishing no port at all;
native mode binds loopback and firewalls 3000. **Aerospike Community Edition has no
authentication**, so this network isolation *is* the access control. If you need DB-level
credentials and TLS as defence-in-depth, use Aerospike Enterprise and enable its `security` stanza.

### 3.4 TLS and proxies

The transaction API is **TLS 1.3 only**, terminated inside the engine — no sidecar, no plaintext
listener. Three AEAD suites are permitted. The engine **refuses to start without a keystore**.

A TLS-intercepting proxy on the outbound path will break the control-plane channel: it is mTLS with
a pinned server identity, and interception is indistinguishable from an attack. Allow-list
`edge.hokeka.com` to bypass interception.

---

## 4. Pre-installation checklist

Work through this before running anything.

- [ ] Retention decided (§2.4) and `filesize` sized accordingly (§2.3)
- [ ] Host meets the tier's RAM/CPU/disk; separate volume for Aerospike data
- [ ] OS on the supported list; **not** musl if using `--native`
- [ ] `x86_64` (not ARM in production — §2.6)
- [ ] Outbound 443 to `edge.hokeka.com` open and **not** TLS-intercepted
- [ ] Inbound 8443 reachable from your API nodes and nowhere else
- [ ] Root/Administrator access
- [ ] From the Hokeka portal: your **numeric** PSP id, your chosen edge id, and a fresh **enrollment code**
- [ ] Your CA-issued server certificate ready (the installer's self-signed cert is not for production — §6.1)
- [ ] Backup covers `/opt/hokeka/data/`

> **`--pspid` takes the numeric portal id** (e.g. `42`), not a name or slug. Activation compares it
> strictly against the enrollment code's owner and returns **403** on mismatch. Copy the command
> straight from the portal's setup wizard rather than composing it by hand.

---

## 5. Installation

### 5.1 Linux — container mode (recommended)

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh -o install.sh

# Read it before running it as root. It is designed to be read.
less install.sh

sudo bash install.sh \
  --pspid 42 \
  --edgeid acme-eu-1 \
  --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234 \
  --bind 10.0.0.5 \
  --allow-subnet 10.0.0.0/24
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

### 5.4 Pinning a version

For production change control, pin explicitly rather than tracking a channel:

```bash
sudo bash install.sh --version 1.2.3 ...
```

Useful flags: `--channel beta`, `--repo-url URL`, `--offline`, `--data-dir PATH`,
`--tls-client-auth need`, `--retention-days N`, `--peak-tps N`, `--data-file-size 256G`,
`--edge-image REF` (override the container image reference the installer pulls and uses to
generate the TLS keystore — defaults to `registry.hokeka.com/hokeka/edge-engine:0.1.0`; use this to
point at a private mirror or pin a specific digest).

### 5.5 What the installer does

1. Detects distro, package manager and CPU architecture.
2. Downloads `SHA256SUMS`, **verifies its GPG signature against a pinned key**, then downloads and
   checksum-verifies each artifact. Any mismatch deletes the file and aborts.
3. Installs prerequisites (runtime, or JRE + Aerospike).
4. Hardens the datastore (no published port / loopback + firewall).
5. Generates a self-signed EC P-384 TLS keystore, mode 600.
6. Writes `/opt/hokeka/config/edge.env`, mode 600.
7. Stages the native core into `/opt/hokeka/lib`.
8. Starts the service and health-checks it.

> **On signature failure, stop.** A signature error is not a network glitch — it means the checksum
> manifest was not signed by the Hokeka release key. Do not retry with
> `--insecure-skip-signature`; contact Hokeka. Nothing has been installed at that point.

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
`SERVER_SSL_TRUST_STORE` / `SERVER_SSL_TRUST_STORE_PASSWORD` at your client-CA truststore. This is
the recommended production posture: only holders of a certificate you issued can submit
transactions.

### 6.2 Activate the node — it is fail-closed until you do

On first boot the node generates its keypairs and enrolls. **Until a Hokeka admin approves it:**

- `GET /edge/status` reports `"authorization": "unauthorized"`
- `POST /edge/evaluate` returns **HOLD** — an unapproved node never issues an ALLOW
- no rule bundle is served (403)

Approve it in the portal, then add the pinned control-plane keys to your `.env`:

```bash
CONTROLPLANE_ED25519_PUBLIC_KEY=<from the portal>   # verifies every rule bundle
CONTROLPLANE_X25519_PUBLIC_KEY=<from the portal>    # encrypts outbound metrics
```

and place the issued mTLS client certificate at `/opt/hokeka/secrets/edge-client.p12`. Without the
pinned keys the node accepts no bundles and ships no metrics.

### 6.3 Verify

```bash
curl -sk https://127.0.0.1:8443/edge/status | jq
```

Check all four:

| Field | Required value | If wrong |
|---|---|---|
| `authorization` | not `unauthorized` | Node not yet approved in the portal (§6.2) |
| `evaluator` | **`native`** | `fallback-java-interpreter` means the Rust core did not load — see §8 |
| `featureStore` | not `unavailable` | Aerospike unreachable; **velocity rules cannot fire** |
| health | `UP` | `systemctl status hokeka-edge` / `docker compose ps` |

> **`featureStore: unavailable` is not a benign warning.** The engine fails soft and keeps deciding
> on caller-supplied features, so it looks healthy from the outside — but velocity rules silently
> cannot fire. Alert on this field.

### 6.4 Send a test transaction

```bash
curl -sk https://127.0.0.1:8443/edge/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"txn_id":"test-1","pan_hash":"<hash>","amount_cents":1000,
       "currency":"KES","country_code":"KE","mcc":"5411","merchant_id":"m-1"}' | jq
```

An approved node returns a decision. A node still returning HOLD for everything is unapproved.

> **Field names are `snake_case` and are matched literally.** `pan_hash`, not `panHash`. A misnamed
> field is not an error — it is simply a feature the rules never see, so rules depending on it
> silently never fire. See the integration guide (`docs/edge-transaction-evaluation.md`) before
> wiring your API nodes.

---

## 7. Operating the node

### 7.1 Day-to-day

```bash
systemctl status hokeka-edge        # native
journalctl -u hokeka-edge -f
docker compose ps                   # container
docker compose logs -f edge
```

### 7.2 What to monitor

| Signal | Why |
|---|---|
| `evaluator` ≠ `native` | Silently degraded to the slower Java path |
| `featureStore` = `unavailable` | Velocity rules cannot fire (§6.3) |
| `authorization` change | Node deauthorised centrally — it will HOLD |
| Aerospike disk vs `filesize` | Data eviction begins when the file fills |
| Decision latency p99 | On the authorisation path |
| Certificate expiry | Both the server cert and the mTLS client cert |

### 7.3 Restart behaviour

The verified rule bundle is kept on the PSP premises: Aerospike set `rules` / key `active`, and
`rule-bundle.ir` beside the node identity (`EDGE_RULE_BUNDLE_FILE` overrides the path). Startup
loads that file even when Aerospike is down, so a restart resumes enforcing immediately rather than
holding traffic until the next successful poll. A control-plane outage does not stop an already
enrolled node from deciding.

### 7.4 Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --native --version 1.3.0
```

Re-running is safe and idempotent. The TLS keystore, `edge.env` and the pinned node identity are
preserved; only the jar and native core are replaced, then the service restarts.

**Rolling back** means re-installing a pinned earlier `--version`. There is no automatic
health-check-and-revert and no canary support, so upgrade one node first and verify §6.3 before
proceeding across a fleet.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Install aborts: signature verification failed | Manifest not signed by the Hokeka key | **Stop.** Contact Hokeka. Do not bypass |
| Install aborts: "no pinned release signing key" | Unofficial/older installer build | Obtain an official installer from Hokeka |
| Engine refuses to start, TLS error | Missing keystore or password | By design — the engine never serves plaintext. Check `/opt/hokeka/secrets/` and `EDGE_TLS_KEYSTORE_PASSWORD` |
| `evaluator: fallback-java-interpreter` | Native core absent or unloadable | Check `/opt/hokeka/lib/libedge_engine.so`; on musl see §2.1; check arch matches |
| `featureStore: unavailable` | Aerospike down or unreachable | `docker compose ps aerospike`; check the data file has not filled |
| Everything returns HOLD | Node not approved | Approve in the portal (§6.2) |
| No rule bundles arriving | Missing pinned keys, missing mTLS cert, or TLS interception | §3.4 and §6.2 |
| 403 during activation | `--pspid` wrong or not numeric | Use the numeric portal id (§4) |
| Aerospike evicting data | `filesize` too small for retention | Raise `filesize` per §2.3 and restart |

Collect for a support ticket: `/edge/status` output, `journalctl -u hokeka-edge --since -1h` (or
`docker compose logs`), the installed version, distro and arch (`uname -a`), and your edge id.

---

## 9. Uninstall

```bash
# native
sudo systemctl disable --now hokeka-edge
sudo rm /etc/systemd/system/hokeka-edge.service && sudo systemctl daemon-reload

# container
cd /opt/hokeka && docker compose down -v
```

Then remove `/opt/hokeka`. **Back up `/opt/hokeka/data/` first if the node may be restored** — it
holds the node identity, and without it the node must be re-enrolled.

---

## 10. Security summary

What the design guarantees, and what it depends on you doing:

| Guarantee | Enforced by | Depends on you |
|---|---|---|
| Transaction data never leaves your estate | Only aggregates are shipped | — |
| No plaintext transaction API | TLS 1.3 in-process; the engine refuses to start without a keystore | Replacing the self-signed cert (§6.1) |
| The database is reachable only by the engine | No published port / loopback + firewall | Not publishing port 3000 yourself |
| Only your API nodes can submit transactions | `--bind` + `--allow-subnet`, and mTLS with `--tls-client-auth need` | Configuring these; defaults are permissive within the host |
| Rules cannot be forged or replayed | Ed25519 signature + replay guard on every bundle | Pinning the control-plane keys (§6.2) |
| An unapproved node cannot approve itself | Fail-closed HOLD until portal approval | — |
| Artifacts cannot be tampered with in transit | GPG-signed checksums, verified against a pinned key | Not using `--insecure-skip-signature` |
