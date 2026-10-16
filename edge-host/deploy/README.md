# Hokeka Edge — one-command deployment

Cross-platform installers that provision **every prerequisite** (container runtime or native Java +
Aerospike), bring up the edge and its feature store, and secure the database so **only the edge app
can reach it**.

| Platform | Script | DB |
|----------|--------|----|
| Any Linux distro (Debian/Ubuntu, RHEL/Rocky/Alma, SUSE, Arch, Alpine) | `sudo ./install.sh …` | container (default) or native Aerospike |
| Windows Server / 10 / 11 | `.\install.ps1 …` (as Administrator) | container (Aerospike has no native Windows build) |

## Quick start

```bash
# Linux — containerised (recommended), DB isolated on a private network:
sudo ./install.sh --pspid acme --edgeid acme-eu-1 --controlplane https://edge.hokeka.com \
     --enrollment-code ABCD-1234

# Linux — native (no containers): Temurin JRE 25 + Aerospike CE, DB bound to 127.0.0.1 + firewalled:
sudo ./install.sh --native --pspid acme --edgeid acme-eu-1
```
```powershell
# Windows (Administrator):
.\install.ps1 -PspId acme -EdgeId acme-eu-1 -ControlPlane https://edge.hokeka.com -EnrollmentCode ABCD-1234
```

The Linux script auto-detects the distro/package manager and installs Docker (or uses Podman) if no
runtime is present; the Windows script installs Docker Desktop via `winget` if needed.

## How "the database is only accessible by this app" is enforced

**Container path (default, all platforms):** Aerospike runs on a **private bridge network with no
published host port** (`docker-compose.yml`). Nothing on the host or the wider network can open a
socket to it — only the `edge` container, by service name over the internal network. The edge's own
API is published bound to `127.0.0.1` by default (widen with `--bind`/`--allow-subnet`).

**Native path (Linux `--native`):** Aerospike is bound to `127.0.0.1` in `aerospike.conf` and port
`3000` is firewalled (ufw / firewalld / iptables) so it is unreachable from the network; only local
processes (the edge) connect. The PostgreSQL 16 ledger is treated the same way on port 5432.

## Network: egress on 443 only, no inbound ports

- **Outbound:** TCP 443 to the Control Plane (`edge.hokeka.com`, mTLS) for bundle pulls, metrics
  and decision records; TCP 443 to `packages.hokeka.com` / `registry.hokeka.com` at install and
  upgrade time. Nothing else after install.
- **Inbound:** none from the internet. The only listener is the 8443 transaction API for the PSP's
  own API nodes, bound to `127.0.0.1` by default (`--bind`, `--allow-subnet`).
- **Edge Tunnel (planned, R5):** the Edge will open an outbound WebSocket (WSS on 443, subprotocol
  `hokeka-tunnel.v1`) to `edge.hokeka.com/api/v1/edge/tunnel` so the Console can read ledger rows
  live. The Control Plane endpoint and its nginx vhost (`BACKEND/nginx/edge-tunnel.conf`) exist; the
  Edge client does not. It needs no extra firewall rule beyond 443 egress, but TLS-intercepting
  proxies must pass WebSocket upgrades to that host.

## Edge Ledger — PostgreSQL 16, the site's system of record

Every `POST /edge/evaluate` is recorded on the **Edge Ledger** (PostgreSQL 16) together with its
decision record v2, in one local transaction (`docs/EDGE_DATA_RESIDENCY_CONTRACT.md` §4.3). The
decision record (ids and outcome only — no PAN hash, amount, currency, country, customer data or
reason text) is the only per-transaction data that leaves the site; it is shipped from the
`edge_outbox` table, so a restart or a Control Plane outage loses nothing. Aerospike keeps only the
26 h velocity buckets and the rule bundle.

- **Container mode:** the `ledger` service (`postgres:16-alpine`, profile `local-ledger`) runs on the
  private network with no published port and scram-sha-256 auth. The installers generate a random
  password into `secrets/edge-ledger.pass` and `.env` (`EDGE_LEDGER_PASSWORD`).
- **Native mode:** `install.sh --native` installs PostgreSQL 16 (PGDG repositories), creates the
  `hokeka_edge` role and `hokeka_ledger` database, keeps it on `127.0.0.1` and firewalls 5432.
- **Multi-node Edge Site:** all nodes share one site database: `--ledger-url jdbc:postgresql://…`
  `--ledger-user …` with the password in `HOKEKA_LEDGER_PASSWORD`, and the same `--site-id`.
- **Fail-closed:** `EDGE_LEDGER_FAIL_CLOSED=true` (default). If `EDGE_LEDGER_URL`, `_USER` or
  `_PASSWORD` is missing the edge logs which variable is missing (never a value), and while the
  ledger cannot be written every evaluation answers `HOLD` (`LEDGER_UNAVAILABLE`). `/edge/status`
  shows the ledger state, outbox backlog and write failures.
- **Schema:** Flyway inside edge-host (`edge_ledger` schema): `edge_evaluations` (monthly range
  partitions on `evaluated_at`, created three months ahead), `edge_outbox`, `edge_legal_holds`,
  `edge_held_evaluations`, `edge_config`, `edge_purge_log`, `edge_access_log`.
- **Retention:** set per PSP in the Console (minimum and default 5 years, maximum 25, maker-checker,
  reductions take effect 30 days after approval). `--retention-days` is ignored with a warning. The
  Control Plane publishes retention in the sealed `GET /api/v1/edge/config` document; the Edge
  consumer and the daily partition purge are planned (R2). Until then the ledger keeps every row.
- **Sizing:** size the ledger disk for at least 5 years and back the volume up. `install.sh
  --peak-tps N` prints `N × 86 400 × 365 × 5` evaluations × 2 048 bytes + 30% headroom:

  | Peak TPS | 5-year ledger disk |
  |---:|---:|
  | 1 | ~390 GiB |
  | 5 | ~1 953 GiB |
  | 10 | ~3 909 GiB |
  | 50 | ~19 548 GiB |
  | 100 | ~39 097 GiB |

  This is an upper bound (peak TPS all day) and the 2 048 B/evaluation figure is an estimate; measure
  it in a pilot. `install.ps1` has no `-PeakTps`; use the same formula.
- A `txn_id` that looks like a card number is refused with `422 {"error":"TXN_REF_LOOKS_LIKE_PAN"}`
  and nothing is stored.

## Transport security — TLS terminates in the app, not in a sidecar

The transaction API is **TLS 1.3 only** and terminates inside the edge process. There is no sidecar
and no plaintext listener: `EdgeTlsGuard` refuses to start the service if the keystore or its
password is missing, so a misconfigured node fails loudly instead of quietly serving HTTP.

The installers generate a **self-signed** PKCS#12 into `secrets/edge-tls.p12` (EC P-384,
SHA384withECDSA, 825 days, alias `edge`, SAN `localhost`/`127.0.0.1`) with a random 32-character
password written to `secrets/edge-tls.pass` and mirrored into `.env` as
`EDGE_TLS_KEYSTORE_PASSWORD`. Both files are mode 600 (Linux) / SYSTEM+Administrators ACL (Windows),
and re-running the installer never overwrites an existing keystore.

**Replace it with your own CA-issued certificate before production**, keeping the same path and
alias:

```bash
keytool -importkeystore -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \
        -destkeystore secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge
# then update EDGE_TLS_KEYSTORE_PASSWORD in .env and restart:  docker compose up -d
```

For mutual TLS from your PSP API nodes set `--tls-client-auth need` (Linux) / `-TlsClientAuth need`
(Windows) and point `SERVER_SSL_TRUST_STORE` / `SERVER_SSL_TRUST_STORE_PASSWORD` at your client-CA
truststore.

## First-boot activation — the node is fail-closed until you approve it

On first start the edge generates its own X25519 + Ed25519 keypairs into
`data/edge-identity.json` (mode 600) and presents the public halves plus the enrollment code to
`POST /api/v1/edge/enroll`. Until a platform admin approves the node in the Hokeka portal:

- `GET /edge/status` reports `"authorization": "unauthorized"` with the reason;
  it also reports `"evaluator"` — `native` when the Rust kernel is serving decisions, or
  `fallback-java-interpreter` when the native core is absent, so a silent drop onto the slow path is
  visible at a glance;
- `POST /edge/evaluate` returns **HOLD** — an unapproved node never issues an ALLOW;
- no rule bundle is served (`403`), so nothing unverified can be loaded.

`POST /edge/bundle` (plaintext bundle upload) exists **only** under the `dev` profile; in a normal
deployment it is a 404. Rules arrive exclusively through the signed, encrypted, replay-guarded pull.

Set `EDGE_ENROLLMENT_CODE` plus the pinned `CONTROLPLANE_ED25519_PUBLIC_KEY` /
`CONTROLPLANE_X25519_PUBLIC_KEY` in `.env` (the installer leaves them as blanks to fill in), and drop
the issued mTLS client certificate at `secrets/edge-client.p12`.

## Options

`--pspid` `--edgeid` `--controlplane` `--edge-image` `--bind <ip>` `--allow-subnet <cidr>`
`--data-dir <path>` `--enrollment-code <code>` `--tls-client-auth none|want|need` `--native`
`--ledger-url <jdbc>` `--ledger-user <role>` `--site-id <id>` `--peak-tps <n>` `--data-file-size <16G>`
`--insecure-skip-signature` ·
Windows: `-PspId -EdgeId -ControlPlane -EdgeImage -Bind -AllowSubnet -EnrollmentCode -TlsClientAuth
-LedgerUrl -LedgerUser -SiteId`

## Artifact authenticity

`SHA256SUMS` is GPG-signed by the Hokeka release key and published as `SHA256SUMS.asc`. `install.sh`
verifies that signature against a **public key pinned in the script** before it trusts any digest —
checksums alone protect against corruption and a bad mirror, not against a compromised origin.

Verification is **fail-closed**: an unset pin, a missing `gpg`, a missing `.asc` or a bad signature
all abort the install with nothing written. `--insecure-skip-signature` exists only for internal
mirrors you already trust by other means; never use it against `packages.hokeka.com`.

Releases are produced by `.github/workflows/release.yml` — see `docs/INSTALL.md` §4.

## Honest advisory — what is and isn't possible

- **✅ Cross-distro Linux + Windows:** yes, via the container path (identical stack everywhere) with
  a native Linux option.
- **✅ Auto-install all prerequisites incl. the databases:** yes — runtime, Aerospike, PostgreSQL 16
  ledger, config, service.
- **✅ Database reachable only by the app:** yes — network isolation (container) or loopback-bind +
  firewall (native).
- **⚠️ Aerospike on Windows:** there is **no native Windows Aerospike server**, so on Windows the DB
  runs in a container (Docker Desktop/WSL2). The script does this automatically; a bare-metal Windows
  Aerospike is not offered by the vendor.
- **⚠️ DB-level username/password + TLS:** Aerospike **Community Edition has no authentication** —
  app-only access is enforced at the network layer as above. That holds on a single-tenant on-prem
  box as long as nobody publishes port 3000. The Edge Ledger adds a scram-sha-256 password on top of
  the same network isolation. For **DB-level credentials, roles and TLS** (defence-in-depth), use **Aerospike
  Enterprise** and enable the `security { enable-security true }` stanza in `aerospike.conf`; the
  edge config already supports authenticated connections.
- **ℹ️ Native Aerospike download URL** varies by distro/version; `install.sh --native` stages the
  hardened config and points you to `aerospike.com/download` for the exact package where needed.

## Files

- `.env.example` — every variable `docker-compose.yml` reads, mirroring what the installers write.
  The installers are the authority; this file exists for hand-wiring and for `docker compose config`
  validation in CI.
- `install.sh` — Linux installer (container + `--native`).
- `install.ps1` — Windows installer (container).
- `docker-compose.yml` — the isolated edge + Aerospike + Edge Ledger stack (neither database is
  port-published);
  mounts `secrets/` read-only and `data/` read-write, and passes the TLS + channel env through.
- `aerospike/aerospike.conf` — single-node feature-store config (`hokeka` namespace, hardening notes).
