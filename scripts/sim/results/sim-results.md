# Hokeka simulation results — run `r1016212351`

Started: 2026-10-16T21:23:51  ·  Finished: 2026-10-16T21:28:53  ·  Control Plane: `http://127.0.0.1:2647/api/v1`

Summary: **FAIL** 3, **PASS** 30, **WARN** 2

| ID | Area | Scenario | Status | Checks | Failed checks |
|---|---|---|---|---|---|
| S0 | seed | Seed PSPs, users, roles, merchants | PASS | 48/48 |  |
| A1 | monitoring | Actuator health / info / metrics exposure | PASS | 15/15 |  |
| A2 | monitoring | Startup configuration warnings (Control Plane log) | WARN | 5/6 | Flyway migrations are free of duplicate DDL (29 'already exists' notices) |
| A3 | schema | Fresh Flyway schema needs no out-of-band patches | PASS | 1/1 |  |
| B1 | auth | Login / me / logout / session | PASS | 10/10 |  |
| B2 | auth | Hard-coded / demo credentials are not usable in production profile | PASS | 8/8 |  |
| B3 | auth | Password reset + change password | PASS | 8/8 |  |
| C1 | rbac | PSP users cannot reach platform endpoints | PASS | 35/35 |  |
| D1 | onboarding | Invite-only onboarding | PASS | 8/8 |  |
| D2 | onboarding | PSP API keys lifecycle and use | PASS | 12/12 |  |
| D3 | onboarding | PSP settings & signal taxonomy mode | PASS | 11/11 |  |
| D4 | billing | Billing, usage, subscriptions, invoices | PASS | 11/11 |  |
| R1 | rules | Rule CRUD with visual-builder JSON + maker/checker + live effect | FAIL | 12/14 | rule id/name cited in decision; below-threshold txn ALLOW |
| R2 | rules | Rule disable / version history / rollback | PASS | 5/5 |  |
| T1 | ingest | Normal traffic at volume (all PSPs, concurrent) | FAIL | 1/3 | normal traffic mostly ALLOW (0/600) — false-positive rate; p95 ingest latency < 2000ms (p95=7051) |
| T2 | ingest | Idempotent ingest (Idempotency-Key / clientReference) | PASS | 7/7 |  |
| T3 | typology | Structuring below CTR threshold | PASS | 2/2 |  |
| T4 | typology | Rapid movement of funds (in then out) | PASS | 2/2 |  |
| T5 | typology | Velocity bursts (1m / 5m windows) | WARN | 2/3 | velocity reason cited |
| T6 | typology | Z-score amount outlier | PASS | 2/2 |  |
| T7 | typology | High-risk MCC (gambling 7995, crypto 6051) | PASS | 4/4 |  |
| T8 | typology | Blacklist hits are tenant-scoped | FAIL | 8/9 | same IP at another PSP not blocked (tenant scope) |
| T9 | typology | Sanctions exact / fuzzy / PEP screening | PASS | 8/8 |  |
| T10 | typology | Tipping-off: PSP-facing payloads carry no filing language | PASS | 2/2 |  |
| C2 | tenant | PSP A cannot see or act on PSP B data | PASS | 12/12 |  |
| C3 | tenant | PSP B users see only their own alerts / transactions | PASS | 9/9 |  |
| L1 | alerts | Alerts list, keyset pagination, prioritisation, resolve | PASS | 11/11 |  |
| L2 | cases | Case management: create, assign, escalate, decide, timeline | PASS | 13/13 |  |
| L3 | reporting | SAR workflow, XML export, CTR/LCTR CSV+PDF, scheme pack, reports, exports | PASS | 21/21 |  |
| AI1 | ai | AI decision layer degrades gracefully with no LAYA key (shadow) | PASS | 4/4 |  |
| E1 | edge | Edge node request / approve / enrollment (real Edge Host) | PASS | 7/7 |  |
| E2 | edge | Rule bundle pull + signature verification + evaluation | PASS | 9/9 |  |
| E3 | edge | Edge metrics shipping (HSE-1) + fleet analytics | PASS | 5/5 |  |
| E4 | dual-post | Edge pre-auth + cloud ingest correlated by edgeAssessmentId | PASS | 14/14 |  |
| E5 | edge | Edge fail-closed + suspend/revoke | PASS | 3/3 |  |

## Failure evidence

### A2 — Startup configuration warnings (Control Plane log) (WARN)
- **WARN** Flyway migrations are free of duplicate DDL (29 'already exists' notices)
- note: boot WARN lines (excluding Flyway 'already exists'): 6; ERROR lines: 0
- note: 2026-10-16 21:22:44.401 WARN  [main] [] org.hibernate.orm.deprecation - HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
- note: 2026-10-16 21:22:51.828 WARN  [main] [] c.p.a.c.s.MpesaCallbackIpAllowlistFilter - MPESA_CALLBACK_ALLOWED_IPS is not configured — the M-Pesa callback endpoint accepts requests from any IP (matches pre-existing behaviour). Set it to Safaricom Daraja's published
- note: 2026-10-16 21:23:01.009 WARN  [main] [] o.d.c.k.builder.impl.KieBuilderImpl - File 'rules/aml-rules.drl' is in folder 'rules' but declares package 'com.posgateway.aml.rules'. It is advised to have a correspondance between package and folder names.
- note: 2026-10-16 21:23:20.344 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.AccountTransfer is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please consider
- note: 2026-10-16 21:23:20.352 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.MerchantRelationship is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please con
- note: 2026-10-16 21:23:22.396 WARN  [main] [] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to di

### R1 — Rule CRUD with visual-builder JSON + maker/checker + live effect (FAIL)
- **WARN** rule id/name cited in decision
  ```json
  {"request": "POST /transactions/ingest", "requestBody": "{\"merchantId\": \"2\", \"amountCents\": 300000, \"currency\": \"USD\", \"pan\": \"4070376300421061\", \"terminalId\": \"T-card-01\", \"ipAddress\": \"198.51.100.187\", \"countryCode\": \"KE\", \"channelType\": \"ECOM\", \"direction\": \"INBOUND\", \"clientReference\": \"r1016212351-e9b3005c1593\", \"acquirerResponse\": \"00\"}", "status": 200, "ms": 6099.0, "response": "{\"txnId\":2,\"action\":\"HOLD\",\"reasons\":[\"SCORING_UNAVAILABLE: manual review required\"],\"latencyMs\":0}"}
  ```
- **FAIL** below-threshold txn ALLOW
  ```json
  {"request": "POST /transactions/ingest", "requestBody": "{\"merchantId\": \"2\", \"amountCents\": 150000, \"currency\": \"USD\", \"pan\": \"4710600081444825\", \"terminalId\": \"T-card-01\", \"ipAddress\": \"198.51.100.109\", \"countryCode\": \"KE\", \"channelType\": \"ECOM\", \"direction\": \"INBOUND\", \"clientReference\": \"r1016212351-a3184d836e46\", \"acquirerResponse\": \"00\"}", "status": 200, "ms": 3497.4, "response": "{\"txnId\":3,\"action\":\"HOLD\",\"reasons\":[\"SCORING_UNAVAILABLE: manual review required\"],\"latencyMs\":0}"}
  ```
- note: PSP compliance officer cannot approve PSP rule (403); using platform checker

### T1 — Normal traffic at volume (all PSPs, concurrent) (FAIL)
- **FAIL** normal traffic mostly ALLOW (0/600) — false-positive rate
  ```json
  {"byAction": {"HOLD": 600}}
  ```
- **FAIL** p95 ingest latency < 2000ms (p95=7051)
  ```json
  {"p95": 7051.063899998553}
  ```
- note: 600 txns in 172.3s (3.5 tps) status={200: 600} actions={'HOLD': 600} latency ms p50=1361 p95=7051 p99=9552

### T5 — Velocity bursts (1m / 5m windows) (WARN)
- **WARN** velocity reason cited
  ```json
  {"reasons": "[\"SCORING_UNAVAILABLE: manual review required\"] [\"SCORING_UNAVAILABLE: manual review required\"] [\"SCORING_UNAVAILABLE: manual review required\"]"}
  ```
- note: velocity-1m: [{"txnId": 627, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}]
- note: velocity actions sequence: ['HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD']; first HOLD/BLOCK at #0

### T8 — Blacklist hits are tenant-scoped (FAIL)
- **FAIL** same IP at another PSP not blocked (tenant scope)
  ```json
  {"request": "POST /transactions/ingest", "requestBody": "{\"merchantId\": \"4\", \"amountCents\": 4000, \"currency\": \"KES\", \"pan\": \"4110625716678204\", \"terminalId\": \"T-momo-01\", \"ipAddress\": \"203.0.113.197\", \"countryCode\": \"KE\", \"channelType\": \"ECOM\", \"direction\": \"INBOUND\", \"clientReference\": \"r1016212351-aa542d0f996e\", \"acquirerResponse\": \"00\"}", "status": 200, "ms": 384.8, "response": "{\"txnId\":653,\"action\":\"HOLD\",\"reasons\":[\"SCORING_UNAVAILABLE: manual review required\"],\"latencyMs\":0}"}
  ```

## Notes per scenario

- **A1**: health components: db=UP, diskSpace=UP, livenessState=UP, neo4j=UP, onPremLease=UP, ping=UP, readinessState=UP, redis=UP, sanctionsData=UP
- **A2**: boot WARN lines (excluding Flyway 'already exists'): 6; ERROR lines: 0 · 2026-10-16 21:22:44.401 WARN  [main] [] org.hibernate.orm.deprecation - HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default) · 2026-10-16 21:22:51.828 WARN  [main] [] c.p.a.c.s.MpesaCallbackIpAllowlistFilter - MPESA_CALLBACK_ALLOWED_IPS is not configured — the M-Pesa callback endpoint accepts requests from any IP (matches pre-existing behaviour). Set it to Safaricom Daraja's published · 2026-10-16 21:23:01.009 WARN  [main] [] o.d.c.k.builder.impl.KieBuilderImpl - File 'rules/aml-rules.drl' is in folder 'rules' but declares package 'com.posgateway.aml.rules'. It is advised to have a correspondance between package and folder names. · 2026-10-16 21:23:20.344 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.AccountTransfer is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please consider · 2026-10-16 21:23:20.352 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.MerchantRelationship is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please con · 2026-10-16 21:23:22.396 WARN  [main] [] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore
- **B3**: reset tokens stored for user: 1
- **R1**: PSP compliance officer cannot approve PSP rule (403); using platform checker
- **R2**: versions: [(302, 'PENDING_APPROVAL'), (301, 'ACTIVE')]
- **T1**: 600 txns in 172.3s (3.5 tps) status={200: 600} actions={'HOLD': 600} latency ms p50=1361 p95=7051 p99=9552
- **T3**: structuring: [{"txnId": 610, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}]
- **T4**: rapid-movement: [{"txnId": 613, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}]
- **T5**: velocity-1m: [{"txnId": 627, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}] · velocity actions sequence: ['HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD', 'HOLD']; first HOLD/BLOCK at #0
- **T6**: zscore-outlier: [{"txnId": 648, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}]
- **T7**: high-risk-mcc-7995: [{"txnId": 649, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}] · high-risk-mcc-6051: [{"txnId": 650, "action": "HOLD", "reasons": ["SCORING_UNAVAILABLE: manual review required"], "alerts": []}]
- **C2**: PSP A /compliance/cases -> 200
- **L1**: priority order sample: ['WARN', 'WARN', 'WARN']
- **L2**: auto-created cases visible to card compliance: 0 · case status after decision: CLOSED_SAR_FILED
- **L3**: CTR rows: 0
- **E2**: /edge/status: {"evaluator":"native","nativeCore":"loaded","nativeDegraded":false,"nativeDegradedReason":"","standbyBundleReady":true,"ruleBundleVersion":1792185892002,"ruleBundleHash":"sha256:0c51709b10737f8b960d50d4a722bf0c54ee5b0c1b634e3fc61a9fc48f20b833","authorization":"active","authorizationReason":"","featureStore":"connected"} · bundle polls: 2026-10-16T18:17:05.889Z GET /api/v1/edge/bundle -> 304 verify=SUCCESS dn="CN=sim-card-r1016211222,O=Hokeka Edge" 30ms | 2026-10-16T18:17:06.847Z GET /api/v1/edge/bundle -> 403 verify=NONE dn="" 3ms | 2026-10-16T18:17:10.906Z GET /api/v1/edge/bundle -> 403 verify=SUCCESS dn="CN=sim-card-r1016211222,O=Hokeka Edge" 7ms | 2026-10-16T18:28:33.984Z GET /api/v1/edge/bundle -> 200 verify=SUCCESS dn="CN=sim-card-r1016212351,O=Hokeka Edge" 138ms · edge velocity sequence: ['ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW', 'ALLOW'] · edge derived features after burst: {"action":"ALLOW","score":0,"triggeredRuleIds":[],"reasons":[],"triggeredRuleVersionIds":[],"edgeAssessmentId":"04ef544d-64b7-4131-af88-e2937e6bbb65","ruleBundleVersion":1792185892002,"ruleBundleHash":"sha256:0c51709b10737f8b960d50d4a722bf0c54ee5b0c1b634e3fc61a9fc48f20b833"} · edge sequential evaluate mean latency 32.5 ms
- **E3**: analytics: {"fromInclusive":"2026-10-15T18:28:37.124901500Z","toExclusive":"2026-10-16T18:28:37.124901500Z","lookbackDays":1,"scope":"PSP","pspId":5,"nodes":1,"totalEvaluated":0,"allowed":0,"alerted":0,"held":0,"blocked":0,"avgP95LatencyMicros":0.0}
