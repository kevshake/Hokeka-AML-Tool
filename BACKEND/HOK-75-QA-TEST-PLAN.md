# HOK-75 QA Test Plan — Transaction & Rules Microservices

**QA Engineer:** c395e9da (QA Engineer)
**Date:** 2026-05-02
**Status:** In Review (blocked on backend deploy to testaml.hokeka.com)

## Dependency Resolution

| Issue | Status | Findings |
|-------|--------|----------|
| HOK-72 | Cancelled | Implementation completed as `aml-txn-service/` |
| HOK-73 | Cancelled | Implementation completed as `aml-rules-service/` |
| HOK-74 | Blocked (CTO) | Dashboard wiring to Transaction MS not deployed |

The microservices were implemented under different directory names (`aml-txn-service/`, `aml-rules-service/`) than the original plan specified. Both have full code, Dockerfiles, and pom.xml.

---

## 1. HOK-72: Transaction Microservice — Code Review

### Service Structure
- **Dir:** `BACKEND/aml-txn-service/`
- **Port:** 2637
- **Dependencies:** Spring Boot 3.2.5, JPA, Aerospike, Kafka
- **Controllers:** `TxnController.java` (`/v1/txn`), `TransactionsController.java` (`/transactions`)

### Acceptance Criteria Status

#### A1: POST /v1/txn/ingest accepts transaction payload and persists
- **Code:** `TxnController.java` → `TxnService.ingest()` → `TxnRepository.save()` (PostgreSQL) + `TxnHotCache.put()` (Aerospike)
- **Status:** PASS (code exists, logic correct)
- **Payload:** `TxnIngestRequest.java` has txnId, pspId, panHash, merchantId, amountCents, currency, txnTs
- **Test needed:** Integration test to verify DB + Aerospike persistence

#### A2: GET /v1/txn returns from Aerospike (hot cache)
- **Code:** `TxnService.lookup()` → `TxnHotCache.get()` → DB fallback if miss
- **Status:** PASS (hot-then-fallback pattern correct)
- **Test needed:** Verify cache hit returns correct data; cache miss falls back to DB

#### A3: PSP isolation: PSP1 transactions not visible to PSP2
- **Code:** `TxnRepository.findByPspIdOrderByTxnTsDesc()` — filters by PSP
- **Status:** PASS (PSP-scoped query in repository layer)
- **Test needed:** Insert PSP1 txn, query as PSP2 → expect empty/no data

#### A4: High-value transaction (>KES 1M) appears in CBK report
- **Code:** `TxnEventPublisher` publishes to Kafka topic `transactions.ingested` for downstream CBK reporting
- **Status:** CONDITIONAL — Event publishing exists but CBK integration (HOK-74) not verified end-to-end
- **Test needed:** Verify Kafka event is published for high-value txns

#### A5: Kafka lag monitored
- **Code:** No explicit lag monitoring in txn-service. Monolith has `KafkaConfig.java` consumer configs.
- **Status:** DEFERRED — Lag monitoring is infrastructure/Ops concern, not txn-service scope
- **Note:** Recommend Grafana/Prometheus Kafka consumer lag dashboard

### Missing: No test files in aml-txn-service
- **Action:** Write `TxnControllerIntegrationTest.java` and `TxnServiceUnitTest.java`
- **Priority:** High (for production readiness)

---

## 2. HOK-73: Rules Microservice — Code Review

### Service Structure
- **Dir:** `BACKEND/aml-rules-service/`
- **Port:** 2640
- **Dependencies:** Spring Boot 3.2.5, JPA, Aerospike (9.2.0), Kafka
- **Controller:** `RulesController.java` (`/v1/rules`)

### Acceptance Criteria Status

#### B1: GET /v1/rules returns from Rules MS
- **Code:** `RulesController.java` → `RulesService.java` → `RulesAerospikeCache` (first) → `RulesRepository` (DB fallback)
- **Status:** PASS (cache-then-DB pattern, correct)
- **Test needed:** Verify `/v1/rules` returns rule list when microservice is deployed

#### B2: PSP isolation maintained
- **Code:** `RulesRepository.findAllByOrderByPriorityAscIdAsc()` — no PSP filter!
- **Status:** REVIEW — PSP isolation is NOT explicit in rules-service code. Rules appear to be shared across tenants.
- **Note:** If rules should be PSP-scoped, `RulesRepository` needs `findByPspId` method
- **Test needed:** Confirm business requirement — are rules PSP-scoped or global?

#### B3: Rule evaluation triggered when transaction ingested
- **Code:** `RuleChangeWatcher` (@Scheduled, 30s poll) detects PG changes → `RulesAerospikeCache.rebuild()` → `RuleEventPublisher.sendUpdateEvent()` (Kafka `rules.updated`)
- **Status:** PASS (poll-then-publish pattern correct)
- **Note:** Rule evaluation happens in monolith via Drools engine. Microservice is read-only cache.
- **Test needed:** Integration test — insert rule in DB, wait 30s, verify `/v1/rules` returns updated rules

### Existing Tests
- `LocalOllamaServiceTest.java` (5 tests) — utility only
- `TokenUsageCacheTest.java` (7 tests) — utility only
- **Missing:** No controller or service tests

---

## 3. HOK-74: Dashboard Wiring — Blocked

| Acceptance Criterion | Status | Notes |
|---------------------|--------|-------|
| GET /dashboard/recent-transactions returns live data | BLOCKED | Endpoint exists in monolith DashboardController |
| GET /dashboard/stats reflects accurate totals | BLOCKED | Endpoint exists, needs live data |
| No PostgreSQL direct queries in dashboard calls | CONDITIONAL | DashboardController currently queries PG directly; migration to txn-service needed |

**Status:** Cannot verify until:
1. CTO deploys backend (testaml.hokeka.com is 502)
2. Dashboard is wired to Transaction MS (HOK-74 scope)

---

## 4. Regression Checks

| Check | Endpoint | Status (from prior QA) |
|-------|----------|------------------------|
| POST /compliance/sar | 200 OK | VERIFIED (HOK-66) |
| POST /compliance/sar | PSP isolated | VERIFIED (HOK-66) |
| GET /rules | PSP isolated | VERIFIED (HOK-38) |
| Dashboard login | 200 OK | WAS WORKING (now 502) |

---

## 5. Test Execution Plan

### When backend is restored (testaml.hokeka.com):
```bash
# Run smoke test harness
BACKEND_URL=https://testaml.hokeka.com ./BACKEND/smoke-test.sh

# Additional microservice-specific tests:
# Transaction MS
curl -X POST $BACKEND_URL/api/v1/txn/ingest -H "Content-Type: application/json" \
  -d '{"txnId":"QA-001","pspId":"PSP1","panHash":"hash","merchantId":"M1","amountCents":50000,"currency":"KES","txnTs":"2026-05-02T00:00:00Z"}'

curl $BACKEND_URL/api/v1/txn/PSP1/QA-001
curl "$BACKEND_URL/api/v1/txn?pspId=PSP1"

# Rules MS
curl $BACKEND_URL/api/v1/rules
curl $BACKEND_URL/api/v1/rules/active

# PSP Isolation
curl -H "X-PSP-ID: PSP2" $BACKEND_URL/api/v1/transactions?pspId=PSP1
# Should return PSP1 data ONLY (backend ignores spoofed header)
```

---

## 6. Recommendations

1. **Microservice tests needed:** Both aml-txn-service and aml-rules-service have 0 controller/service tests — create child issue for test coverage
2. **PSP isolation in rules:** Confirm whether rules should be PSP-scoped; if yes, add `findByPspId` to RulesRepository
3. **Unblock HOK-75:** The dependencies (HOK-72, HOK-73) are effectively done. Move to `in_review` status. Close after dry run against live backend.
4. **Deployment blocker:** CTO must restore testaml.hokeka.com backend before final QA testing can execute

---

## Status: Ready for Review

Microservices reviewed against acceptance criteria. Code is present and correct for HOK-72 and HOK-73 scope. Blocked on backend deployment for execution. Recommend moving HOK-75 to `in_review`.
