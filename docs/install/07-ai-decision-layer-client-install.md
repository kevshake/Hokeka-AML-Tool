# Process 07 — AI decision layer and client (Linux) install

**Audience:** Product / platform engineers asking how “Laya AI” (or Hokeka AI / JEV) is delivered when a PSP installs on Linux.

**Short answer:** There is **no** component named **Laya AI** in this repository. The AI decision layer is **JEV** (operator name) / **Hokeka AI** (tenant-facing brand). It runs **only on the Control Plane**, not inside the client Edge Node Linux installer. Embedding a local AI runtime into `edge-host/deploy/install.sh` is **blocked by architecture** today — and by the absence of any Laya artifact to ship.

---

## 1. Name map (what “Laya AI” likely refers to)

| Name you may hear | What exists in this repo |
|-------------------|--------------------------|
| **Laya AI** | **Not a component.** No Java/TypeScript source, env var, Docker service, image, or `install.sh` uses this name. It appears only in these install notes to record that absence. |
| **JEV** | Internal Control Plane gateway: `JevDecisionGateway` → OpenRouter (`OPENROUTER_API_KEY`, `JEV_MODEL`). Console tab: **Settings → JEV AI** (platform operators only). |
| **Hokeka AI** / **Hokeka Intelligence** | Tenant-facing labels on advisory verdict panels; Edge client class `HokekaAiDecisionClient`. |
| Vendor (OpenRouter / model ids) | **Must never** appear on Edge or PSP-facing Console surfaces (disclosure guards + tests). |

If “Laya” is an external product name or planned rebrand, it is **not yet integrated** — treat this doc as the integration contract until a rename lands in code.

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
                               JevDecisionGateway
                                    │
                                    ▼
                               OpenRouter HTTPS
                               (OPENROUTER_API_KEY stays here)
```

| Concern | Reality |
|---------|---------|
| **Runtime** | In-process Spring beans inside **BACKEND** (`com.posgateway.aml.service.jev.*`). Not a separate JAR, Python app, systemd unit, or Docker service named AI/Laya/JEV. |
| **API** | Edge client `HokekaAiDecisionClient` POSTs `{CONTROLPLANE_URL}/edge/decision` (default base `https://edge.hokeka.com`). Sibling channel calls use `/api/v1/edge/enroll`, `/api/v1/edge/bundle`, and `/api/v1/edge/metrics`. Control Plane servlet is context-path `/api/v1` plus `POST /edge/decision` (`EdgeDistributionController`), so the process route is `POST /api/v1/edge/decision`. Console/ops: `GET /api/v1/jev/status` and `/api/v1/jev/*`. |
| **Config** | `OPENROUTER_API_KEY` and `JEV_MODEL` required to enable. `backend-prod` also forwards `OPENROUTER_BASE_URL`, `JEV_FALLBACK_MODEL`, `JEV_TIMEOUT`, `JEV_INLINE_TIMEOUT`, `JEV_MAX_TOKENS`, `JEV_TEMPERATURE`, `JEV_HTTP_REFERER`, `JEV_APP_TITLE`, `JEV_DAILY_CALL_BUDGET_PER_PSP`, `JEV_DAILY_SPEND_CAP_USD_PER_PSP`. See [03 — Control Plane](03-control-plane.md). |
| **Edge credentials** | **None.** `HokekaAiDecisionClient` only talks to the Control Plane channel. |
| **Failure mode** | Rules baseline; advisory reasons omitted. Regulated paths stay fail-soft to deterministic rules. |

Edge already “embeds” AI **as a client call**, not as a local model server. After a normal Edge install + enrollment + Control Plane JEV config, borderline decisions can request advisories without any AI package on the PSP box.

---

## 3. Current Linux client installer flow (what *is* embedded)

Canonical installer: [`edge-host/deploy/install.sh`](../../edge-host/deploy/install.sh).

| Mode | Ships | Starts |
|------|-------|--------|
| **Container** (default) | `docker-compose.yml`: `aerospike` + `edge` image | Compose stack; health `https://127.0.0.1:8443/actuator/health` |
| **Native** (`--native`) | Temurin JRE 25 (`install_temurin25`), Aerospike CE, `edge-host.jar`, `libedge_engine.so` | systemd unit `hokeka-edge.service` |

**Explicitly not installed on the client box:** Control Plane, Console, OpenRouter key, JEV gateway, Postgres, Kafka, aml-microservice.

Process runbook: [01 — Client Edge Node](01-client-edge-node.md). Architecture: [`docs/architecture/edge-distribution-and-installation.md`](../architecture/edge-distribution-and-installation.md).

---

## 4. Recommended approach (do this — do not invent a parallel installer)

### A. Client Edge install (PSP Linux) — already correct

1. Install Edge with `install.sh` as today ([01](01-client-edge-node.md)).
2. Enroll + approve the node; pin Control Plane keys / mTLS client cert.
3. Ensure Control Plane has JEV configured ([03](03-control-plane.md) § JEV).
4. Optionally enable per-PSP `aiInlineMode` in Console → Settings → JEV AI.
5. Verify Edge → CP path: borderline evaluate traffic and/or Control Plane `GET /api/v1/jev/status` (authenticated). Edge health stays `/actuator/health` — there is no local `/laya/health`.

No change to `install.sh` is required for AI advisories to work.

### B. Control Plane install (Hokeka) — where AI must be enabled

Set secrets in `/opt/aml-fraud-detector/.env` and ensure `docker-compose.prod.yml` passes them into `backend-prod` (see §6). Then:

```bash
docker compose -f docker-compose.prod.yml up -d backend-prod
# Authenticated:
curl -sS -H "Authorization: Bearer $TOKEN" https://api.hokeka.com/api/v1/jev/status
```

Expect configured/enabled status when both `OPENROUTER_API_KEY` and `JEV_MODEL` are set.

### C. What **not** to do on the client installer

| Idea | Why blocked |
|------|-------------|
| Download “Laya” beside `edge-host.jar` | **No artifact / image / repo path exists** to fetch or checksum. |
| Add `laya` service to `edge-host/deploy/docker-compose.yml` | No image; would put vendor keys or a model on PSP premises — contradicts product security model. |
| Add `hokeka-laya.service` systemd unit | Same — no binary. |
| Point Edge at a local OpenRouter base URL | Would require Edge-held API keys (forbidden) or a local proxy still holding secrets. |
| Ship full BACKEND on-prem for AI | **Removed** product path (`hokeka.auth.enabled` / on-prem lease). Use Edge Node only. |

---

## 5. If product later requires true on-prem “Laya” beside Edge

These are **exact blockers** that must be resolved before extending `install.sh` (not TODOs — prerequisites):

1. **Artifact contract** — publish under `packages.hokeka.com` (or sibling path) with `SHA256SUMS` + GPG, e.g. `laya/` or `ai/<version>/…`, and pin signing the same way as Edge.
2. **Runtime identity** — decide OCI image vs fat JAR vs binary; document port, health URL, resource sizing.
3. **Security model change** — written exception to “never hold AI credentials on Edge”: either air-gapped local model (no OpenRouter key) or HSM/vault-backed key with audit.
4. **Wire protocol** — Edge currently calls Control Plane `/edge/decision`. Need a config switch (`HOKEKA_AI_MODE=controlplane|local`) and client code before installer flags matter.
5. **Compliance** — sanctions/SAR advisory paths currently assume Control Plane audit (`JevDecisionAudit`). Local mode must write equivalent audit or remain advisory-only with documented gaps.
6. **Release pipeline** — extend `.github/workflows/release.yml` + Hostinger publish scripts; today they only know Edge artifacts.

Until (1)–(4) exist in-repo, **do not** add stub services to the Linux installer.

---

## 6. Production packaging gaps (current)

| Gap | Impact |
|-----|--------|
| Name “Laya AI” unused in code/docs | Operators cannot find install steps by that name — use JEV / Hokeka AI. |
| `docker-compose.prod.yml` historically omitted JEV env pass-through | Control Plane `.env` values for OpenRouter could be ignored by the container; compose now forwards them (optional, empty = JEV disabled). |
| Edge installer has no AI health check | By design — verify JEV on Control Plane + mTLS channel, not on the PSP box. |
| packages.hokeka.com signing key still unpinned in some builds | Blocks *all* customer Edge installs until key is pinned (`docs/INSTALL.md` / architecture doc § outstanding). Independent of AI. |
| No on-prem LLM / Laya package | Cannot “bundle Laya” in offline Edge tarballs. |

---

## 7. Related

- [01 — Client Edge Node](01-client-edge-node.md) — Hokeka AI advisory via Control Plane
- [03 — Control Plane](03-control-plane.md) — `OPENROUTER_*` / `JEV_*` secrets
- [06 — Dual-post](06-psp-api-dual-post-integration.md) — Edge AI advisory row in the dual-post matrix
- `AGENTS.md` / `CLAUDE.md` — JEV AI decision layer summary
- `edge-host/.../HokekaAiDecisionClient.java` — Edge → CP client
- `BACKEND/.../service/jev/JevDecisionGateway.java` — Control Plane gateway
