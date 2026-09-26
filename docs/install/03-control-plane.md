# Process 03 — Control Plane Installation (Hokeka VPS)

**Audience:** Hokeka platform operators.

**Product term:** **Control Plane** = `BACKEND/` + `aml-microservice/` + Postgres, Kafka, Redis, Neo4j, Aerospike (cloud sanctions cache), ClamAV, etc. (`docs/SYSTEM-GLOSSARY.md`).

**This is not:** Console-only dev setup ([02](02-console-dashboard.md)), Edge Node ([01](01-client-edge-node.md)), or packages CDN ([04](04-packages-cdn-and-edge-release.md)).

**Canonical method:** Docker Compose — `docker-compose.prod.yml` at repo root. Do not hand-assemble services.

**Hosting:** Hostinger VPS (not AWS). Host nginx terminates TLS; application containers bind **127.0.0.1** only.

---

## Prerequisites checklist

- [ ] Linux VPS with Docker Engine + Compose v2
- [ ] DNS: `api.hokeka.com`, `aml.hokeka.com` (and test equivalents if used)
- [ ] Ports **80/443** open publicly; nothing else published from app containers
- [ ] SSH access for deployment user
- [ ] Real secrets prepared **before** first `docker compose up` (see below)

---

## Step 1 — Bootstrap server (optional script)

```bash
./deploy-to-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>
```

This installs Docker, rsyncs the repo to `/opt/aml-fraud-detector`, and may create `.env.prod`.

> **STOP:** `deploy-to-hostinger.sh` writes placeholder `CHANGE_ME_*` secrets and variable names (`DB_HOST`, `DB_USER`, …) that **`docker-compose.prod.yml` does not read**. Treat the script as Docker bootstrap only. Write the real `.env` per Step 2 **before** the first production `up`.

---

## Step 2 — Secrets (`/opt/aml-fraud-detector/.env`)

Compose **refuses to render** if required `${VAR:?}` values are missing.

### Required (compose fails without these)

```bash
DATABASE_USERNAME=<db user>
DATABASE_PASSWORD=<strong unique password>
AUDIT_HMAC_KEY=<32-byte key>
PII_LOOKUP_HMAC_KEY=<32-byte key>
NEO4J_PASSWORD=<strong unique password>
```

### Required in practice (auth/encryption broken without them)

```bash
JWT_SECRET=<256-bit secret>
ENCRYPTION_KEY=<32-byte key>
AML_MS_INTERNAL_KEY=<shared secret: backend → aml-ms>
AML_INTERNAL_API_KEY=<same value as AML_MS_INTERNAL_KEY>
```

Generate each secret independently: `openssl rand -base64 32`. Never reuse across variables.

**AML microservice alias** — one credential, two names:

```bash
cd /opt/aml-fraud-detector
umask 077
AML_KEY="$(openssl rand -base64 48)"
printf 'AML_MS_INTERNAL_KEY=%s\nAML_INTERNAL_API_KEY=%s\n' "$AML_KEY" "$AML_KEY" >> .env
unset AML_KEY
```

### Defaults (override per environment)

```bash
CORS_ALLOWED_ORIGINS=https://aml.hokeka.com,https://testaml.hokeka.com
KAFKA_BOOTSTRAP_SERVERS=kafka-prod:29092
```

`CORS_ALLOWED_ORIGINS` must include every Console origin ([02-console-dashboard.md](02-console-dashboard.md)).

### Hokeka AI decision layer (Laya Studio — Control Plane only)

[Laya Studio](https://laya.studio) credentials live **only** on the Hostinger Control Plane VPS — never on Edge Nodes or in the Console bundle. Until `LAYA_API_KEY` is set, the platform runs rules-only (no AI mutations).

```bash
LAYA_API_KEY=lsk_live_…              # required to enable Hokeka AI (dashboard → API keys)
LAYA_API_BASE_URL=https://api.laya.studio
LAYA_MODEL=                          # optional: english | multilingual | typed-decisions (blank = auto)
LAYA_LANG=                           # optional hint, e.g. en or de-CH
LAYA_DECISIONS_TIMEOUT=3s
LAYA_ASK_TIMEOUT=30s
LAYA_MAX_RETRIES=2
LAYA_MIN_CONFIDENCE_TO_APPLY=0.55    # below → rules + human review
LAYA_MIN_CONFIDENCE_TO_AUTO_ACT=0.72 # below → no auto-apply when promoted
HOKEKA_AI_SHADOW_MODE=true           # default log-only
HOKEKA_AI_PROMOTED=false             # both promoted + shadow off required to apply
HOKEKA_AI_INLINE_TIMEOUT=500ms
HOKEKA_AI_DAILY_CALL_BUDGET_PER_PSP=0
LAYA_DAILY_INPUT_TOKEN_CAP_PER_PSP=0 # 0 = unlimited input tokens / day / PSP
LAYA_SWISS_DATA_RESIDENCY=false      # optional Swiss-only routing
```

Health/status (no key exposure): `GET /api/v1/jev/status` (authenticated). Platform Admins configure per-engine toggles and PSP `aiInlineMode` in Console → Settings → Hokeka AI.

Optional rule-generator toggle (`AI_RULE_GENERATOR_ENABLED`) uses Laya `/v1/ask` on the Control Plane when enabled.

### Fixed inside compose (not from `.env`)

- `DATABASE_URL=jdbc:postgresql://postgres-prod:5432/fraud_detector`
- `SPRING_PROFILES_ACTIVE=production`
- `SERVER_PORT=2637`
- Internal hostnames: `aml-ms-prod`, `kafka-prod`, `redis-prod`, `neo4j-prod`, `clamav-prod`

Full reference: [`docs/INSTALL.md`](../INSTALL.md) §1.3.

---

## Step 3 — Bring up the stack

```bash
cd /opt/aml-fraud-detector
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps    # every service (healthy)
```

**Startup order:** `backend-prod` waits for postgres, aml-ms, clamav, kafka, redis, neo4j. ClamAV first boot may take **~120s** for signature download — slow first `up` is normal.

**Services included:**

| Service | Role |
|---------|------|
| `backend-prod` | Control Plane API (:2637 loopback) |
| `frontend-prod` | Console static SPA (:8088 loopback) — see [02](02-console-dashboard.md) |
| `aml-ms-prod` | Aerospike-backed sanctions lookups (internal :8091) |
| `postgres-prod`, `kafka-prod`, `redis-prod`, `neo4j-prod`, `aerospike-prod`, `clamav-prod` | Data / pipeline |
| `website-prod` | Marketing site (internal) |

**Edge Node is not in this compose file** — PSPs install edge separately.

---

## Step 4 — Verify health

```bash
curl -sf http://127.0.0.1:2637/actuator/health
curl -sf http://127.0.0.1:8088/
```

Checklist:

- [ ] No `CHANGE_ME_*` in `.env`
- [ ] All five `${VAR:?}` secrets set
- [ ] `SPRING_PROFILES_ACTIVE=production` — **never `testenv`** (`ddl-auto=create-drop`, destroys DB)
- [ ] Flyway migrations applied; `ddl-auto=validate`
- [ ] Every container `(healthy)` in `docker compose ps`
- [ ] No container on `0.0.0.0` except host nginx
- [ ] Aerospike **not** reachable from backend directly — only via `aml-ms-prod`

---

## Step 5 — Host nginx and TLS

Containers listen on loopback only. Host nginx is the sole public listener.

From `BACKEND/nginx/fraud-detector-api.conf`:

- **`api.hokeka.com`** → `127.0.0.1:2637` (Control Plane API)
- **`aml.hokeka.com`** → `127.0.0.1:8088` (Console) + proxy `/api/v1` to `127.0.0.1:2637` if same-origin

```bash
sudo certbot --nginx -d aml.hokeka.com -d api.hokeka.com
sudo certbot renew --dry-run
sudo nginx -t && sudo systemctl reload nginx
```

---

## Step 6 — Schema changes (Flyway)

All schema changes: new sequential migration only.

```
BACKEND/src/main/resources/db/migration/V###__description.sql
```

Never edit applied migrations. Never rely on `ddl-auto` in production.

Apply on deploy: backend container runs Flyway on startup.

---

## Updates

```bash
./deploy-update-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>
```

On VPS manually:

```bash
cd /opt/aml-fraud-detector
git pull origin main
docker compose -f docker-compose.prod.yml up -d --build
```

`.env` is untouched. Script does full `down` then `up` — **not zero-downtime**; schedule maintenance.

---

## Non-negotiables

| Rule | Reason |
|------|--------|
| Profile **`production`** only | `testenv` drops schema on restart |
| Flyway-only schema | Envers + multi-profile DDL safety |
| No Aerospike from backend JVM | Sanctions path via `AML_MICROSERVICE_URL` |
| No `0.0.0.0` on app containers | Host nginx is only public surface |
| No on-prem lease BACKEND | Removed product path — Edge Node instead |

---

## Verification checklist (copy for runbook)

**Control plane**

- [ ] No `CHANGE_ME_*` anywhere in `.env`
- [ ] All five `${VAR:?}` secrets set; each generated independently
- [ ] `SPRING_PROFILES_ACTIVE=production`
- [ ] Flyway clean; `ddl-auto=validate`
- [ ] `docker compose ps` — all `(healthy)`
- [ ] TLS valid and auto-renewing
- [ ] `CORS_ALLOWED_ORIGINS` matches Console hostnames

---

## Related

- [Installation process map](README.md)
- [02 — Console](02-console-dashboard.md)
- [04 — Packages CDN](04-packages-cdn-and-edge-release.md)
- [`docs/INSTALL.md`](../INSTALL.md) §1 — full canonical reference
- `BACKEND/DEPLOYMENT.md` — **superseded** for install; nginx/tuning reference only
