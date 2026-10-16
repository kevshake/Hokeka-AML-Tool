# DB-level tenant isolation backstop (W35-1)

Application code scopes most queries through `PspIsolationService` and explicit `psp_id`
predicates. As defense-in-depth, PSP-scoped entities also carry a Hibernate `@Filter`
(`pspTenantFilter`) enabled before every `com.posgateway.aml.repository.*` call.

## Mechanism

| Piece | Role |
|---|---|
| `RlsContextFilter` | Sets `RlsContextHolder` from the authenticated user's PSP on each HTTP request |
| `PspFilterEnabler` | AOP `@Before` on all repositories — binds filter param from the thread-local |
| `PspTenantWriteGuard` | Blocks `save` / `saveAll` when entity `psp_id` ≠ tenant context |
| `@Filter(name = "pspTenantFilter")` | On `TransactionEntity`, `Alert`, `ComplianceCase`, `User`, `Merchant` |

Filter SQL (via `config/tenant/package-info.java`):

```sql
(:pspId = 0 OR psp_id = :pspId)
```

`pspId = 0` is the platform-administrator sentinel (same convention as
`PspIsolationService.getCurrentUserPspId()`). Background jobs with no thread-local bind `0`
and therefore remain unfiltered.

## Limits

- Native SQL (`nativeQuery = true`) is **not** filtered — those queries must stay explicitly scoped.
- `EntityManager.find` / `getReference` may bypass the filter on this Hibernate version; prefer repository
  queries (HQL/Criteria) which are filtered when `PspFilterEnabler` runs.
- Hibernate Envers audit tables are not filtered.
- Alerts with null `psp_id` after V222 backfill remain visible only to platform admins until repaired.

## Data residency: isolation between PSPs and between Edge and cloud

_Added 2026-10-17 (R14a). Normative source: [`EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md)._

The residency model adds a second boundary. Tenant data is split by **where it may live**, not
only by `psp_id`. Each PSP's transaction data lives on its own Edge Site. The cloud holds only
what the contract allows.

### Data classes (contract §2)

| Class | Meaning | Where it may live |
|---|---|---|
| S | Secret: raw PAN, CVV, track data, PIN block, expiry | Nowhere. The Edge drops these keys before it stores features (`edge-host` `contract.FeatureHygiene`). |
| E | Edge-only: `pan_hash`, velocity keys | Edge Ledger, Aerospike, Edge memory. Never leaves the site, not even over the tunnel. |
| D | Decision record: ids, decision, score, rule ids, bundle version, flags | Edge Ledger; cloud `assessments`. |
| A | Aggregate: hourly counts and sums per merchant and currency | Edge memory; cloud metrics tables. |
| T | Tunnel-transient: amount, currency, country, features, names, reasons | Edge Ledger. In the cloud only in memory for one request, and in the Console's memory. |
| F | Filed: class-T data inside a filed SAR/STR/CTR document | Cloud filed-document store (planned, R9). |
| C | Cloud master data: merchants, rules, users, cases | Cloud. |

### What the cloud stores for residency today

| Table | Migration | Contents | Tenant key |
|---|---|---|---|
| `psps` columns `retention_years`, `support_access_enabled`, `cross_psp_opt_in`; `psp_data_setting_changes`; `psp_edge_settings`; `edge_limits`; `edge_merchant_blocklist`; `edge_config_versions` | V261 | PSP settings, maker-checker change requests, Edge config inputs and versions | `psp_id` |
| `assessments` decision-record columns; `decision_stats_hourly` | V262 | Decision records v2 (class D only) and the hourly rollup | `psp_id` |
| `edge_data_access_log`; `edge_nodes.site_id` | V263 | One row per tunnel request: actor, purpose, op, params summary, outcome. Never the items. | `psp_id` |

The Hibernate filter above does not cover these tables. Their readers scope by `psp_id`
explicitly.

### Tunnel isolation

- **Handshake.** The cloud resolves the Edge identity from the mTLS certificate (`EdgeIdentityResolver`) and requires the node `ACTIVE` before the WebSocket upgrade. The `hello` frame must carry the node's registered `siteId` and `pspId`, or the cloud refuses the session (`TunnelSessionHub`, close 4403).
- **Registry.** `TunnelRegistry` keeps live sessions per PSP in the Redis hash `tunnel:psp:{pspId}`. A request for PSP A can only route to a session in A's hash.
- **Cloud gate.** `EdgeDataGateway` refuses a request whose actor belongs to another PSP with `TENANT_MISMATCH`, before it sends anything. A platform user needs the PSP's support-access flag (read on every pull by `JdbcSupportAccessPolicy`), purpose `SUPPORT_ACCESS`, a 10–500 character reason and a platform role. Otherwise the gateway answers `SUPPORT_ACCESS_DISABLED`.
- **Edge gate (planned, R5).** The Edge will re-check `actor.pspId` against its own PSP (`TENANT_MISMATCH`) and refuse platform users when its config has support access off. The Edge tunnel client does not exist yet, so this second gate is not in place.
- **Relay.** Cross-instance routing uses Redis pub/sub channels `tunnel:cp:{instance}` and `tunnel:reply:{instance}`. Relayed frames stay HSE-1 sealed (`RelayMessage`): the instance that holds the session forwards the Edge's bytes unopened, so Redis never sees plaintext transaction data.
- **Audit.** `EdgeDataAccessAuditor` writes the cloud `edge_data_access_log` row before it sends a request. The PSP-facing endpoint `GET /api/v1/edge-data/access-log` and its Console viewer are planned (R6, R11). Only the repository query exists.

### Residency gaps that remain

- The cloud `transactions` table and its dependants (`transaction_features`, `alerts.pan_hash`, mobile-money, multi-asset and travel-rule stores) still exist. `/api/v1/transactions/ingest` still writes them. R15 back-fills each PSP's Edge; R12 deletes the ingest path and purges the tables.
- Cross-PSP intelligence is opt-in (`psps.cross_psp_opt_in`) and limited by contract to indicators from filed SARs. The indicator set and its distribution are planned (R10b).
