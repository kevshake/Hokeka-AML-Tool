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
