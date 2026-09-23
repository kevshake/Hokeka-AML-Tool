# Process 04 — Packages CDN & Edge Release (Hokeka Ops)

**Audience:** Hokeka release and platform operations.

**Purpose:** Publish signed Edge Node artifacts that PSP clients download via `install.sh` — **not** Control Plane or Console deployment.

Clients consume this CDN during [01 — Client Edge Node](01-client-edge-node.md). This process does **not** install anything on PSP servers; it maintains the **origin** they pull from.

---

## What packages.hokeka.com is

Default artifact origin for Edge installers:

```
$REPO_URL default: https://packages.hokeka.com/edge
```

Physical layout on the Hostinger VPS (`HOSTINGER_PACKAGES_DIR=/var/www/packages.hokeka.com`):

```
/var/www/packages.hokeka.com/
├── stable/<version>/...          stable signed release files
├── beta/<version>/...            beta signed release files
└── edge/
    ├── install.sh
    ├── stable.json               {"version":"1.2.3"}
    ├── beta.json
    └── <version> -> ../{stable,beta}/<version>
```

The version symlink keeps the public contract `/edge/<version>/...` independent of channel.

Each version directory contains:

- `SHA256SUMS`
- `SHA256SUMS.asc` (GPG signature)
- `edge-host.jar`
- Platform native libraries (`libedge_engine-*.so`, `edge_engine-*.dll`)

Serve **HTTPS only** with HSTS. No plain-HTTP fallback.

---

## OPS-REQUIRED — before first public release

Complete before any non-dry-run release:

1. **DNS & TLS:** `packages.hokeka.com` A/AAAA → Hostinger VPS; nginx docroot `/var/www/packages.hokeka.com`; valid certificate.
2. **Deployment user:** Restricted SSH user with write access to docroot; pre-create `stable/`, `beta/`, `edge/` with nginx-readable permissions.
3. **GitHub secrets** on protected `release` environment:

   | Secret | Purpose |
   |--------|---------|
   | `RELEASE_GPG_PRIVATE_KEY` | Armored release signing key |
   | `RELEASE_GPG_PASSPHRASE` | Key passphrase |
   | `RELEASE_GPG_FINGERPRINT` | Expected fingerprint after import |
   | `HOSTINGER_SSH_HOST` | Packages VPS hostname |
   | `HOSTINGER_SSH_USER` | Deployment user |
   | `HOSTINGER_SSH_KEY` | Private SSH key |
   | `HOSTINGER_PACKAGES_DIR` | Docroot path |

4. **Pin signing key in installer** (see below).
5. **Dry run** manual workflow first; on first real tag verify public key fingerprint in `/edge/install.sh` and `SHA256SUMS.asc` from VPS.

Private release key lives in GitHub secrets only — **never** on the VPS. VPS receives the **public** key embedded in pinned `install.sh`.

AWS (`AWS_ROLE_ARN`, `RELEASE_S3_BUCKET`) is optional legacy fallback when Hostinger secrets are absent.

---

## Pin signing key in `install.sh` — REQUIRED

`edge-host/deploy/install.sh` verifies `SHA256SUMS.asc` against a **public key pinned in the script**. Empty pin = **fail-closed** (install aborts).

On a dedicated **offline signing host**:

```bash
./scripts/generate-release-key.sh --pin
```

This:

- Creates sign-only Ed25519 key + revocation cert in `.release-key/` (gitignored)
- Rewrites `HOKEKA_SIGNING_*` placeholders in `install.sh`
- Prints three secret values for GitHub

Then:

1. Set `RELEASE_GPG_*` GitHub secrets
2. Move `private-key.asc` to hardware/offline storage
3. **Commit** pinned `install.sh` (public half is meant to be public)

`--insecure-skip-signature` is for internal mirrors only — never for `packages.hokeka.com`.

---

## Cutting a release

GitHub Actions: `.github/workflows/release.yml`

**Tag push:**

```bash
git tag v1.2.3 && git push origin v1.2.3          # → stable channel
git tag v1.3.0-beta.1 && git push --tags        # → beta (prerelease suffix)
```

**Manual:** `workflow_dispatch` — requires explicit `version`; defaults `dry_run: true` (build/sign, no publish).

### Pipeline stages

`plan → verify → {native, jar} → publish`

| Stage | Role |
|-------|------|
| **plan** | Resolve semver; prerelease cannot land on `stable` |
| **verify** | Edge-host tests + HSE-1 Java↔Rust interop — prevents seal/open drift |
| **native** | Build all platform cores; `fail-fast: true` |
| **publish** | Assemble, checksum, sign, upload, flip channel manifest **last** |

**Ordering invariants (do not change):**

1. Artifacts upload **before** channel manifest flip — manifest is the commit point.
2. Version prefix **never overwritten** — `publish` aborts if `edge/<version>/` exists.

Configure **required reviewers** on GitHub `release` environment so tags alone cannot ship.

---

## Building artifacts locally (engineering)

For debugging releases — not the client install path:

```bash
# Rust native core
cargo build --release -p edge-jni

# Java edge host
mvn -pl edge-host package
mvn -pl edge-host package jib:buildTar   # OCI image

# Control Plane (separate from edge release)
cd BACKEND && mvn clean package
```

CI `edge-native` job must stay green — JNI boundary executed per platform.

---

## Client-facing install URL

PSPs download:

```bash
curl -fsSL https://packages.hokeka.com/edge/install.sh -o install.sh
```

Or pipe to bash with flags from Console Setup Wizard ([01](01-client-edge-node.md)).

Optional mirrors: `--repo-url https://your-mirror/edge` or `--offline`.

---

## Known gaps (track before promising dates)

From [`docs/INSTALL.md`](../INSTALL.md) §5:

| Gap | Mitigation |
|-----|------------|
| Hostinger ops incomplete | Finish DNS/TLS/docroot/secrets (this doc § OPS-REQUIRED) |
| Signing key not yet pinned | Run `generate-release-key.sh --pin` |
| No automatic rollback | Clients re-install prior `--version` manually |

---

## Verification checklist

- [ ] `https://packages.hokeka.com/edge/install.sh` returns pinned installer
- [ ] `stable.json` / `beta.json` version matches uploaded directory
- [ ] `SHA256SUMS.asc` verifies with embedded public key
- [ ] Symlink `edge/<version>` resolves
- [ ] Release environment requires human approval
- [ ] No private key material on VPS or in git

---

## Related

- [Installation process map](README.md)
- [01 — Client Edge Node](01-client-edge-node.md) — consumer of this CDN
- [`docs/INSTALL.md`](../INSTALL.md) §3–4 — canonical build + release reference
- [`docs/architecture/edge-distribution-and-installation.md`](../architecture/edge-distribution-and-installation.md)
