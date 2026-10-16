# Hokeka simulation results — run `r1016174257`

Started: 2026-10-16T17:42:57  ·  Finished: 2026-10-16T17:48:31  ·  Control Plane: `http://127.0.0.1:2637/api/v1`

Summary: **FAIL** 6, **PASS** 6

| ID | Area | Scenario | Status | Checks | Failed checks |
|---|---|---|---|---|---|
| S0 | seed | Seed PSPs, users, roles, merchants | PASS | 42/42 |  |
| A1 | monitoring | Actuator health / info / metrics exposure | FAIL | 13/16 | anonymous /actuator/health is UP; health component mail UP; health component sanctionsData UP |
| A2 | monitoring | Startup configuration warnings (Control Plane log) | FAIL | 4/6 | EnvVarStartupValidator reports 0 MISSING; no ERROR lines during boot |
| AI1 | ai | AI decision layer degrades gracefully with no LAYA key (shadow) | PASS | 3/3 |  |
| B1 | auth | Login / me / logout / session | FAIL | 10/11 | Authorization: Bearer <token> authenticates (docs contract) |
| B2 | auth | Hard-coded / demo credentials are not usable in production profile | FAIL | 0/8 | 'admin' / 'Hokeka2026!' must not log in on the production profile; 'super.admin' / 'Hokeka2026!' must not log in on the production profile; 'platform.admin' / 'Hokeka2026!' must not log in on the production profile; 'velocity.admin' / 'Hokeka2026!' must not log in on the production profile; 'compliance' / 'Hokeka2026!' must not log in on the production profile; 'analyst' / 'Hokeka2026!' must not l |
| B3 | auth | Password reset + change password | PASS | 8/8 |  |
| C1 | rbac | PSP users cannot reach platform endpoints | PASS | 35/35 |  |
| D1 | onboarding | Invite-only onboarding | PASS | 8/8 |  |
| D2 | onboarding | PSP API keys lifecycle and use | FAIL | 5/6 | issued API key authenticates PSP ingest (X-Api-Key or Bearer) |
| D3 | onboarding | PSP settings & signal taxonomy mode | PASS | 11/11 |  |
| D4 | billing | Billing, usage, subscriptions, invoices | FAIL | 9/10 | PSP admin GET /billing/rates |

## Failure evidence

### A1 — Actuator health / info / metrics exposure (FAIL)
- **FAIL** anonymous /actuator/health is UP
  ```json
  {"request": "GET /actuator/health", "requestBody": null, "status": 503, "ms": 15649.0, "response": "{\"status\":\"DOWN\",\"groups\":[\"liveness\",\"readiness\"]}"}
  ```
- **WARN** health component mail UP
  ```json
  {"component": "mail", "detail": {"status": "DOWN", "details": {"location": ":587", "error": "jakarta.mail.AuthenticationFailedException: failed to connect, no password specified?"}}}
  ```
- **WARN** health component sanctionsData UP
  ```json
  {"component": "sanctionsData", "detail": {"status": "DOWN", "details": {"watchlistRecords": -1, "status": "UNAVAILABLE", "reason": "Could not read sanctions count from aml-microservice; screening data status is unknown."}}}
  ```
- note: health components: db=UP, diskSpace=UP, livenessState=UP, mail=DOWN, neo4j=UP, onPremLease=UP, ping=UP, readinessState=UP, redis=UP, sanctionsData=DOWN

### A2 — Startup configuration warnings (Control Plane log) (FAIL)
- **FAIL** EnvVarStartupValidator reports 0 MISSING
  ```json
  {"line": null}
  ```
- **WARN** no ERROR lines during boot
  ```json
  {"errors": ["2026-10-16 17:20:51.539 ERROR [main] [] o.h.m.internal.MetadataContext - HHH015007: Illegal argument on static metamodel field injection : org.hibernate.envers.DefaultRevisionEntity_#class_; expected type :  org.hibernate.metamodel.model.domain.internal.EntityTypeImpl; encountered type : jakarta.per"]}
  ```
- note: boot WARN lines (excluding Flyway 'already exists'): 6; ERROR lines: 1
- note: 2026-10-16 17:20:40.846 WARN  [main] [] org.hibernate.orm.deprecation - HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
- note: 2026-10-16 17:20:56.153 WARN  [main] [] c.p.a.c.s.MpesaCallbackIpAllowlistFilter - MPESA_CALLBACK_ALLOWED_IPS is not configured — the M-Pesa callback endpoint accepts requests from any IP (matches pre-existing behaviour). Set it to Safaricom Daraja's published
- note: 2026-10-16 17:21:17.797 WARN  [main] [] o.d.c.k.builder.impl.KieBuilderImpl - File 'rules/aml-rules.drl' is in folder 'rules' but declares package 'com.posgateway.aml.rules'. It is advised to have a correspondance between package and folder names.
- note: 2026-10-16 17:21:43.944 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.AccountTransfer is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please consider
- note: 2026-10-16 17:21:43.950 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.MerchantRelationship is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please con
- note: 2026-10-16 17:21:47.143 WARN  [main] [] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to di
- note: 2026-10-16 17:20:51.539 ERROR [main] [] o.h.m.internal.MetadataContext - HHH015007: Illegal argument on static metamodel field injection : org.hibernate.envers.DefaultRevisionEntity_#class_; expected type :  org.hibernate.metamodel.model.domain.internal.Entity

### B1 — Login / me / logout / session (FAIL)
- **FAIL** Authorization: Bearer <token> authenticates (docs contract)
  ```json
  {"request": "GET /auth/me", "requestBody": null, "status": 403, "ms": 113.6, "response": "{\"timestamp\":\"2026-10-16T14:45:41.525+00:00\",\"status\":403,\"error\":\"Forbidden\",\"path\":\"/api/v1/auth/me\"}"}
  ```

### B2 — Hard-coded / demo credentials are not usable in production profile (FAIL)
- **FAIL** 'admin' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"admin\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 443.3, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Platform\",\"lastName\":\"Admin\",\"createdAt\":\"2026-10-16T17:22:10.161477\",\"role\":{\"permissions\":[\"ADD_CASE_EVIDENCE\",\"MERCHANT_VIEW\",\"MANAGE_USERS\",\"MANAGE_RULES\",\"OVERRIDE_SCREENING_MATCH\",\"VIEW_SCREENING_RESULTS\",\"MANAGE_SKILLS\",\"PSP_SETTINGS_EDIT\",\"APPROVE_SAR\",\"ADD_CASE_NOTES\",\"ESCALATE_CASES\",\"MODIFY_RISK_SCORES\",\"MANAGE_ROLES\",\"CONFIGURE_SYSTEM\",\"REOPEN_CASES\",\"VIEW_CASES\",\"MERCHANT_EDIT\",\"PSP_UI_EDIT\",\"VIEW_AUDIT_LOGS\",\"MANAGE_PSP_THEME\",\"ASSIGN_CASES\",\"PSP_SETTINGS_VIEW\",\"AMEND_SAR\",\"MANAGE_WATCHLISTS\",\"CREATE_CASES\",\"REJECT_S"}
  ```
- **FAIL** 'super.admin' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"super.admin\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 602.0, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Hokeka\",\"lastName\":\"Super Admin\",\"createdAt\":\"2026-10-16T17:08:26.369351\",\"role\":{\"permissions\":[\"ADD_CASE_EVIDENCE\",\"MERCHANT_VIEW\",\"MANAGE_USERS\",\"OVERRIDE_SCREENING_MATCH\",\"MANAGE_RULES\",\"VIEW_SCREENING_RESULTS\",\"APPROVE_SAR\",\"PSP_SETTINGS_EDIT\",\"ESCALATE_CASES\",\"ADD_CASE_NOTES\",\"MODIFY_RISK_SCORES\",\"MANAGE_ROLES\",\"REOPEN_CASES\",\"CONFIGURE_SYSTEM\",\"VIEW_CASES\",\"MERCHANT_EDIT\",\"VIEW_AUDIT_LOGS\",\"PSP_UI_EDIT\",\"ASSIGN_CASES\",\"MANAGE_PSP_THEME\",\"AMEND_SAR\",\"PSP_SETTINGS_VIEW\",\"MANAGE_WATCHLISTS\",\"CREATE_CASES\",\"REJECT_SAR\",\"VIEW_SA"}
  ```
- **FAIL** 'platform.admin' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"platform.admin\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 442.7, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Hokeka\",\"lastName\":\"Platform Admin\",\"createdAt\":\"2026-10-16T17:08:26.369351\",\"role\":{\"permissions\":[\"MERCHANT_VIEW\",\"VIEW_CASES\",\"MANAGE_USERS\",\"VIEW_AUDIT_LOGS\",\"VIEW_SCREENING_RESULTS\",\"PSP_UI_EDIT\",\"PSP_SETTINGS_VIEW\",\"VIEW_SAR\",\"VIEW_TRANSACTION_DETAILS\",\"MANAGE_ROLES\",\"REPORT_VIEW\",\"CONFIGURE_SYSTEM\",\"MANAGE_PSP\"],\"name\":\"PLATFORM_ADMIN\",\"id\":2},\"pspId\":1,\"id\":2,\"psp\":{\"code\":\"HOKEKA_PLATFORM\",\"name\":\"Hokeka\",\"theme\":{\"brandingTheme\":\"default\"},\"id\":1},\"email\":\"platform.admin@hokeka.com\",\"enabled\":true,\"username\":\"platform.ad"}
  ```
- **FAIL** 'velocity.admin' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"velocity.admin\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 400.9, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Velocity\",\"lastName\":\"Admin\",\"createdAt\":\"2026-10-16T17:08:26.369351\",\"role\":{\"permissions\":[\"ADD_CASE_EVIDENCE\",\"MERCHANT_VIEW\",\"MANAGE_USERS\",\"MANAGE_RULES\",\"VIEW_SCREENING_RESULTS\",\"APPROVE_SAR\",\"PSP_SETTINGS_EDIT\",\"ESCALATE_CASES\",\"ADD_CASE_NOTES\",\"REOPEN_CASES\",\"VIEW_CASES\",\"MERCHANT_EDIT\",\"PSP_UI_EDIT\",\"ASSIGN_CASES\",\"MANAGE_PSP_THEME\",\"AMEND_SAR\",\"PSP_SETTINGS_VIEW\",\"MANAGE_WATCHLISTS\",\"CREATE_CASES\",\"VIEW_SAR\",\"VIEW_TRANSACTION_DETAILS\",\"CREATE_SAR\",\"WHITELIST_ENTITY\",\"REPORT_VIEW\",\"EXPORT_DATA\",\"VIEW_PII\",\"CLOSE_CASES\",\"F"}
  ```
- **FAIL** 'compliance' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"compliance\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 377.8, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Compliance\",\"lastName\":\"Officer\",\"createdAt\":\"2026-10-16T17:22:10.476693\",\"role\":{\"permissions\":[\"ADD_CASE_EVIDENCE\",\"ESCALATE_CASES\",\"VIEW_CASES\",\"VIEW_SCREENING_RESULTS\",\"ASSIGN_CASES\",\"FILE_SAR\",\"MANAGE_WATCHLISTS\"],\"name\":\"COMPLIANCE_OFFICER\",\"id\":29},\"pspId\":0,\"id\":10,\"email\":\"compliance@hokeka.com\",\"enabled\":true,\"username\":\"compliance\"},\"token\":\"823CAB139DD4627AA7E4B3A6BDBB0C94\"}"}
  ```
- **FAIL** 'analyst' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"analyst\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 388.5, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"Risk\",\"lastName\":\"Analyst\",\"createdAt\":\"2026-10-16T17:22:11.296903\",\"role\":{\"permissions\":[\"VIEW_TRANSACTION_DETAILS\",\"VIEW_CASES\",\"VIEW_SCREENING_RESULTS\"],\"name\":\"ANALYST\",\"id\":31},\"pspId\":0,\"id\":12,\"email\":\"analyst@hokeka.com\",\"enabled\":true,\"username\":\"analyst\"},\"token\":\"0E94C679E427F7432976C94B05CF3A79\"}"}
  ```
- **FAIL** 'viewer' / 'Hokeka2026!' must not log in on the production profile
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"viewer\", \"password\": \"Hokeka2026!\"}", "status": 200, "ms": 591.5, "response": "{\"redirectUrl\":\"/dashboard\",\"success\":true,\"user\":{\"firstName\":\"View\",\"lastName\":\"Only\",\"createdAt\":\"2026-10-16T17:22:11.659177\",\"role\":{\"permissions\":[\"VIEW_SAR\",\"VIEW_CASES\",\"VIEW_TRANSACTION_DETAILS\"],\"name\":\"VIEWER\",\"id\":32},\"pspId\":0,\"id\":13,\"email\":\"viewer@hokeka.com\",\"enabled\":true,\"username\":\"viewer\"},\"token\":\"D58F77504FA4EC81CE0740BA71EE69B3\"}"}
  ```
- **FAIL** admin logs in with SEED_ADMIN_PASSWORD (operator-controlled secret)
  ```json
  {"request": "POST /auth/login", "requestBody": "{\"username\": \"admin\", \"password\": \"SimAdmin!6bcd6516585c362a\"}", "status": 401, "ms": 292.1, "response": "{\"message\":\"Invalid username or password\"}"}
  ```

### D2 — PSP API keys lifecycle and use (FAIL)
- **FAIL** issued API key authenticates PSP ingest (X-Api-Key or Bearer)
  ```json
  {"request": "POST /transactions/ingest", "requestBody": "{\"merchantId\": \"1\", \"amountCents\": 1500, \"currency\": \"USD\", \"pan\": \"4873955523526895\", \"terminalId\": \"T-card-01\", \"ipAddress\": \"198.51.100.157\", \"countryCode\": \"KE\", \"channelType\": \"ECOM\", \"direction\": \"INBOUND\", \"clientReference\": \"r1016174257-1f357b1ca543\", \"acquirerResponse\": \"00\"}", "status": 403, "ms": 197.9, "response": "{\"timestamp\":\"2026-10-16T14:48:17.544+00:00\",\"status\":403,\"error\":\"Forbidden\",\"path\":\"/api/v1/transactions/ingest\"}"}
  ```

### D4 — Billing, usage, subscriptions, invoices (FAIL)
- **FAIL** PSP admin GET /billing/rates
  ```json
  {"request": "GET /billing/rates", "requestBody": null, "status": 400, "ms": 48.4, "response": "{\"status\":\"BAD_REQUEST\",\"message\":\"Required parameter 'pspId' is missing\",\"errorCode\":\"ERR_MISSING_PARAM_001\",\"httpStatus\":400,\"timestamp\":\"2026-10-16T17:48:22.3030742\",\"path\":\"/api/v1/billing/rates\",\"method\":\"GET\",\"details\":{\"parameterType\":\"Long\",\"parameterName\":\"pspId\"},\"traceId\":\"44032ada-0703-4ebc-885e-9005e81d0c3d\"}"}
  ```

## Notes per scenario

- **S0**: admin did not accept SEED_ADMIN_PASSWORD; falling back to the hard-coded default
- **A1**: health components: db=UP, diskSpace=UP, livenessState=UP, mail=DOWN, neo4j=UP, onPremLease=UP, ping=UP, readinessState=UP, redis=UP, sanctionsData=DOWN
- **A2**: boot WARN lines (excluding Flyway 'already exists'): 6; ERROR lines: 1 · 2026-10-16 17:20:40.846 WARN  [main] [] org.hibernate.orm.deprecation - HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default) · 2026-10-16 17:20:56.153 WARN  [main] [] c.p.a.c.s.MpesaCallbackIpAllowlistFilter - MPESA_CALLBACK_ALLOWED_IPS is not configured — the M-Pesa callback endpoint accepts requests from any IP (matches pre-existing behaviour). Set it to Safaricom Daraja's published · 2026-10-16 17:21:17.797 WARN  [main] [] o.d.c.k.builder.impl.KieBuilderImpl - File 'rules/aml-rules.drl' is in folder 'rules' but declares package 'com.posgateway.aml.rules'. It is advised to have a correspondance between package and folder names. · 2026-10-16 17:21:43.944 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.AccountTransfer is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please consider · 2026-10-16 17:21:43.950 WARN  [main] [] o.s.d.n.c.m.Neo4jPersistentEntity - The entity com.posgateway.aml.entity.graph.MerchantRelationship is using a Long value for storing internally generated Neo4j ids. The Neo4j internal Long Ids are deprecated, please con · 2026-10-16 17:21:47.143 WARN  [main] [] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore
- **B3**: reset tokens stored for user: 1
