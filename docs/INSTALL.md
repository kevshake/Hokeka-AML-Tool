# Installation & Runtime — Canonical Guide

> **Process map (start here):** Step-by-step install **processes** — who installs Edge vs Console vs
> Control Plane — live in [`docs/install/README.md`](install/README.md). Use that index for
> runbooks; use **this document** for deep technical reference (secrets, compose internals, release
> pipeline, verification checklists). Where a process doc and this file disagree on facts, **this
> file wins**; see [§7 Superseded documents](#7-superseded-documents).

The single authoritative install document for the platform.

There are **two independent deployment targets** with different lifecycles, different operators and
different security models:

| # | Target | Who runs it | What it is |
|---|---|---|---|
| 1 | **Control plane** | Hokeka, on our own VPS | The portal, API, ML, case management, reporting — the whole `BACKEND/` app plus its datastores |
| 2 | **Edge node** | The client (PSP), on their own servers | `edge-host` + Rust core + a local Aerospike feature store, evaluating transactions on premises |

They are joined by one narrow channel. **Transaction data never leaves the client's estate** — only
aggregate counts are pushed up. That property is the product; do not weaken it.

```
CLIENT (PSP) PREMISES                          HOKEKA CONTROL PLANE
─────────────────────                          ────────────────────
PSP API node                                   host nginx :443  (TLS termination)
   │ TLS 1.3                                        │
   ▼                                         ┌──────┴───────────────────────┐
edge-host :8443                              │ backend-prod    127.0.0.1:2637│
   ├─ Rust core (JNI)  ◄── sealed rule bundles ──── frontend-prod 127.0.0.1:8088│
   ├─ Aerospike (NO published port)                │ aml-ms-prod :8091 ──► Aerospike│
   └─ txn + velocity history  ── aggregate counts ─►│ Postgres · Neo4j · Kafka · Redis│
      STAYS HERE                                    │ ClamAV · website              │
                                                    └───────────────────────────────┘
```

---

## 1. Control plane — install on Hokeka's servers

**Canonical method: Docker Compose.** `docker-compose.prod.yml` is the authority for what the control
plane consists of. Do not hand-assemble the stack.

### 1.1 Prerequisites

A Linux VPS with Docker Engine + Compose v2, a DNS record, and ports 80/443 open. Nothing else is
published to the internet — every application container binds to `127.0.0.1` and the **host** nginx
is the only public listener.

### 1.2 Provision

```bash
./deploy-to-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>
```

This installs Docker, rsyncs the repo to `/opt/aml-fraud-detector`, and brings the stack up.

> **STOP — read before running.** The script writes an `.env.prod` containing literal
> `CHANGE_ME_STRONG_PASSWORD` and `CHANGE_ME_JWT_SECRET_32_CHARS_MIN` placeholders and then starts
> the stack regardless. It also writes variable names (`DB_HOST`, `DB_USER`, `DB_PASSWORD`,
> `AEROSPIKE_HOST`) that **`docker-compose.prod.yml` does not read**. Treat the script as a
> bootstrap for the Docker install only. Write the real `.env` yourself per §1.3 **before** the first
> `up`, and never let a placeholder secret reach a running node.

### 1.3 Secrets — `/opt/aml-fraud-detector/.env`

These are the variable names the compose file actually reads. Five of them are declared
`${VAR:?...}`, so Compose **refuses to render** if they are missing — that is deliberate, do not
paper over it with a default.

```bash
# Required — compose fails without these
DATABASE_USERNAME=<db user>
DATABASE_PASSWORD=<strong unique password>
AUDIT_HMAC_KEY=<32-byte key>
PII_LOOKUP_HMAC_KEY=<32-byte key>
NEO4J_PASSWORD=<strong unique password>

# Required in practice — auth and encryption are broken without them
JWT_SECRET=<256-bit secret>
ENCRYPTION_KEY=<32-byte key>
AML_MS_INTERNAL_KEY=<shared secret: backend -> aml-ms>
AML_INTERNAL_API_KEY=<same shared secret; compatibility name for direct aml-ms launches>

# Defaulted, override per environment
CORS_ALLOWED_ORIGINS=https://aml.hokeka.com,https://testaml.hokeka.com
KAFKA_BOOTSTRAP_SERVERS=kafka-prod:29092
```

Generate each secret independently (`openssl rand -base64 32`). Never reuse one across two variables.
The two AML names are the exception: they are aliases for one backend-to-microservice credential and
must contain the same value. On Hostinger, generate it once and append both names to the deployment
environment before starting Compose:

```bash
cd /opt/aml-fraud-detector
umask 077
AML_KEY="$(openssl rand -base64 48)"
printf 'AML_MS_INTERNAL_KEY=%s\nAML_INTERNAL_API_KEY=%s\n' "$AML_KEY" "$AML_KEY" >> .env
unset AML_KEY
docker compose -f docker-compose.prod.yml up -d backend-prod aml-ms-prod
```

`docker-compose.prod.yml` currently forwards `AML_MS_INTERNAL_KEY` to both containers;
`AML_INTERNAL_API_KEY` is retained for standalone/legacy Hostinger service definitions. Do not put
either value in an nginx file, image, repository, or shell history.

Fixed inside the compose file and **not** taken from `.env`: `DATABASE_URL`
(`jdbc:postgresql://postgres-prod:5432/fraud_detector`), `SPRING_PROFILES_ACTIVE=production`,
`SERVER_PORT=2637`, and the Redis/Neo4j/ClamAV/aml-ms hostnames.

### 1.4 Bring up and verify

```bash
cd /opt/aml-fraud-detector
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps      # every service should be (healthy)

curl -sf http://127.0.0.1:2637/actuator/health
curl -sf http://127.0.0.1:8088/
```

`backend-prod` will not start until `postgres-prod`, `aml-ms-prod`, `clamav-prod`, `kafka-prod`,
`redis-prod` and `neo4j-prod` all report healthy. ClamAV has a 120s start period on first boot while
it downloads signatures — a slow first `up` is expected, not a fault.

### 1.5 Host nginx and TLS

The containers publish only to loopback; host nginx terminates TLS and proxies:

- `/` and `/api` → `127.0.0.1:2637` (backend)
- app UI → `127.0.0.1:8088` (frontend)

Start from `BACKEND/nginx/fraud-detector-api.conf`, then:

```bash
sudo certbot --nginx -d aml.hokeka.com -d api.hokeka.com
sudo certbot renew --dry-run
sudo nginx -t && sudo systemctl reload nginx
```

### 1.6 Updates

```bash
./deploy-update-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>
```

Pulls `main`, rebuilds, restarts. `.env` is untouched. Note this does a full `down` before `up` —
it is **not** zero-downtime; schedule it.

### 1.7 Non-negotiables

- **Profile is `production`.** Never `testenv` — that profile is `ddl-auto=create-drop` and will
  destroy the database on restart.
- **Schema changes go through Flyway only.** Add the next sequential
  `BACKEND/src/main/resources/db/migration/V###__description.sql`. Never edit an applied migration,
  never rely on `ddl-auto`.
- **Aerospike is not reachable from the backend.** It lives behind `aml-ms-prod` (`:8091`), reached
  over `AML_MICROSERVICE_URL` and authenticated with `AML_MS_INTERNAL_KEY`.
- **No application container may publish to `0.0.0.0`.** Host nginx is the only public surface.

---

## 2. Edge node — install on a client's servers

One command provisions everything, including the datastore.

### 2.1 Install

```bash
# Linux, containerised (default, recommended)
sudo ./install.sh --pspid 42 --edgeid acme-eu-1 \
     --controlplane https://edge.hokeka.com --enrollment-code ABCD-1234

# Linux, estates that do not permit containers: Temurin JRE 25 + Aerospike CE + systemd
sudo ./install.sh --native --pspid 42 --edgeid acme-eu-1 \
     --controlplane https://edge.hokeka.com --enrollment-code ABCD-1234
```

```powershell
# Windows Server / 10 / 11, as Administrator (container path only)
.\install.ps1 -PspId 42 -EdgeId acme-eu-1 -ControlPlane https://edge.hokeka.com -EnrollmentCode ABCD-1234
```

> `--pspid` takes the **numeric** portal id (e.g. `42`), not a slug. Activation compares it strictly
> against the enrollment code's owner and returns **403** on mismatch. Copy the exact command from
> the portal's setup wizard rather than composing it by hand.

Useful flags: `--version 1.2.3` (pin an exact build — recommended for production change control),
`--channel beta`, `--repo-url URL` (mirror for restricted-egress estates), `--offline`,
`--bind 10.0.0.5`, `--allow-subnet CIDR`, `--tls-client-auth need`.

### 2.2 What the installer does

1. Detects distro + package manager (apt/dnf/zypper/pacman/apk) and arch (x86_64, aarch64).
2. Downloads and **checksum-verifies** `edge-host.jar` and the arch-correct native core. A file
   missing from `SHA256SUMS`, or whose digest mismatches, is deleted and the install aborts.
3. Installs prerequisites — container runtime, **or** Temurin JRE 25 + Aerospike CE.
4. **Hardens the datastore**: container mode gives Aerospike *no published port* at all; native mode
   binds `127.0.0.1` and firewalls port 3000.
5. Generates a self-signed EC P-384 PKCS#12 (mode 600). The edge **refuses to start without TLS**.
6. Writes `/opt/hokeka/config/edge.env` (mode 600).
7. Stages the native core into `/opt/hokeka/lib`.
8. Installs + starts `hokeka-edge.service`, or runs `docker compose up`.
9. Health-checks `https://127.0.0.1:8443/actuator/health`.

### 2.3 Before production — replace the certificate

The generated certificate is **self-signed**. Swap in the PSP's CA-issued one, keeping path and alias:

```bash
keytool -importkeystore -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \
        -destkeystore secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge
# update EDGE_TLS_KEYSTORE_PASSWORD in .env, then: docker compose up -d
```

For mutual TLS from the PSP's API nodes, set `--tls-client-auth need` and point
`SERVER_SSL_TRUST_STORE` / `SERVER_SSL_TRUST_STORE_PASSWORD` at their client-CA truststore.

### 2.4 Activation — the node is fail-closed until approved

On first start the edge generates X25519 + Ed25519 keypairs into `data/edge-identity.json` (mode 600)
and presents the public halves plus the enrollment code to `POST /api/v1/edge/enroll`. Until a Hokeka
platform admin approves it in the portal:

- `GET /edge/status` reports `"authorization": "unauthorized"` with a reason;
- `POST /edge/evaluate` returns **HOLD** — an unapproved node never issues an ALLOW;
- no rule bundle is served (`403`), so nothing unverified can load.

Then set the pinned `CONTROLPLANE_ED25519_PUBLIC_KEY` and `CONTROLPLANE_X25519_PUBLIC_KEY` in `.env`
(the installer leaves them blank) and drop the issued mTLS client certificate at
`secrets/edge-client.p12`. Without the pinned keys the node accepts no bundles and ships no metrics.

`POST /edge/bundle` (plaintext upload) exists **only** under the `dev` profile; in a real deployment
it is a 404. Rules arrive exclusively via the signed, encrypted, replay-guarded pull.

### 2.5 Operating the node

```bash
systemctl status hokeka-edge
journalctl -u hokeka-edge -f
curl -sk https://127.0.0.1:8443/edge/status | jq
```

`/edge/status` reports two fields worth watching:

- `evaluator` — `native` when the Rust kernel is serving decisions, `fallback-java-interpreter` when
  the native core is absent. A silent drop onto the slow path is visible here.
- `featureStore` — `unavailable` when Aerospike is unreachable. The node **fails soft**, still
  deciding on caller-supplied features, but velocity rules cannot fire without local history, so a
  history-blind node must not be treated as healthy.

### 2.6 Runtime flow

```
POST /edge/evaluate
  ├─► Aerospike: read velocity history for the card
  ├─► Rust core (JNI): evaluate rules against enriched features
  ├─► Aerospike: record the transaction + advance the hourly counter
  └─► ALLOW / ALERT / HOLD / BLOCK
```

On-premises data model (Aerospike namespace `hokeka`):

| Set | Key | Contents | TTL |
|---|---|---|---|
| `txn` | txn id | transaction + its decision (`decision`, `score`, `triggered_rule_ids`, `reasons`) | retention days (default 90) |
| `velocity` | `<panHash>:yyyyMMddHH` | hourly `{count, amount_cents}` counters | 26 h |
| `rules` | `active` | last verified rule IR + version | never |

Hourly buckets mean each transaction does one atomic server-side `Operation.add`, a window is at most
24 key lookups, and buckets expire themselves — no cleanup job. The verified bundle is persisted, so a
restart resumes enforcing immediately rather than HOLDing all traffic until the next poll.

### 2.7 Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --native --version 1.3.0
```

Re-running is safe and idempotent: the TLS keystore, `edge.env` and the pinned node identity
(`/opt/hokeka/data/edge-identity.json`) are preserved, so the node keeps its enrollment. Only the jar
and native core are replaced, then the service restarts.

---

## 3. Building the artifacts

```bash
# Rust native core -> libedge_engine.so / edge_engine.dll
cargo build --release -p edge-jni

# Java edge host
mvn -pl edge-host package                 # Spring Boot fat jar
mvn -pl edge-host package jib:buildTar    # OCI image, no Dockerfile / no Docker daemon

# Control-plane backend
cd BACKEND && mvn clean package
```

The image bakes in the flags that let the JVM find the native core
(`-Djava.library.path=/opt/hokeka/lib`, `--enable-native-access=ALL-UNNAMED`). CI stages the Linux
`.so` into `src/main/jib/opt/hokeka/lib` so the library ships inside the image.

CI's `edge-native` job builds the core on an `ubuntu-latest` + `windows-latest` matrix and **executes
the real Java→Rust JNI crossing** (`mvn test -Pnative-core`). Keep that job green — before it existed
nothing in CI produced a `.so`/`.dll`, so ABI drift could ship undetected.

---

## 4. Releasing — the package server

`install.sh` fetches from `$REPO_URL`, default `https://packages.hokeka.com/edge`. On the Hostinger
VPS, `HOSTINGER_PACKAGES_DIR=/var/www/packages.hokeka.com` has this physical layout:

```
/var/www/packages.hokeka.com/
├── stable/<version>/...                       stable signed release files
├── beta/<version>/...                         beta signed release files
└── edge/
    ├── install.sh
    ├── stable.json                            {"version":"1.2.3"}
    ├── beta.json
    └── <version> -> ../{stable,beta}/<version>
```

The symlink keeps the public contract `/edge/<version>/...` independent of channel. Each version
contains `SHA256SUMS`, `SHA256SUMS.asc`, `edge-host.jar`, and the platform native libraries. Give the
SSH deployment user write access to this docroot and nginx read/traverse access. Serve it strictly
over HTTPS with HSTS and never publish a plain-HTTP fallback.

### 4.1 Cutting a release

`.github/workflows/release.yml` runs on a `v*` tag:

```bash
git tag v1.2.3 && git push origin v1.2.3     # -> stable channel
git tag v1.3.0-beta.1 && git push --tags     # -> beta channel (any prerelease suffix)
```

Manual runs (`workflow_dispatch`) require an explicit `version` and default to `dry_run: true`,
which builds, checksums and signs but publishes nothing.

The pipeline is `plan → verify → {native, jar} → publish`:

- **plan** resolves version and channel, rejecting anything that is not strict semver. A prerelease
  suffix can never land on `stable`.
- **verify** re-runs the edge-host tests and the **HSE-1 Java↔Rust interop suite**. A tag can point
  at any commit, including one that never saw a PR; this is what stops a release whose seal side and
  open side have drifted, which would silently stop PSP nodes receiving rules.
- **native** builds all three cores. `fail-fast: true` — a partial set must never reach publish.
- **publish** assembles, checksums, signs, uploads, then flips the manifest.

Two ordering properties matter and should not be "tidied":

1. **Artifacts upload first; the channel manifest flips last.** The flip is the commit point of the
   release. If the manifest named a version before its files existed, every client installing in
   that window would abort on a 404.
2. **A version prefix is never overwritten.** `publish` aborts if `edge/<version>/` already exists.
   Rewriting a release would change what a node pinned with `--version` believes it verified.

### 4.2 Required repository secrets

| Secret | Purpose |
|---|---|
| `RELEASE_GPG_PRIVATE_KEY` | Armored private half of the Hokeka release signing key |
| `RELEASE_GPG_PASSPHRASE` | Its passphrase |
| `RELEASE_GPG_FINGERPRINT` | Expected fingerprint, asserted after import — guards against a wrong or rotated key being swapped into the secret |
| `HOSTINGER_SSH_HOST` | Hostname of the packages VPS |
| `HOSTINGER_SSH_USER` | Restricted deployment user with write access to the package docroot |
| `HOSTINGER_SSH_KEY` | Private SSH key for that deployment user |
| `HOSTINGER_PACKAGES_DIR` | nginx docroot, normally `/var/www/packages.hokeka.com` |

AWS is not required. `AWS_ROLE_ARN` plus `RELEASE_S3_BUCKET` enables the legacy S3 fallback when no
Hostinger secrets are present; `RELEASE_CLOUDFRONT_ID` optionally invalidates its CDN.

The `publish` job uses the `release` GitHub environment — configure required reviewers on it so a
tag push alone cannot ship to customers.

### 4.3 Pinning the signing key into the installer — REQUIRED before first public release

`install.sh` verifies `SHA256SUMS.asc` against a public key **pinned in the script itself**, before
it trusts any digest. Checksums alone are worthless against a compromised origin: whoever can rewrite
an artifact can rewrite the digest beside it.

The pin is currently **empty**, and the installer is deliberately **fail-closed** — it aborts rather
than installing unverified artifacts. Generate the key and pin it with:

```bash
./scripts/generate-release-key.sh --pin
```

This creates a sign-only Ed25519 key, exports both halves plus a revocation certificate into
`.release-key/` (gitignored, mode 600), verifies the key is sign-capable, and rewrites the two
`HOKEKA_SIGNING_*` placeholders in `install.sh` — validating the result parses before replacing the
original. It then prints the three secret values to set.

**Do this on an offline or dedicated signing host, not a developer laptop.** Whoever holds the
private half can sign artifacts that every PSP edge node in the field will download and execute as
root. After setting the secrets, move `private-key.asc` to a hardware token or offline store and
securely delete the working copy; keep `revocation.asc` somewhere separate from the key itself.

Commit the pinned `install.sh` — the public half is meant to be public.

`--insecure-skip-signature` exists for internal mirrors you already trust by other means; it must
never be used against `packages.hokeka.com`, and it warns loudly.

### 4.4 OPS-REQUIRED — DNS and first key upload

Before the first non-dry-run release, operations must:

1. Create the `packages.hokeka.com` DNS A/AAAA record for the Hostinger VPS, configure nginx with
   `/var/www/packages.hokeka.com` as its document root, obtain a TLS certificate, and verify HTTPS.
2. Create the restricted SSH deployment user/key, pre-create `stable`, `beta`, and `edge` under the
   docroot with nginx-readable permissions, and set all four `HOSTINGER_*` secrets on the protected
   GitHub `release` environment.
3. On a dedicated signing host, run `./scripts/generate-release-key.sh --pin`, commit the pinned
   `edge-host/deploy/install.sh`, then upload `private-key.asc`, its passphrase, and
   `fingerprint.txt` as the three `RELEASE_GPG_*` GitHub secrets.
4. Run a manual dry release first. For the first real tag, confirm the public key embedded in
   `/edge/install.sh` has the expected fingerprint and verify `SHA256SUMS.asc` from the VPS.

The private release key is uploaded only to GitHub's protected release secrets, never to the VPS.
The VPS receives the public key as part of the pinned installer.

---

## 5. Blocking gaps

These are real and unresolved. Read them before promising an install date to a client.

| # | Gap | Consequence |
|---|---|---|
| 1 | **The release job now publishes to Hostinger**, but operations must complete §4.4: DNS/TLS, nginx docroot, deployment user, and the four `HOSTINGER_*` secrets. | Until the VPS is provisioned the workflow has nowhere to publish. AWS is only an optional fallback. |
| 2 | **The signing key is not yet generated or pinned.** Tooling is done (`scripts/generate-release-key.sh --pin`, verified end to end); the key itself must be generated on a signing host and the three secrets set. | Blocks the first public release by design — the installer is fail-closed with an empty pin. One command plus secret configuration. |
| 3 | `deploy-to-hostinger.sh` writes placeholder secrets and starts the stack anyway. | A control plane can come up on real infrastructure with `CHANGE_ME_*` credentials. Mitigated by §1.3, but the script should fail instead of defaulting. |
| 4 | Aerospike code paths are unit-tested, never run against a live Aerospike. | Needs a Testcontainers integration test or a staging install before it is trustworthy. |
| 5 | ~~aarch64 JNI never executed~~ **Closed.** The aarch64 leg now runs on a native `ubuntu-24.04-arm` runner, and the musl leg runs its crossing inside an Alpine container. Every published core has had its JNI boundary executed on its own platform. | — |
| 6 | No automatic rollback, health-check-and-revert, or canary. `--version <previous>` works manually. | A bad release needs a manual pinned re-install across the fleet. |
| 7 | The systemd unit hardcodes `/usr/bin/java`. | Fine for the Temurin package the installer lays down; breaks if a customer brings their own JRE. |
| 8 | Aerospike CE has **no authentication**; app-only access is enforced at the network layer. | Robust for a single-tenant on-prem box. For DB-level credentials, roles and TLS, the customer needs Aerospike Enterprise with `security { enable-security true }` — the edge config already supports authenticated connections. |
| 9 | There is **no native Windows Aerospike server**. Windows always uses the container path. | Vendor limitation, documented, not a defect. |

---

## 6. Verification checklist

**Control plane**

- [ ] No `CHANGE_ME_*` value present anywhere in `.env`
- [ ] All five `${VAR:?}` secrets set; each generated independently
- [ ] `SPRING_PROFILES_ACTIVE=production` (never `testenv`)
- [ ] Flyway migrations applied cleanly; `ddl-auto` is `validate`
- [ ] `docker compose ps` — every service `(healthy)`
- [ ] No container publishing on `0.0.0.0`; only host nginx is public
- [ ] TLS certificate valid and auto-renewing
- [ ] `CORS_ALLOWED_ORIGINS` restricted to hokeka.com domains

**Edge node**

- [ ] Artifact digests verified against `SHA256SUMS`
- [ ] Self-signed certificate replaced with the PSP's CA-issued one
- [ ] Aerospike has no published port (container) or is loopback-bound + firewalled (native)
- [ ] Node approved in the portal; `/edge/status` shows `authorization` cleared
- [ ] `evaluator: native` — not silently on the Java fallback
- [ ] `featureStore` healthy — velocity rules cannot fire without it
- [ ] Pinned control-plane keys and `secrets/edge-client.p12` in place
- [ ] `POST /edge/bundle` returns 404 (i.e. not running the `dev` profile)

---

## 7. Superseded documents

| Document | Status |
|---|---|
| `BACKEND/DEPLOYMENT.md` | **Superseded for the control plane.** Predates the current architecture: it describes a bare-metal systemd + jar install, database `fraud_detector_prod`, and env vars (`DATABASE_URL`, `DB_MAX_POOL_SIZE`, `RATE_LIMIT_RPM`) that the compose stack does not read. Critically, it also predates Aerospike moving out of the backend into `aml-ms-prod`. Retain only for its nginx, security-header, tuning and troubleshooting reference. |
| `docs/architecture/edge-distribution-and-installation.md` | Still accurate; the deeper architectural companion to §2. |
| `edge-host/deploy/README.md` | Still accurate; operator quickstart and security model for the edge. |

Related: `docs/architecture/edge-channel-contract.md` (normative control-plane ↔ edge wire contract),
`docs/architecture/encryption-posture.md` (per-hop encryption evidence).
