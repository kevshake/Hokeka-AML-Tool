# On-prem Hokeka service auth

Short operator notes. Full design: [docs/architecture/onprem-service-auth.md](../docs/architecture/onprem-service-auth.md).

## Central (cloud)

1. Set `HOKEKA_AUTH_LEASE_SIGNING_SECRET` to a long random secret.
2. Ensure PSP is `ACTIVE`.
3. `POST /api/v1/admin/onprem/instances` with `{ "pspId", "instanceId", "displayName", "approvedDays" }`.
4. Install returned `clientId` / `clientSecret` on the on-prem host (secret shown once).

## On-prem

```properties
hokeka.auth.enabled=true
hokeka.auth.upstream-url=https://api.hokeka.com
hokeka.auth.client-id=op_…
hokeka.auth.client-secret=…
hokeka.auth.instance-id=psp-ke-node-1
# Optional but recommended — same secret as central for offline lease verify:
hokeka.auth.lease-signing-secret=…
```

Env equivalents: `HOKEKA_AUTH_ENABLED`, `HOKEKA_AUTH_UPSTREAM_URL`, `HOKEKA_AUTH_CLIENT_ID`, `HOKEKA_AUTH_CLIENT_SECRET`, `HOKEKA_AUTH_INSTANCE_ID`, `HOKEKA_AUTH_LEASE_SIGNING_SECRET`.

## Fail-closed

If upstream cannot be reached when a check is due (or the lease expires), the instance enters **STOPPED**: ingest/scoring return `503 SERVICE_AUTHORIZATION_STOPPED` and `onPremLease` health is **DOWN**.

Flyway migration: **V209**.
