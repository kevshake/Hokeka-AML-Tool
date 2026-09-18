# On-prem PSP service authentication / licensing

PSPs may run the AML Fraud Detector **on-prem**, while **service authorization** is issued by Hokeka central servers (app-to-bot / service-to-service). Local user login is separate; without a valid central lease the on-prem instance **fail-closes**.

## Architecture

```
┌─────────────────────┐         client credentials          ┌──────────────────────────┐
│  On-prem BACKEND    │  ── POST /api/v1/onprem/auth/lease ─▶│  Hokeka central BACKEND  │
│  hokeka.auth.enabled│ ◀──── lease token + validUntil ────│  onprem_instances table  │
│  = true             │         + nextCheckAt (jittered)    │  HMAC-signed lease       │
└─────────────────────┘                                     └──────────────────────────┘
         │
         ├─ Scheduler wakes at nextCheckAt → renew / heartbeat
         ├─ Lease valid for approvedDays (multi-day)
         └─ Unreachable / expired / revoked → STOPPED
              • block ingest + scoring (HTTP 503)
              • /actuator/health → DOWN (onPremLease)
```

## Endpoints

### Machine (on-prem → central) — `permitAll`, auth via body credentials

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/onprem/auth/lease` | Authenticate + grant lease |
| POST | `/api/v1/onprem/auth/renew` | Renew lease (same as lease) |
| POST | `/api/v1/onprem/auth/heartbeat` | Daily check-in (same as lease) |

Request body:

```json
{
  "clientId": "op_…",
  "clientSecret": "…",
  "instanceId": "psp-ke-node-1",
  "hostname": "aml-01.local",
  "agentVersion": "1.2.0"
}
```

Response includes `validUntil`, `nextCheckAt`, `approvedDays`, `checkIntervalDays`, `leaseToken`.

### Admin (platform roles)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/admin/onprem/instances` | Register instance (returns client secret **once**) |
| GET | `/api/v1/admin/onprem/instances` | List (`?pspId=` optional) |
| GET | `/api/v1/admin/onprem/instances/{id}` | Get one |
| PUT | `/api/v1/admin/onprem/instances/{id}/approved-days` | Set lease length (1–365) |
| PUT | `/api/v1/admin/onprem/instances/{id}/revoke` | Revoke (immediate) |
| PUT | `/api/v1/admin/onprem/instances/{id}/suspend` | Suspend |

## Config / environment variables

| Property | Env var | Role |
|----------|---------|------|
| `hokeka.auth.enabled` | `HOKEKA_AUTH_ENABLED` | `true` on on-prem only |
| `hokeka.auth.upstream-url` | `HOKEKA_AUTH_UPSTREAM_URL` | Central base URL |
| `hokeka.auth.client-id` | `HOKEKA_AUTH_CLIENT_ID` | From admin create response |
| `hokeka.auth.client-secret` | `HOKEKA_AUTH_CLIENT_SECRET` | From admin create response |
| `hokeka.auth.instance-id` | `HOKEKA_AUTH_INSTANCE_ID` | Stable machine id |
| `hokeka.auth.lease-signing-secret` | `HOKEKA_AUTH_LEASE_SIGNING_SECRET` | HMAC secret (required on central; optional on on-prem for offline token verify) |
| `hokeka.auth.lease-store-path` | `HOKEKA_AUTH_LEASE_STORE_PATH` | Local lease JSON (default `./data/onprem-lease.json`) |
| `hokeka.auth.check-interval-days` | `HOKEKA_AUTH_CHECK_INTERVAL_DAYS` | Days between assigned check-ins (default 1) |
| `hokeka.auth.timeout-seconds` | `HOKEKA_AUTH_TIMEOUT_SECONDS` | Upstream HTTP timeout |
| `hokeka.auth.scheduler-poll-ms` | `HOKEKA_AUTH_SCHEDULER_POLL_MS` | Local poll interval (default 60000) |

## Fail-closed behaviour

1. **Startup** — if no valid persisted lease (or check-in already due), call upstream. Failure → mode `STOPPED` (JVM still starts for health/diagnostics).
2. **Scheduler** — at `nextCheckAt` (or lease expiry / while STOPPED), renew. Unreachable upstream or auth failure → `STOPPED`.
3. **Gate filter** — blocks transaction ingest, AML/screening, batch, monitoring ingest-style APIs with `503` / `SERVICE_AUTHORIZATION_STOPPED`.
4. **Health** — component `onPremLease` is **DOWN** when operations are not allowed.
5. **Multi-day lease** — `approvedDays` sets `validUntil`; daily (or interval) check-ins still occur at a **server-assigned minute-of-day** derived from `instanceId` so fleets are distributed across 24h.

## Migration

Flyway: `V209__onprem_service_leases.sql` → table `onprem_instances` (+ Envers `onprem_instances_aud`).
