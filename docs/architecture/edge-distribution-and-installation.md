# Edge Engine — Distribution, Installation and Runtime

How the on-premises engine is built, published, downloaded, installed as a service, run and upgraded
on a client's (PSP's) own servers.

**Architecture context.** Rule evaluation runs on customer premises. Transactions and velocity history
stay there, in Aerospike. Only *aggregate* counts are pushed to the Hokeka-hosted control plane, whose
own database is PostgreSQL + TimescaleDB. No transaction data leaves the client's estate.

---

## 1. The chain at a glance

```
  SOURCE                 BUILD                    PUBLISH                 INSTALL                RUN
  ──────                 ─────                    ───────                 ───────                ───
  edge-engine/  ──cargo──►  libedge_engine.so  ┐
  (Rust)                    edge_engine.dll    ├─► packages.hokeka.com ──► install.sh ──► systemd
                                               │   /edge/<version>/         (verifies      hokeka-edge
  edge-host/    ──maven──►  edge-host.jar      ┘   + SHA256SUMS              checksums)     .service
  (Java)        ──jib────►  OCI container image ─► registry.hokeka.com ──► docker compose ─► container
```

Two delivery modes, both supported:

| Mode | What it installs | Best for |
|---|---|---|
| **Container** (default) | Edge + Aerospike via `docker compose`, DB on a private network with **no published port** | Most estates; distro-agnostic; also the Windows path |
| **Native** (`--native`) | Temurin JRE 25 + Aerospike CE on the host, edge as a **systemd service** | Linux estates that don't permit containers |

---

## 2. What compiles what

### Rust native core (`edge-engine/`)
```bash
cargo build --release -p edge-jni
```
`crate-type = ["cdylib"]` → a native shared library, not an executable:

| Platform | Artifact |
|---|---|
| Linux | `libedge_engine.so` |
| Windows | `edge_engine.dll` |

The Java side calls `System.loadLibrary("edge_engine")`, which resolves the platform filename
automatically — **one code path covers both**. The Rust is fully portable: verified to contain **zero**
`cfg(target_os)`, `cfg(unix)`, `cfg(windows)` or platform-specific imports.

**CI (`edge-native` job)** builds it on a `ubuntu-latest` + `windows-latest` matrix, runs the Rust unit
tests, **executes the real Java→Rust JNI crossing** against the freshly built library
(`mvn test -Pnative-core`), and uploads both artifacts. This matters: before it existed, nothing in CI
produced a `.so`/`.dll`, so the JNI boundary had never actually run and an ABI drift could ship
undetected.

### Java host (`edge-host/`)
```bash
mvn -pl edge-host package                 # Spring Boot fat jar → edge-host.jar
mvn -pl edge-host package jib:buildTar    # OCI image, no Dockerfile / no Docker daemon
```
The image bakes in the JVM flags that let the JVM find the native core:
```
-Djava.library.path=/opt/hokeka/lib
--enable-native-access=ALL-UNNAMED
```
CI stages the Linux `.so` into `src/main/jib/opt/hokeka/lib` before Jib runs, so the library ships
**inside the image**.

---

## 3. Publishing — the package server

`install.sh` fetches from `$REPO_URL` (default `https://packages.hokeka.com/edge`). Required layout:

```
/edge/install.sh                                  # the bootstrap script itself
/edge/stable.json                                 # {"version":"1.2.3"}
/edge/beta.json                                   # pre-release channel
/edge/<version>/SHA256SUMS                        # checksums for every artifact below
/edge/<version>/edge-host.jar
/edge/<version>/libedge_engine-linux-x86_64.so
/edge/<version>/libedge_engine-linux-aarch64.so
```

**Integrity.** Every downloaded artifact is verified against `SHA256SUMS` before installation. A file
that is missing from the manifest, or whose digest does not match, is **deleted and the install
aborts** — a truncated proxy response or a tampered mirror must never execute on a client's server.

The release pipeline that produces this layout is `.github/workflows/release.yml`, triggered on a
`v*` tag. See **`docs/INSTALL.md` §4** for the full contract (channel mapping, ordering guarantees,
required secrets). `SHA256SUMS` is GPG-signed and published as `SHA256SUMS.asc`; the installer
verifies it against a pinned public key **before** trusting any digest.

> **STILL TO DO — server side.** The release job and the installer's verification are both built.
> Remaining:
> 1. Provision the `packages.hokeka.com` bucket + CDN (S3 + CloudFront is sufficient; static files
>    only) and set the six repo secrets listed in `docs/INSTALL.md` §4.2.
> 2. Generate the release signing key, store the private half offline, and **pin the public half**
>    into `install.sh` (`HOKEKA_SIGNING_KEY` / `HOKEKA_SIGNING_FINGERPRINT`). Until this is done the
>    installer is fail-closed and will refuse to install — deliberately.
> 3. Serve strictly over HTTPS with HSTS; never publish a plain-HTTP fallback.

---

## 4. Installing on a client server

### One-line bootstrap (recommended)
```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- \
     --native --pspid 42 --edgeid acme-eu-1 --enrollment-code ABCD-1234
```

### Container mode (default)
```bash
sudo ./install.sh --pspid 42 --edgeid acme-eu-1 \
     --controlplane https://edge.hokeka.com --enrollment-code ABCD-1234
```

### Useful flags
| Flag | Purpose |
|---|---|
| `--native` | No containers: JRE + Aerospike on the host, edge as a systemd service |
| `--repo-url URL` | Mirror for air-gapped / restricted-egress estates |
| `--channel beta` | Track pre-releases |
| `--version 1.2.3` | Pin an exact build (recommended for production change control) |
| `--offline` | Never touch the network; use artifacts placed beside the script |
| `--bind 10.0.0.5` | Bind the transaction API to a private-network address instead of loopback |
| `--allow-subnet CIDR` | Restrict port 8443 to the PSP's API-node subnet |
| `--tls-client-auth need` | Require client certificates from the PSP's API nodes (mTLS) |

> **`--pspid` takes the NUMERIC portal id** (e.g. `42`), not a slug. Activation compares it strictly
> against the enrollment code's owner and returns **403** on mismatch. The portal's setup wizard emits
> the correct value — copy the command from there.

### What the installer does, in order
1. Detect distro + package manager (apt / dnf / zypper / pacman / apk) and CPU arch (x86_64, aarch64).
2. **Download + checksum-verify** `edge-host.jar` and the arch-correct native core.
3. Install prerequisites — container runtime, **or** Temurin JRE 25 + Aerospike CE.
4. **Harden the database**: container mode gives Aerospike *no published port* (private network only);
   native mode binds it to `127.0.0.1` and firewalls port 3000. The DB is reachable only by the edge.
5. Generate a self-signed EC P-384 TLS keystore (mode 600) — **the edge refuses to start without TLS**.
6. Write `/opt/hokeka/config/edge.env` (mode 600) with identity, control-plane URL and enrollment code.
7. Stage the native core into `/opt/hokeka/lib`.
8. **Install and start the systemd service** (native mode) or `docker compose up` (container mode).
9. Health-check `https://127.0.0.1:8443/actuator/health` and print a summary.

---

## 5. Running as a service (native mode)

`install.sh` writes `/etc/systemd/system/hokeka-edge.service`:

```ini
[Service]
Type=simple
User=hokeka                      # dedicated unprivileged system account
EnvironmentFile=/opt/hokeka/config/edge.env
ExecStart=/usr/bin/java -Djava.library.path=/opt/hokeka/lib \
          --enable-native-access=ALL-UNNAMED -jar /opt/hokeka/bin/edge-host.jar
Restart=always
RestartSec=5
NoNewPrivileges=true             # hardening: the engine needs no privileges
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/opt/hokeka
```

Operations:
```bash
systemctl status hokeka-edge
journalctl -u hokeka-edge -f
systemctl restart hokeka-edge
curl -sk https://127.0.0.1:8443/edge/status | jq
```

> **Fixed during review:** native mode previously only *printed* “fetch the tarball… `systemctl enable
> --now hokeka-edge`” — it never wrote a unit file and never installed a jar, so `--native` produced
> configuration and **nothing runnable**. It now installs both.

---

## 6. Runtime flow on the client box

```
PSP API node ──TLS 1.3──► edge-host :8443  POST /edge/evaluate
                               │
                               ├─► Aerospike: read velocity history for the card
                               ├─► Rust core (JNI): evaluate rules against the enriched features
                               ├─► Aerospike: record the transaction + advance hourly counters
                               └─► decision (ALLOW / ALERT / HOLD / BLOCK)

edge-host ──mTLS + HSE-1──► Hokeka control plane (PostgreSQL + TimescaleDB)
              ▲ pulls sealed rule bundles          ▼ pushes AGGREGATE counts only
```

### On-premises data model (Aerospike, namespace `hokeka`)
| Set | Key | Contents | TTL |
|---|---|---|---|
| `txn` | txn id | the transaction **plus its decision** (`decision`, `score`, `triggered_rule_ids`, `reasons`) — for future rule checks and audit | retention days (default 90) |
| `velocity` | `<panHash>:yyyyMMddHH` | hourly `{count, amount_cents}` counters | 26 h |
| `rules` | `active` | last verified rule IR + version | never |

**Why hourly buckets:** Aerospike is a key-value store — counting by scanning is O(n) and needs a
secondary index. Each transaction instead does a single atomic server-side `Operation.add` on its
hour's record, and a window is read as at most 24 key lookups. Buckets expire themselves, so no
cleanup job is needed.

**Fail-soft:** if the store is unavailable, decisions are still made on caller-supplied features. It is
surfaced as `featureStore: unavailable` on `/edge/status` so a history-blind node cannot masquerade as
healthy — important, because velocity rules silently cannot fire without local history.

**Restart behaviour:** the verified rule bundle is persisted, so a restart resumes enforcing
immediately. Previously a restart meant HOLDing all traffic until the next successful poll — an outage
on every process bounce, and an indefinite one if the control plane was unreachable.

---

## 7. Upgrades

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- --native --version 1.3.0
```
Re-running is safe: the TLS keystore, `edge.env` and the pinned node identity
(`/opt/hokeka/data/edge-identity.json`) are preserved, so the node keeps its enrollment. Only the jar
and native core are replaced, then the service restarts.

> **NOT YET BUILT:** rollback (`--version <previous>` works, but there is no automatic
> health-check-and-revert), and staged/canary rollout across a fleet.

---

## 8. Outstanding work

| # | Item | Why it matters |
|---|---|---|
| 1 | **Generate the release key and pin its public half** in `install.sh` | Signing + verification are implemented; the key itself does not exist yet, so the installer is fail-closed. Last step before external customers can install. |
| 2 | Provision `packages.hokeka.com` (bucket + CDN) and set the release secrets | The tag-triggered release job is built; it has nowhere to publish until the bucket exists. |
| 3 | Native Aerospike install is Linux-only | Aerospike has no native Windows build — Windows uses the container path. Documented, not a defect. |
| 4 | Aerospike paths are unit-tested, **not yet run against a live Aerospike** | Needs an integration test (Testcontainers) or a staging install. |
| 5 | `aarch64` is cross-compiled by the release job, but its JNI boundary is never executed (an x86_64 runner cannot load an ARM `.so`) | Add a real ARM runner before promising ARM support. |
| 6 | No automatic rollback / canary | A bad release currently needs a manual pinned re-install. |
| 7 | systemd unit assumes `/usr/bin/java` | Fine for the Temurin package the installer uses; make it configurable if customers bring their own JRE. |

---

## 9. Related documents
- `docs/architecture/edge-channel-contract.md` — the normative control-plane ↔ edge wire contract
- `docs/architecture/encryption-posture.md` — per-hop encryption evidence
- `edge-host/deploy/README.md` — operator quickstart and security model
