# Process 07 — First-run bootstrap (`hokeka-install`)

**Audience:** Anyone performing a **first install** on a fresh Linux or Windows host.

**Purpose:** One idempotent entry point that detects the OS, installs missing prerequisites, and delegates to the canonical role installers — without duplicating Edge or Compose logic.

---

## Entry points

| Platform | Command |
|----------|---------|
| Linux (all roles) | `sudo ./scripts/install/hokeka-install.sh --role control-plane\|edge\|console` |
| Windows (Edge / Console) | `.\scripts\install\hokeka-install.ps1 -Role edge\|console` |

**Audit only (no changes):**

```bash
./scripts/install/hokeka-install.sh --role edge --check
./scripts/install/hokeka-install.sh --role control-plane --check
```

**Dry-run:**

```bash
sudo ./scripts/install/hokeka-install.sh --role control-plane --dry-run
```

**Non-interactive:** pass `--yes` or run from CI with non-TTY stdin (auto-yes).

---

## Roles

| Role | What it does | Delegates to |
|------|----------------|--------------|
| **control-plane** | Docker + Compose v2, Node LTS, git, `.env` with strong secrets (never logged), `docker-compose.prod.yml` up, systemd unit, health check | [`03-control-plane.md`](03-control-plane.md), [`docs/deploy/DEPLOYMENT-READINESS-RUNBOOK.md`](../deploy/DEPLOYMENT-READINESS-RUNBOOK.md) |
| **edge** | JRE 21 (native), Docker (container), optional Rust pin + build (`--build-rust`) | [`edge-host/deploy/install.sh`](../../edge-host/deploy/install.sh) / [`install.ps1`](../../edge-host/deploy/install.ps1) — see [01-client-edge-node.md](01-client-edge-node.md) |
| **console** | Node LTS, Docker, Playwright chromium, `npm ci` in `FRONTEND/` | [05-local-developer-stack.md](05-local-developer-stack.md) |

Production **Control Plane** remains Linux/Docker on the Hostinger VPS. Windows `-Role control-plane` only validates Docker/Node and prints the Linux command.

---

## Robustness

| Feature | Environment / flag |
|---------|-------------------|
| Registry mirror (Docker Hub timeouts) | `HOKEKA_DOCKER_REGISTRY_MIRROR` or `DOCKER_REGISTRY_MIRROR` |
| Offline images | `HOKEKA_DOCKER_IMAGE_TARBALLS_DIR` (`.tar` / `.tar.gz` from `docker save`) |
| HTTP(S) proxy | Standard `HTTP_PROXY` / `HTTPS_PROXY` |
| Retries | Built into curl/get.docker.com/docker pull paths |
| Minimum RAM/disk | Enforced per role; `--check` reports shortfalls |

---

## Edge passthrough

Arguments after `--` are forwarded to `edge-host/deploy/install.sh`:

```bash
sudo ./scripts/install/hokeka-install.sh --role edge --yes -- \
  --pspid 42 --edgeid acme-eu-1 --controlplane https://edge.hokeka.com \
  --enrollment-code ABCD-1234
```

Native Linux + local Rust build:

```bash
sudo HOKEKA_EDGE_BUILD_RUST=1 ./scripts/install/hokeka-install.sh --role edge --build-rust --native -- \
  --pspid 42 --edgeid acme-eu-1
```

---

## After bootstrap

1. **Control Plane:** Run verification in [`DEPLOYMENT-READINESS-RUNBOOK.md`](../deploy/DEPLOYMENT-READINESS-RUNBOOK.md) §3–9.
2. **Edge:** Complete enrollment in Console ([01-client-edge-node.md](01-client-edge-node.md)).
3. **Secrets:** Rotate any bootstrap-generated values before public traffic ([03-control-plane.md](03-control-plane.md) Step 2).

Legacy `deploy-to-hostinger.sh` remains an SSH/rsync helper; prefer `hokeka-install.sh --role control-plane` on the VPS for dependency and `.env` handling.
