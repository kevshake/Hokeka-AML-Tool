# Hokeka Edge — Installation Failure Modes & Root Causes

**Audience:** whoever is running or supporting an installation that did not go cleanly.

The install guide's troubleshooting table maps symptom → fix. This document is the other direction:
it groups failures by **root cause**, explains *why* each one happens, and says how to prevent it.
It is organised by where in the install the failure occurs.

**Read this first:** the two most common categories by a wide margin are §2 (environment mismatch —
the host was never suitable) and §3 (network egress — something between you and Hokeka is filtering
or intercepting). Together they account for most failed installs. Almost everything in both is
preventable by the pre-flight checklist in the install guide §4.

---

## 1. The single most important rule

**A failure during download or verification is a safe failure. A "fix" that bypasses it is not.**

The installer is deliberately fail-closed at several points. When it stops, nothing has been
installed and nothing is running. That is the design working. The dangerous move is reaching for
`--insecure-skip-signature` to make an error go away — that converts a detected integrity problem
into an undetected one, on a box that will be making payment decisions.

If verification fails, stop and contact Hokeka. Do not retry with the check disabled.

---

## 2. Environment mismatch — the host was never going to work

These fail because the machine does not meet a requirement, usually one that was not checked in
advance. They are the largest category and the cheapest to prevent.

### 2.1 musl / Alpine

**Current behaviour.** A musl-linked core is published for x86_64, and the installer detects your C
library and downloads the matching artifact automatically. `--native` works on musl/x86_64.

**musl on ARM is refused** — no musl/aarch64 core is published. Use container mode.

**On Alpine `--native`, two things differ:** Java comes from Alpine's OpenJDK 21 rather than Temurin
25 (Adoptium publishes no apk repo; 21 meets the engine's floor), and Aerospike has no Alpine
package, so the installer stages the config and points you at `aerospike.com/download`. Container
mode avoids both.

**Historical note.** Older installers had no musl core and no libc check: `--native` on Alpine
appeared to succeed and left a node either dead or silently serving every transaction on the slow
Java interpreter. If you installed with an older build, check `evaluator: native` on `/edge/status`.

### 2.2 Architecture mismatch

**What happens.** Install completes; the engine runs on the Java fallback, or the library fails to
load outright.

**Root cause.** The wrong `.so` for the CPU — usually an x86_64 artifact staged by hand onto an ARM
box, or an offline install where the operator copied the wrong file.

**Fix.** `uname -m` and match: `x86_64` → `libedge_engine-linux-x86_64.so`, `aarch64` →
`libedge_engine-linux-aarch64.so`.

**Note on ARM.** aarch64 builds are published but the JNI boundary is cross-compiled and **never
executed in CI** — no ARM runner exists. ARM is not validated end to end. Use x86_64 in production.

### 2.3 Java version

**Root cause.** A pre-existing JRE earlier than 21 on the `PATH`, or a customer-supplied JRE the
systemd unit does not point at. The engine serves each transaction on a **virtual thread**, so Java
21 is a hard floor; the installer lays down Temurin **25**.

There is a related sharp edge: the systemd unit hardcodes `/usr/bin/java`. If you bring your own JRE
somewhere else, the unit runs the wrong one — or nothing.

**Fix.** `java -version` ≥ 21. If you manage your own JRE, edit `ExecStart` in
`/etc/systemd/system/hokeka-edge.service` to the correct absolute path.

### 2.4 Undersized ledger disk

**What happens.** The node works for months, then answers **HOLD** with
`failClosedCode = LEDGER_UNAVAILABLE` for every transaction. Nothing changed except the date.

**Root cause.** The Edge Ledger (PostgreSQL 16) keeps every evaluation for the PSP's retention
period: at least 5 years. The disk was sized for weeks, not years. Until the Edge purge job is built
(planned, R2) the ledger only grows.

**Fix and prevent.** Install with `--peak-tps N`. The installer prints the 5-year estimate:
`N × 86 400 × 365 × 5` evaluations at about 2 048 bytes each, plus 30% headroom (about 390 GiB per
peak TPS). It is an upper bound, because it assumes peak TPS all day. See the install guide §2.3.
Alert on ledger disk usage. Retention is set in the Console, not by the installer:
`--retention-days` is ignored with a warning.

### 2.5 Undersized Aerospike data file

**What happens.** Aerospike begins **evicting data** once its file fills. Nothing crashes, but
velocity history disappears, so velocity rules weaken.

**Root cause.** Aerospike now holds only 26 h of hourly velocity buckets and the rule bundle. The
shipped `filesize 4G` covers roughly 90 TPS sustained at an assumed 512 bytes per record. A busier
node needs more.

**Fix.** Re-run the installer with `--data-file-size 16G` (or larger). See the install guide §2.4.

### 2.6 PostgreSQL 16 not available (native mode)

**What happens.** Native install stops with "install PostgreSQL 16 for … manually (or pass
--ledger-url to a site database)".

**Root cause.** The installer installs PostgreSQL 16 from the distro or the PostgreSQL project's
PGDG repository for apt, dnf/yum, apk and zypper hosts. Other package managers, or a host that
cannot reach PGDG, are not covered.

**Fix.** Install PostgreSQL 16 yourself and re-run, or point the node at an existing PostgreSQL 16
database with `--ledger-url`, `--ledger-user` and `HOKEKA_LEDGER_PASSWORD`. Container mode avoids
this.

### 2.7 Missing `gnupg`

**Root cause.** Signature verification is mandatory and needs `gpg`. The installer tries to install
it, but on a minimal or air-gapped image with no working package source that attempt fails.

**Fix.** Install `gnupg` manually and re-run. Do not skip the signature check to work around a
missing package.

### 2.8 File descriptor limits

**Root cause.** Aerospike wants `proto-fd-max 15000`. A restrictive default `ulimit -n` for its user
causes connection failures under load rather than at startup — so it looks like a runtime problem,
not an install one.

**Fix.** Raise `nofile` for the Aerospike user/container.

---

## 3. Network and egress — something in the middle

### 3.1 TLS interception on the control-plane channel

**What happens.** Install succeeds. The node enrolls or doesn't, but **no rule bundles ever arrive**,
so the node sits at `HOLD` for everything.

**Root cause.** A corporate TLS-intercepting proxy on the outbound path. The control-plane channel is
mTLS with a pinned server identity. Interception is cryptographically indistinguishable from an
attack, and the channel correctly refuses it.

**Why it is confusing.** Nothing about the *install* failed. The symptom appears later, at the point
where the node should start enforcing, and looks like an activation problem.

**Fix.** Allow-list `edge.hokeka.com` to bypass interception. Do not install the proxy's CA as a
workaround — the pinning is the point.

### 3.2 Blocked or filtered egress

**Root cause.** 443 outbound to `packages.hokeka.com` (artifacts), `registry.hokeka.com` (image), or
`edge.hokeka.com` (channel) is blocked. Also common: distro mirrors, `get.docker.com`,
`packages.adoptium.net` or the PostgreSQL PGDG repository blocked, so prerequisite installation
fails.

**Fix.** Open the destinations in the install guide §3.2, or mirror the artifacts and use
`--repo-url https://your-mirror/edge`, or install fully offline with `--offline`. The Edge needs
egress on TCP 443 only, and no inbound port from the internet.

### 3.2a Decision records not reaching the Control Plane

**What happens.** The node decides normally, but no alerts or cases appear in the Console.

**Root cause.** Decision records wait in the ledger outbox (`edge_outbox`) until the Control Plane
acknowledges them. The usual causes are the ones in §3.1 and §6.2: blocked or intercepted egress to
`edge.hokeka.com`, a missing mTLS client certificate, or missing pinned keys. A `401`/`403` marks the
node unauthorised and stops shipping.

**Fix.** Fix the channel. Nothing is lost: the shipper drains the backlog in order once the channel
works.

### 3.3 A proxy that returns HTML instead of the artifact

**What happens.** Checksum mismatch, or "not listed in SHA256SUMS".

**Root cause.** A captive portal or filtering proxy answered the download with an error page. The
installer received a valid HTTP 200 containing HTML, not a jar. The checksum check catches this
exactly as intended.

**Fix.** Fix the proxy path. The verification behaved correctly — do not bypass it.

### 3.4 Inbound 8443 unreachable from your API nodes

**Root cause.** The default bind is `127.0.0.1`. If your API nodes are on other hosts, they cannot
reach a loopback-bound listener.

**Fix.** Reinstall or reconfigure with `--bind <private-ip> --allow-subnet <cidr>`. Never bind to a
public interface.

---

## 4. Integrity and signature failures

Treat everything in this section as a stop condition.

| Message | Root cause | What to do |
|---|---|---|
| `SHA256SUMS FAILED SIGNATURE VERIFICATION` | The manifest was not signed by the Hokeka release key | **Stop.** Possible tampering, or a mirror serving a manifest from a different origin. Contact Hokeka |
| `this installer has no pinned release signing key` | An installer build with no key pinned in it | Obtain an official installer from Hokeka. Do not bypass |
| `<file> is not listed in SHA256SUMS` | Truncated/substituted response, or a mismatched manifest | Usually §3.3. Re-check the download path |
| `checksum MISMATCH for <file>` | Corrupted or tampered artifact | Retry once on a clean path; if it repeats, stop and escalate |
| `could not fetch SHA256SUMS.asc` | Signature file missing on the mirror | An incomplete mirror. Re-sync it; do not skip the check |

**Not-yet-provisioned caveat.** Until Hokeka generates the release signing key and pins its public
half into `install.sh`, the installer is fail-closed and will refuse to install from a public mirror
at all. If you hit "no pinned release signing key" today, that is why — the distribution chain is not
open yet.

---

## 5. TLS and startup failures

### 5.1 The engine refuses to start

**Root cause.** Missing keystore or missing `EDGE_TLS_KEYSTORE_PASSWORD`. `EdgeTlsGuard` refuses to
start the service rather than serve plaintext.

**This is correct behaviour, not a bug.** There is no plaintext listener and no sidecar; a
misconfigured node fails loudly instead of quietly serving HTTP.

**Fix.** Check `/opt/hokeka/secrets/edge-tls.p12` exists (mode 600) and the password in `.env`
matches `secrets/edge-tls.pass`.

### 5.2 Certificate replacement broke startup

**Root cause.** The replacement certificate was imported under a different **alias**, or the password
in `.env` was not updated to match the new keystore.

**Fix.** Keep alias `edge` and the same path; update `EDGE_TLS_KEYSTORE_PASSWORD`. See install guide
§6.1.

### 5.3 Your API nodes reject the certificate

**Root cause.** The installer's certificate is **self-signed**, with SANs for `localhost` and
`127.0.0.1` only. A client connecting by hostname or private IP will fail hostname verification.

**Fix.** Replace it with your CA-issued certificate carrying the right SANs. This is required for
production anyway.

### 5.4 The Edge Ledger is unreachable

**What happens.** The engine starts, but every evaluation answers **HOLD** with
`failClosedCode = LEDGER_UNAVAILABLE`.

**Root cause.** The engine cannot write the evaluation and its outbox row. With
`EDGE_LEDGER_FAIL_CLOSED=true` (the default) it refuses to decide without a record. Common causes:
the `ledger` container or PostgreSQL service is down; the disk is full (§2.4); `EDGE_LEDGER_PASSWORD`
in `.env` no longer matches the database (for example after `secrets/edge-ledger.pass` was deleted
and regenerated); or a shared site database (`EDGE_LEDGER_URL`) is unreachable.

**Fix.** Check `docker compose ps ledger` and `docker compose logs ledger`, or
`systemctl status postgresql` (`postgresql-16` on RHEL family). Check the disk. Restore the original
ledger password rather than generating a new one. The engine retries the connection on its own;
once the ledger is writable, decisions resume.

---

## 6. Activation and enrollment failures

### 6.1 403 during activation

**Root cause.** `--pspid` was given a slug or name instead of the **numeric** portal id. Activation
compares it strictly against the enrollment code's owner and rejects a mismatch.

**Fix.** Use the numeric id. Copy the command from the portal's setup wizard rather than composing it.

### 6.2 Everything returns HOLD after a successful install

**Root cause.** Almost always: the node has not been approved in the portal yet. An unapproved node
is fail-closed — it answers `HOLD`, is served no rule bundle, and never issues an `ALLOW`.

**This is not a fault.** Approve the node, then confirm `authorization` on `/edge/status`.

Second possibility: approved, but the pinned control-plane keys
(`CONTROLPLANE_ED25519_PUBLIC_KEY` / `CONTROLPLANE_X25519_PUBLIC_KEY`) are missing from `.env`, so no
bundle is ever accepted and the node stays unarmed.

### 6.3 Enrollment code rejected

**Root cause.** Codes are single-use with a 24 h TTL. A re-run after the code was consumed, or a code
issued yesterday, will fail.

**Fix.** Issue a fresh code. Note that a **re-install preserves the node identity** in
`/opt/hokeka/data/edge-identity.json`, so an already-enrolled node does not need a new code — if you
find yourself needing one on an upgrade, check whether that directory was wiped (§7.1).

---

## 7. Upgrade and re-install failures

### 7.1 The node lost its identity and wants re-enrollment

**Root cause.** `/opt/hokeka/data/` was deleted, recreated, or — in container mode — the volume was
removed (`docker compose down **-v**`). That directory holds the node's pinned X25519/Ed25519 private
keys.

**Fix.** Re-enroll with a fresh code from the portal.

**Prevent.** Back up `/opt/hokeka/data/` and the Edge Ledger. Do not pass `-v` to
`docker compose down`: it also deletes the `ledger-data` volume, which holds the PSP's transaction
records for the retention period.

### 7.2 An upgrade made things worse and there is no automatic rollback

**Root cause.** There is no health-check-and-revert and no canary support. A bad release has to be
un-done by hand.

**Fix.** Re-install pinning the previous version: `--version <previous>`.

**Prevent.** Pin versions explicitly in production rather than tracking a channel, and upgrade one
node first, verifying `/edge/status` before proceeding across a fleet.

### 7.3 Re-running the installer worried someone

Re-running is safe and idempotent. The TLS keystore, `edge.env`, the ledger password, the ledger
data and the node identity are preserved; only the jar and native core are replaced, then the service
restarts. It never overwrites an existing keystore or ledger password.

---

## 8. Post-install states that look healthy but are not

These are not install failures. They are the states worth catching in the first week, because each
one leaves a node that answers requests and looks fine while doing less than you think.

| State on `/edge/status` | What it actually means | Why it is easy to miss |
|---|---|---|
| `featureStore: unavailable` | Aerospike unreachable. Enrichment is skipped, so **velocity rules cannot fire** | The node fails *soft*: requests still succeed and return normal-looking decisions |
| `evaluator: fallback-java-interpreter` | The Rust core did not load or faulted; decisions are correct but slower | Nothing in the response differs |
| `standbyBundleReady: false` | The Java standby could not parse the bundle — a native fault would fall through to `HOLD` | Only visible on `/edge/status` |
| `authorization` changed | The node was deauthorised centrally and is now holding everything | Looks like a traffic problem |
| Aerospike near `filesize` | Eviction is imminent or under way; history silently shrinks | No error is raised |
| Ledger disk filling | The node will soon answer HOLD with `LEDGER_UNAVAILABLE` | It grows slowly for months; no purge until R2 |

**Alert on all six.** The install guide §7.2 lists these as the standing monitoring set. A node that
returns 200s is not evidence that enforcement is intact.

---

## 9. Collecting diagnostics for a support ticket

```bash
curl -sk https://127.0.0.1:8443/edge/status | jq        # always include this
journalctl -u hokeka-edge --since -1h                   # native
docker compose logs --since 1h                          # container
uname -a && ldd --version | head -1                     # arch + libc
java -version 2>&1
df -h /opt/aerospike/data                               # data file headroom
docker compose ps ledger                                # container: ledger state
docker system df -v | grep ledger-data                  # container: ledger volume size
```

Include: the installed version, your edge id, the exact install command (with the enrollment code
redacted), and whether the node has ever been approved in the portal.

---

## 10. Related documents

- [`edge-client-install-guide.md`](edge-client-install-guide.md) — requirements, sizing, installation
- [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md) — the evaluation contract and rule semantics
- [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md) — what data leaves the Edge
- [`INSTALL.md`](INSTALL.md) — internal: control plane deployment and the release pipeline
