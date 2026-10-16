# Live readiness report: 2026-10-16

Branch `wp-0a-risk-score-hygiene`. The simulator lives in `scripts/sim/`, and its last results are in `scripts/sim/results/sim-results.md` (run `r1016212351`).

## Verdict: not ready for LIVE

The platform passes every simulated scenario except cloud ingest scoring. Cloud ingest is being replaced: the owner decided the Edge is the only place transactions are evaluated, and the cloud never stores them (`docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md`). Live readiness therefore depends on that redesign, and on the blockers listed below.

## How the simulation ran

- **Stack:** an empty database, with Flyway applying V1 through V260 and Hibernate `validate` passing. The Control Plane ran under the production profile, alongside the aml-microservice and a native Rust Edge engine plus edge-host behind an mTLS proxy.
- **PSPs:** three simulated PSPs (a card acquirer, a mobile-money PSP and a VASP), each with users for every role, merchants and an MLRO.
- **Memory caps:** Control Plane 1.2 GB, aml-ms 384 MB, edge-host 384 MB, with small data-service heaps. No process was killed for memory.

## Results (35 scenarios)

| Area | Scenarios | Result |
|---|---|---|
| Seeding, health, schema | S0, A1, A3 | PASS |
| Startup log hygiene | A2 | WARN: 29 duplicate-DDL Flyway notices |
| Auth, default credentials, password reset | B1–B3 | PASS |
| PSP vs platform isolation, cross-tenant ingest and edge adoption | C1–C3 | PASS |
| Onboarding, API keys, settings, billing | D1–D4 | PASS |
| Rule governance | R2 | PASS |
| Rule enforcement on cloud ingest | R1 | FAIL: superseded by the redesign |
| Typologies on cloud ingest | T1, T5, T8 | FAIL/WARN: superseded |
| Typologies on cloud ingest | T3, T4, T6, T7 | PASS, but meaningless: every cloud ingest returns HOLD (S1) |
| Ingest validation, PEP/sanctions, tipping-off | T2, T9, T10 | PASS |
| Lists and exports, case workflow, SAR/STR/CTR reports | L1–L3 | PASS |
| AI shadow mode (no provider disclosed to PSPs) | AI1 | PASS |
| Edge enroll, bundle, enforcement, assessment correlation, suspend/revoke | E1–E5 | PASS |

**Edge:** the native core loaded and the signed bundle armed. Mean evaluation latency was 28 ms. The Edge-minted `edgeAssessmentId` was shipped over mTLS and correlated idempotently. Suspend makes the Edge fail closed.

## Defects found and fixed

35 in total, each with a regression test.

### Wave 73: 19 fixes from the first partial run

**Critical:**
- Demo password `Hokeka2026!` worked in production. Demo users are now created only in dev/test, and V253 disables the seeded accounts.
- Envers `revinfo_seq` was missing.
- 47 entity/schema mismatches. Schema validation had been silently switched off; V255 fixes the mismatches.
- Production boot failed with Neo4j enabled, and on the session timeout setting.

**High:**
- PSP API keys authenticated nothing. There is now an API-key filter.
- Ingest did not check that the merchant belongs to the calling PSP.
- Secrets were missing from the production compose file.
- Sanctions count timed out, so health stayed DOWN.

**Medium/low:**
- Mail health was DOWN with no SMTP host configured.
- Kafka topics were never created.
- Ingest returned 500s with empty bodies.
- `/cases/{id}/escalate` had a duplicate mapping.
- The AI client called the wrong path.
- The Edge returned no assessment id.
- There was no blacklist API.
- PSP users could read SARs.
- Super admins got 403 on other PSPs.
- Neo4j GDS errors were logged on every run.

### Simulation run 5: 16 fixes

- The Control Plane failed to boot: there were two `AuditLogService` beans.
- **Every ingest returned an empty body.** The usage filter broke async responses.
- CSV and case exports returned 500.
- **The first Edge bundle fetch always returned 500**, which left every Edge fail-closed.
- Edge analytics returned 500.
- Rule rejection returned 500 instead of 409.
- **Security:** an API-key call issued a session cookie, and that cookie then bypassed key revocation.
- Compliance officers could not create cases or SARs.
- Every case insert failed on a legacy NOT NULL column (V259).
- PEPs were reported as sanctions hits.
- The Edge showed an AI recommendation with no AI configured.
- SAR creation failed on foreign keys pointing at the old users table, and every case creation returned 503 because of the audit key (V260).
- A race in case enrichment.
- Manual reports stayed PENDING forever because no security context reached the worker.
- API-key ingest was throttled at 120/min instead of 1200.
- **Case escalation could assign another PSP's MLRO** (a tenancy breach).

**Verification:** BACKEND 731 tests, 0 failures. The 2 errors come from the Docker environment, not product code. aml-microservice 41/41, edge-host 72/72.

## Blockers before LIVE

### Architecture: owner decision, plan approved
1. **Edge-only redesign** (work packages R0–R15 in the plan):
   - an Edge ledger with 5-year retention;
   - the outbound tunnel;
   - decision records in place of cloud transactions;
   - Console live pulls;
   - back-fill of existing cloud data to the Edge, then purge.
2. **Edge typology coverage.** The ~60 seeded typology rules are SpEL, and `EdgeRuleCompiler` drops them. Structuring, velocity, z-score, blacklist and high-risk-country rules do not run on the Edge today; in E2, a 12-transaction burst stayed ALLOW. This is work package R10a.
3. **Existing PII leak.** The Edge AI client POSTs the full feature bag to the cloud. This is work package R10c.
4. **Cloud ingest scoring** always returns HOLD, and its p95 latency is 7 s. Customer IPs are also sent to ipapi.co. This code is removed by the redesign (R12) and is not being fixed.

### Product decisions still needed
5. **Travel Rule (VASP):** should the originator/beneficiary exchange happen Edge-to-Edge, or be relayed through the cloud without being stored?
6. **Rate limits:** the docs advertise 1000 RPM (Standard) and 5000 RPM (Premium), but the code applies a flat 120/min per tenant.
7. **Case retention:** deleting a merchant cascades and deletes its compliance cases.
8. **Onboarding:** a beneficial owner who exactly matches a sanctions list gets REVIEW, not REJECT.
9. **Case archival:** it needs an object store and a deletion policy. Today the cold-storage upload is not performed and expired-case deletion is disabled.
10. **PEP data:** production has no PEP source unless `SANCTIONS_DOWNLOAD_ENABLED=true` is set.

### Engineering follow-ups
11. **CSRF is disabled** (`SecurityConfig.java:120`), although the docs say it is on.
12. **Demo PSPs** (`DEMO_VELOCITY`, `DEMO_MWANANCHI`, `DEMO_APEX`) are created by V127 in production.
13. **Session handling:** unauthenticated ingest creates sessions, and per-PSP role templates are not provisioned at registration.
14. **Merchant errors:** `MerchantController` returns 500 with an empty body on errors.
15. **Audit chain:** two audit writes at the same moment can fork the chain.
16. **Flyway:** 29 duplicate-DDL notices.

### Infrastructure and credentials (owner/ops)
17. **Hosting:**
    - DNS for `packages.hokeka.com`;
    - an offline release-key ceremony and pinning;
    - repository secrets and the VPS env files;
    - a real nginx mTLS front end, with WebSocket support for the tunnel.
18. **Service credentials:** a LAYA AI key, SMTP, ClamAV, adverse-media and corporate-registry APIs, `MPESA_CALLBACK_ALLOWED_IPS`, and the Neo4j GDS plugin.
19. **Production env:** `ENCRYPTION_KEY` (base64, 32 bytes), `PII_LOOKUP_HMAC_KEY`, `AUTH_PASSWORD_RESET_PEPPER`, `SEED_ADMIN_PASSWORD`, `JWT_SECRET`, `AML_MS_INTERNAL_KEY`.
20. **Installer:** a real full install of #42 on a clean Linux VM.
