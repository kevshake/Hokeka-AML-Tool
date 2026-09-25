# Process 02 — Console (Dashboard) Installation

**Audience:** Hokeka platform operators and frontend developers.

**Product term:** **Console** = operator SPA in `FRONTEND/` — alerts, cases, Edge fleet management, rules, reports (`docs/SYSTEM-GLOSSARY.md`).

**This is not:** Edge Node (PSP on-prem), Control Plane API internals, or PSP API integration.

> **Console is never installed on the PSP edge box.** PSP engineers install Edge only ([01 — Client Edge Node](01-client-edge-node.md)).

---

## What Console is

The **Console** is a React 18 + TypeScript + Vite + MUI single-page application. It talks to the **Control Plane** REST API (`BACKEND/` on port **2637** in dev, proxied in production).

Operators use Console to:

- Log in, manage PSPs and users
- View alerts and cases
- Enroll and approve **Edge Nodes** (Setup Wizard generates install commands for clients)
- Configure rules, limits, and reporting
- **JEV AI** — Platform Admin status, per-engine toggles, PSP inline mode, and AI verdict panels on alert/case/screening/KYC detail views (advisory recommendations with audit trail)

Console does **not** evaluate transactions and does **not** replace Edge Node or dual-post API wiring. Console never holds the OpenRouter API key.

---

## Production installation (Hokeka Hostinger VPS)

Production Console ships as the **`frontend-prod`** service in `docker-compose.prod.yml`. It is built and deployed **with** the Control Plane stack — but it is a **separate process document** because operators must not confuse it with client Edge installs.

### Architecture

```
Internet
   │
   ▼
Host nginx (TLS termination, public :443)
   │
   ├─ aml.hokeka.com / testaml.hokeka.com
   │     └─► 127.0.0.1:8088  (frontend-prod — static SPA + container nginx)
   │     └─► /api/v1/* proxied to 127.0.0.1:2637 (Control Plane) when same-origin
   │
   └─ api.hokeka.com
         └─► 127.0.0.1:2637  (backend-prod — Control Plane API)
```

Reference nginx templates: `BACKEND/nginx/fraud-detector-api.conf` (API host). Console host blocks follow the same TLS pattern; app UI targets **`127.0.0.1:8088`**.

### Build and serve (`frontend-prod`)

From `docker-compose.prod.yml`:

```yaml
frontend-prod:
  build:
    context: ./FRONTEND
    dockerfile: Dockerfile
  ports:
    - "127.0.0.1:8088:80"
```

The **multi-stage Dockerfile** (`FRONTEND/Dockerfile`):

1. **Builder:** Node 20 Alpine — `npm ci`, `npm run build:prod` (Vite production build → `dist/`).
2. **Runtime:** nginx 1.25 Alpine serves static assets from `/usr/share/nginx/html` using `FRONTEND/nginx.prod.conf` (SPA `try_files`, gzip, `/health`).

Only **loopback 8088** is exposed; host nginx is the public surface — same rule as Control Plane ([03-control-plane.md](03-control-plane.md)).

### Environment / API base URL

Vite embeds env at **build time** (not runtime):

| Variable | Production typical | Purpose |
|----------|-------------------|---------|
| `VITE_API_URL` | empty or `/api` per deploy | Prefix before `/api/v1/...` in `FRONTEND/src/config/api.ts` |
| `VITE_APP_NAME` | `Fraud Detector` | UI title |
| `VITE_ENABLE_DEBUG` | `false` | Debug logging |

Files: `FRONTEND/.env.production`, `FRONTEND/.env.development`.

With **empty** `VITE_API_URL`, the Console uses **relative** URLs (`/api/v1/...`) on the Console origin — host nginx must proxy those paths to Control Plane, **or** operators configure cross-origin calls to `https://api.hokeka.com` with CORS.

### CORS relationship to Control Plane

Control Plane reads allowed browser origins from **`CORS_ALLOWED_ORIGINS`** in `/opt/aml-fraud-detector/.env`:

```bash
CORS_ALLOWED_ORIGINS=https://aml.hokeka.com,https://testaml.hokeka.com
```

Default in `docker-compose.prod.yml` matches production Console hostnames. If Console is served from a new domain, add it here **and** update host nginx — otherwise browser login and API calls fail.

Session cookies use Secure, HttpOnly, SameSite=Strict under the `production` profile.

### Bring up Console with the stack

Console is not started in isolation in production — it is part of the full compose up documented in [03 — Control Plane](03-control-plane.md):

```bash
cd /opt/aml-fraud-detector
docker compose -f docker-compose.prod.yml up -d --build
curl -sf http://127.0.0.1:8088/          # Console static root
curl -sf http://127.0.0.1:8088/health     # container nginx health
```

After host nginx + TLS (`certbot` for `aml.hokeka.com`): open `https://aml.hokeka.com` in a browser.

---

## Local / development Console installation

For Hokeka engineers working on UI — **not** for PSP client servers.

### Prerequisites

| Tool | Version |
|------|---------|
| Node.js | 20+ (matches `FRONTEND/Dockerfile`) |
| npm | bundled with Node |
| Running Control Plane | `BACKEND/` on port **2637** (see [05 — Local developer stack](05-local-developer-stack.md)) |

### Steps

```bash
cd FRONTEND
npm install
```

Optional: copy or edit `.env.development`:

```bash
# FRONTEND/.env.development
VITE_API_URL=
VITE_APP_NAME=Fraud Detector Dev
VITE_ENABLE_DEBUG=true
```

Start Vite dev server:

```bash
npm run dev
```

- Console URL: **http://localhost:5173**
- Vite proxies **`/api/v1`** → Control Plane (`vite.config.ts`):

```typescript
proxy: {
  '/api/v1': {
    target: process.env.VITE_PROXY_TARGET || 'http://localhost:2637',
    ...
  },
}
```

Point at a remote Control Plane when needed:

```bash
VITE_PROXY_TARGET=https://testapi.hokeka.com npm run dev
```

### Other dev commands

```bash
npm run typecheck    # TypeScript only
npm run lint         # zero-warning policy
npm run build        # tsc + vite build
npm run preview      # serve production build locally
```

---

## Verification checklist

**Production**

- [ ] `curl -sf http://127.0.0.1:8088/` returns HTML from `frontend-prod`
- [ ] `https://aml.hokeka.com` loads login page over valid TLS
- [ ] Login succeeds; session cookie set
- [ ] **Edge Nodes** page loads; Setup Wizard shows install command
- [ ] Alerts list loads (proves API connectivity + CORS)
- [ ] No Console container on `0.0.0.0` — only `127.0.0.1:8088`

**Local dev**

- [ ] `npm run dev` on 5173
- [ ] Control Plane reachable at proxy target (default `localhost:2637`)
- [ ] Login works via proxied `/api/v1/auth/login`

---

## Updates / rebuild

Production Console updates when Hokeka redeploys the stack:

```bash
./deploy-update-hostinger.sh <VPS_IP> <SSH_USER> <SSH_KEY_PATH>
# or on VPS:
cd /opt/aml-fraud-detector && git pull && docker compose -f docker-compose.prod.yml up -d --build frontend-prod
```

Vite assets are **immutable-hashed** in `dist/`; nginx sets long cache headers on static files. Full rebuild required for UI changes — there is no runtime env injection in the nginx stage.

---

## What Console is **not**

| Misconception | Reality |
|---------------|---------|
| Install Console on PSP edge server | **Wrong** — Edge runs headless API only |
| Console evaluates transactions | **Wrong** — Edge Node `/edge/evaluate` |
| Lowercase `frontend/` folder | **Orphan** — use **`FRONTEND/`** only |
| Console replaces dual-post | **Wrong** — see [06 — dual-post](06-psp-api-dual-post-integration.md) |

---

## Related

- [Installation process map](README.md)
- [03 — Control Plane](03-control-plane.md) — secrets, compose, host nginx
- [01 — Client Edge Node](01-client-edge-node.md) — what PSPs install instead
- [`docs/INSTALL.md`](../INSTALL.md) §1.5 — host nginx and TLS
