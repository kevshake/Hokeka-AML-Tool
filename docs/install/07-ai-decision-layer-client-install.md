# Process 07 — AI decision layer and client (Linux) install

**Audience:** Product and platform engineers enabling Laya Studio-backed Hokeka AI while a PSP installs an Edge Node on Linux.

**Short answer:** Laya Studio is integrated through the Control Plane's `AiDecisionGateway` and `LayaSystemOneClient`. It does not run inside the client Edge Node Linux installer. Edge calls the Control Plane over mTLS and never receives `LAYA_API_KEY`.

---

## 1. Name map

| Name | What exists in this repo |
|-------------------|--------------------------|
| **Laya Studio** | External AI provider called by `LayaSystemOneClient` using `LAYA_API_KEY`; optional rule generation uses `/v1/ask`. |
| **Hokeka AI** / **Hokeka Intelligence** | Product-facing name for the advisory decision layer and Console settings. |
| **AiDecisionGateway** | Control Plane boundary used by regulated paths; deterministic rules remain the fallback. |
| **Edge AI client** | Calls the Control Plane's `/edge/decision` endpoint over the enrolled mTLS channel; it holds no provider credential. |

---

## 2. How AI runs today (not a separate on-prem service)

```
PSP Edge Node (client Linux box)              Hokeka Control Plane (VPS)
───────────────────────────────              ─────────────────────────
edge-host.jar / container
  rules + Aerospike locally
  borderline ALERT/HOLD ──mTLS──► POST /edge/decision
                                    (client URI; see API row)
                                    │
                                    ▼
                               AiDecisionGateway
                                    │
                                    ▼
                               Laya Studio HTTPS
                               (LAYA_API_KEY stays here)
```

| Concern | Reality |
|---------|---------|
| **Runtime** | In-process Spring beans inside **BACKEND** (`AiDecisionGateway`, `LayaSystemOneClient`). There is no separate AI JAR, systemd unit, or Edge-side model service. |
| **API** | Edge posts to `{CONTROLPLANE_URL}/edge/decision` over mTLS. Operator endpoints are under `/api/v1/ai/*`. |
| **Config** | `LAYA_API_KEY` enables the provider. `LAYA_*` and `HOKEKA_AI_*` tune timeouts, confidence gates, promotion, and budgets. See [03 — Control Plane](03-control-plane.md). |
| **Edge credentials** | **None.** `HokekaAiDecisionClient` only talks to the Control Plane channel. |
| **Failure mode** | Rules baseline; advisory reasons omitted. Regulated paths stay fail-soft to deterministic rules. |

Edge uses AI **as a Control Plane client call**, not as a local model server. After a normal Edge install, enrollment, and Control Plane Laya configuration, eligible decisions can request advisories without an AI package on the PSP box.

---

## 3. Current Linux client installer flow (what *is* embedded)

Canonical installer: [`edge-host/deploy/install.sh`](../../edge-host/deploy/install.sh).

| Mode | Ships | Starts |
|------|-------|--------|
| **Container** (default) | `docker-compose.yml`: `aerospike` + `edge` image | Compose stack; health `https://127.0.0.1:8443/actuator/health` |
| **Native** (`--native`) | Temurin JRE 25 (`install_temurin25`), Aerospike CE, `edge-host.jar`, `libedge_engine.so` | systemd unit `hokeka-edge.service` |

**Explicitly not installed on the client box:** Control Plane, Console, Laya key, AI gateway, Postgres, Kafka, aml-microservice.

Process runbook: [01 — Client Edge Node](01-client-edge-node.md). Architecture: [`docs/architecture/edge-distribution-and-installation.md`](../architecture/edge-distribution-and-installation.md).

---

## 4. Recommended approach (do this — do not invent a parallel installer)

### A. Client Edge install (PSP Linux) — already correct

1. Install Edge with `install.sh` as today ([01](01-client-edge-node.md)).
2. Enroll + approve the node; pin Control Plane keys / mTLS client cert.
3. Ensure the Control Plane has Laya configured ([03](03-control-plane.md) § Hokeka AI).
4. Optionally enable per-PSP `aiInlineMode` in Console → Settings → Hokeka AI.
5. Verify Edge → Control Plane traffic and authenticated `GET /api/v1/ai/status`. Edge health stays `/actuator/health`; there is no local AI health endpoint.

No change to `install.sh` is required for AI advisories to work.

### B. Control Plane install (Hokeka) — where AI must be enabled

Set secrets in `/opt/aml-fraud-detector/.env` and ensure `docker-compose.prod.yml` passes them into `backend-prod` (see §6). Then:

```bash
docker compose -f docker-compose.prod.yml up -d backend-prod
# Authenticated:
curl -sS -H "Authorization: Bearer $TOKEN" https://api.hokeka.com/api/v1/ai/status
```

Expect configured/enabled status when `LAYA_API_KEY` is set and the relevant engine is enabled.

### C. What **not** to do on the client installer

| Idea | Why blocked |
|------|-------------|
| Download Laya beside `edge-host.jar` | Laya Studio is a hosted API, not an Edge artifact distributed by this repository. |
| Add a Laya service to `edge-host/deploy/docker-compose.yml` | This would move provider credentials onto PSP premises and violate the current security boundary. |
| Add a local AI systemd unit | No supported local model binary exists in the product release. |
| Point Edge directly at Laya Studio | Edge-held provider keys are forbidden; the enrolled Control Plane channel is the supported boundary. |
| Ship full BACKEND on-prem for AI | **Removed** product path (`hokeka.auth.enabled` / on-prem lease). Use Edge Node only. |

---

## 5. If product later requires true on-prem “Laya” beside Edge

These are **exact blockers** that must be resolved before extending `install.sh` (not TODOs — prerequisites):

1. **Artifact contract** — publish under `packages.hokeka.com` (or sibling path) with `SHA256SUMS` + GPG, e.g. `laya/` or `ai/<version>/…`, and pin signing the same way as Edge.
2. **Runtime identity** — decide OCI image vs fat JAR vs binary; document port, health URL, resource sizing.
3. **Security model change** — written exception to “never hold AI credentials on Edge”: either air-gapped local model (no OpenRouter key) or HSM/vault-backed key with audit.
4. **Wire protocol** — Edge currently calls Control Plane `/edge/decision`. Need a config switch (`HOKEKA_AI_MODE=controlplane|local`) and client code before installer flags matter.
5. **Compliance** — sanctions and SAR advisory paths assume Control Plane audit. Local mode must write equivalent audit or remain advisory-only with documented gaps.
6. **Release pipeline** — extend `.github/workflows/release.yml` + Hostinger publish scripts; today they only know Edge artifacts.

Until (1)–(4) exist in-repo, **do not** add stub services to the Linux installer.

---

## 6. Production packaging gaps (current)

| Gap | Impact |
|-----|--------|
| Laya credentials are Control Plane-only | Do not add them to Edge compose files, installers, or host environment files. |
| Compose must forward `LAYA_*` / `HOKEKA_AI_*` values | Missing pass-through leaves the provider disabled and the system on deterministic rules fallback. |
| Edge installer has no AI health check | By design — verify Hokeka AI on the Control Plane and the mTLS channel, not on the PSP box. |
| packages.hokeka.com signing key still unpinned in some builds | Blocks *all* customer Edge installs until key is pinned (`docs/INSTALL.md` / architecture doc § outstanding). Independent of AI. |
| No on-prem LLM / Laya package | Cannot “bundle Laya” in offline Edge tarballs. |

---

## 7. Related

- [01 — Client Edge Node](01-client-edge-node.md) — Hokeka AI advisory via Control Plane
- [03 — Control Plane](03-control-plane.md) — `LAYA_*` / `HOKEKA_AI_*` secrets
- [06 — Dual-post](06-psp-api-dual-post-integration.md) — Edge AI advisory row in the dual-post matrix
- `AGENTS.md` / `CLAUDE.md` — Hokeka AI decision layer summary
- `edge-host/.../HokekaAiDecisionClient.java` — Edge → CP client
- `BACKEND/.../service/ai/decision/AiDecisionGateway.java` — Control Plane gateway
- `BACKEND/.../service/ai/decision/LayaSystemOneClient.java` — Laya Studio client
