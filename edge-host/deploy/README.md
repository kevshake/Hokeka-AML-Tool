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
processes (the edge) connect.

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
`--insecure-skip-signature` ·
Windows: `-PspId -EdgeId -ControlPlane -EdgeImage -Bind -AllowSubnet -EnrollmentCode -TlsClientAuth`

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
- **✅ Auto-install all prerequisites incl. the database:** yes — runtime, Aerospike, config, service.
- **✅ Database reachable only by the app:** yes — network isolation (container) or loopback-bind +
  firewall (native).
- **⚠️ Aerospike on Windows:** there is **no native Windows Aerospike server**, so on Windows the DB
  runs in a container (Docker Desktop/WSL2). The script does this automatically; a bare-metal Windows
  Aerospike is not offered by the vendor.
- **⚠️ DB-level username/password + TLS:** Aerospike **Community Edition has no authentication** —
  app-only access is enforced at the network layer as above, which is robust for a single-tenant
  on-prem box. For **DB-level credentials, roles and TLS** (defence-in-depth), use **Aerospike
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
- `docker-compose.yml` — the isolated edge + Aerospike stack (Aerospike **not** port-published);
  mounts `secrets/` read-only and `data/` read-write, and passes the TLS + channel env through.
- `aerospike/aerospike.conf` — single-node feature-store config (`hokeka` namespace, hardening notes).
