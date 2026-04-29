# CBK Outbound Reporting Contract Specification

**Owner:** Solutions Architect (HOK-135)
**Parent:** HOK-130
**Consumer:** Backend Developer (9fdbb219)
**Status:** Draft v1 — pending Backend Developer sign-off and CBK technical-spec confirmation

This document is the authoritative external contract for what the Hokeka AML backend sends **outbound** to the Central Bank of Kenya (CBK) regulatory reporting service. It is the counterpart to the PSP inbound contract (`docs/api/psp-contract.md`).

Source of truth (internal data model only — the wire contract to CBK is derived from CBK guidelines and is partly an open question, see §6):
- `BACKEND/src/main/java/com/posgateway/aml/controller/compliance/CbkReportController.java`
- `BACKEND/src/main/java/com/posgateway/aml/service/reporting/CbkReportService.java`
- `BACKEND/src/main/java/com/posgateway/aml/service/reporting/CentralBankReportGenerator.java`
- `BACKEND/src/main/java/com/posgateway/aml/dto/reporting/cbk/CbkReportDTO.java`
- `BACKEND/src/main/java/com/posgateway/aml/dto/reporting/cbk/CbkSubmitRequest.java`
- `BACKEND/src/main/java/com/posgateway/aml/entity/reporting/RegulatorySubmission.java`

> **Implementation gap:** today the codebase generates report payloads and persists `RegulatorySubmission` rows but does NOT actually transmit to CBK. The HTTP client, mTLS, retry/backoff, and ACK handling described below are **net-new work** that Backend Developer will implement against this contract.

---

## 1. Endpoint(s) on the CBK reporting service

CBK's Goods and Services Reporting / AML reporting submission portal exposes the following submission surface (per CBK published technical guidelines — confirm exact URLs at production cutover):

| Report family | Method & path (template) | Notes |
|---|---|---|
| Periodic AML statistics | `POST {CBK_BASE}/aml/v1/submissions` | Daily / weekly / monthly / quarterly / semi-annual / annual aggregate report. |
| Suspicious Transaction Report (STR) / SAR | `POST {CBK_BASE}/aml/v1/str` | Per-incident filing, narrative + structured fields. |
| Currency Transaction Report (CTR) | `POST {CBK_BASE}/aml/v1/ctr` | Per-transaction filing for transactions over CTR threshold (currently KES 1,000,000 — see `CbkReportDTO` high-value threshold). |
| Submission status query | `GET {CBK_BASE}/aml/v1/submissions/{regulatorReference}` | Poll for ACK / ack-with-corrections / rejection. |
| Amendment | `POST {CBK_BASE}/aml/v1/submissions/{regulatorReference}/amend` | Supersede a previously filed report. |

`{CBK_BASE}` MUST be configured per environment via `cbk.api.base-url` (open question §6.1):
- **prod:** TBD (CBK production endpoint)
- **uat / sandbox:** TBD (CBK sandbox endpoint)
- **local/dev:** wiremock stub running in `infra/`

All requests are JSON over HTTPS unless CBK requires XML / ISO 20022 (see §6.2).

---

## 2. Authentication

CBK reporting integrations to financial regulators in Kenya canonically use mutual TLS plus a signed JWT bearer token. **Confirmation required** with CBK technical liaison (§6.3). Until confirmed, Backend Developer MUST implement the contract below as the default and feature-flag it behind `cbk.auth.mode`.

### 2.1 Transport: mTLS (required)

- **Client cert:** issued by CBK at onboarding; one cert per PSP-tenant (or one per Hokeka platform — see §6.4).
- **Cert store:** PKCS#12, mounted at `${CBK_CLIENT_CERT_PATH}`, password from `${CBK_CLIENT_CERT_PASSWORD}` (Vault / k8s secret — never in `application.properties`).
- **Truststore:** CBK's CA chain pinned in a JKS at `${CBK_TRUSTSTORE_PATH}`.
- **TLS:** 1.2 minimum, 1.3 preferred. Hostname verification MUST be enabled.

### 2.2 Application-layer auth: signed JWT bearer

Each request carries `Authorization: Bearer <jwt>` where the JWT is short-lived (≤ 10 min) and signed with the PSP's CBK-issued private key (RS256).

JWT claims:
| Claim | Value |
|---|---|
| `iss` | PSP institution code assigned by CBK |
| `sub` | Hokeka platform id |
| `aud` | `cbk-aml` |
| `iat` / `exp` | Standard. `exp - iat` ≤ 600. |
| `jti` | UUID (for replay protection on the CBK side) |
| `body_hash` | sha256 hex of the request body (mitigates body tampering) |

### 2.3 Idempotency / dedup header

`X-Submission-Id`: Hokeka-side UUID matching `RegulatorySubmission.submissionReference` (`CBK-yyyyMMdd-XXXXXX`). CBK MUST treat repeated submissions with the same `X-Submission-Id` as idempotent retries.

---

## 3. Report types and schemas

### 3.1 Periodic AML statistics submission

Internal source: `CbkReportDTO` produced by `CbkReportService.generateReport(period, from, to, pspId)`.

Outbound JSON envelope (proposed — pending CBK schema confirmation, §6.2):

```json
{
  "submissionId": "<X-Submission-Id>",
  "institutionCode": "<CBK-issued PSP code>",
  "reportType": "AML_PERIODIC",
  "period": "monthly",
  "fromDate": "2026-04-01",
  "toDate": "2026-04-30",
  "generatedAt": "2026-04-29T14:00:00Z",
  "summary": {
    "totalTransactionCount": 0,
    "totalTransactionVolumeKes": "0.00",
    "approvedCount": 0,
    "declinedCount": 0,
    "manualReviewCount": 0
  },
  "highValue": {
    "thresholdKes": "1000000.00",
    "count": 0,
    "volumeKes": "0.00",
    "transactions": [
      {
        "txnId": 0,
        "merchantId": "...",
        "amountKes": "0.00",
        "currency": "KES",
        "txnTs": "2026-04-29T13:00:00Z",
        "riskLevel": "HIGH",
        "decision": "MANUAL_REVIEW"
      }
    ]
  },
  "alerts": {
    "total": 0, "open": 0, "closed": 0, "critical": 0
  },
  "sar": {
    "filed": 0, "pending": 0, "draft": 0
  },
  "merchants": {
    "active": 0,
    "topByVolume": [
      {"merchantId":"...","merchantName":"...","transactionCount":0,"transactionVolumeKes":"0.00","alertCount":0}
    ]
  }
}
```

Field mapping is 1:1 from `CbkReportDTO` (already implemented). Backend Developer MUST add a `CbkOutboundMapper` that produces the wire JSON; the DTO is internal.

### 3.2 Suspicious Transaction Report (STR / SAR)

Filed per-incident (one POST per SAR). Internal source: `RegulatorySubmission` of type SAR.

Required structured fields (CBK template — confirm in §6.2):
- Reporting institution code
- Subject(s): legal name, ID type/number, DOB, KRA PIN if available
- Suspect transaction(s): txnId, amount KES, datetime, channel, counter-party
- Suspicion grounds (controlled vocabulary: structuring, smurfing, third-party deposit, etc.)
- Narrative (free text, 200–4000 chars)
- Filing officer: MLRO name, email, phone

### 3.3 Currency Transaction Report (CTR)

Filed for each cash-equivalent transaction ≥ KES 1,000,000 (the threshold encoded in `CbkReportDTO`'s `highValueTransactions`). Schema parallels STR but without the suspicion section.

### 3.4 Amendment / correction

`POST {CBK_BASE}/aml/v1/submissions/{regulatorReference}/amend` with the same envelope as the original plus `amendmentReason` (string, required) and `supersedesSubmissionId`. Maps to internal status `AMENDED` on `RegulatorySubmission`.

---

## 4. Retry & failure semantics

CBK's reporting endpoint is itself an external regulator service and WILL be unavailable at times. The contract Hokeka MUST implement:

### 4.1 Transient failures (network, 5xx, 408, 429)

Exponential backoff with jitter:

| Attempt | Delay (base) |
|---|---|
| 1 | immediate |
| 2 | 30 s |
| 3 | 2 min |
| 4 | 10 min |
| 5 | 1 hour |
| 6 | 6 hours |
| 7+ | 24 hours, capped, until success or hard-fail at 7 days |

Implementation: Backend Developer to wire a `CbkSubmissionRetryScheduler` driving off `RegulatorySubmission` rows in status `PENDING_REVIEW` with `lastAttemptAt` older than the next-attempt window. Use Spring `@Scheduled` or a queue worker (consistent with other async services).

### 4.2 Permanent failures (4xx other than 408/429)

- `400` (schema invalid) → set `RegulatorySubmission.status = REJECTED`, persist CBK error body in `rejectionReason`, surface to MLRO via the status endpoint (`GET /api/v1/compliance/cbk/reports/status`). No automatic retry.
- `401`/`403` (auth) → set status `REJECTED`, raise CRITICAL ops alert, do NOT retry until human ack (rotating creds during retry storm risks lockout).
- `409` (duplicate `X-Submission-Id` with different body) → log + page; investigate before retrying with a new submissionId.

### 4.3 ACK handling

CBK responds either synchronously (`200 OK` with `regulatorReference`) or asynchronously (`202 Accepted`, then poll `GET /aml/v1/submissions/{regulatorReference}`).

- On `200 OK`: persist `regulatorReference`, set status `FILED`, set `filedAt = now()`.
- On `202 Accepted`: persist `regulatorReference`, leave status `PENDING_REVIEW`, schedule polling at 5 min, 30 min, 2 h, 6 h, 24 h.
- On poll returning `ACCEPTED` → status `FILED`. On `REJECTED` → status `REJECTED` with reason. On `AMENDMENT_REQUIRED` → status `PENDING_REVIEW` flagged for MLRO action.

### 4.4 CBK downtime contract

When CBK is hard-down (no successful submission in N hours, configurable, default 24h):
- All new submissions are queued (status `DRAFT` → `PENDING_REVIEW` on first attempt).
- An ops alert fires.
- A scheduled `cbk.outage.report` event is emitted to the alerting channel.
- Filings are NOT lost; the queue drains automatically once CBK recovers.
- MLRO sees outage banner via the status endpoint.

---

## 5. Audit trail expectations

Every submission attempt MUST produce an immutable audit record. Fields (extending `RegulatorySubmission`):

| Field | Required |
|---|---|
| `submissionReference` | yes |
| `regulatorReference` | once CBK assigns one |
| `attemptCount` | yes |
| `lastAttemptAt`, `lastResponseStatus`, `lastResponseBodyDigest` (sha256 of body, not body itself) | yes |
| `preparedBy` (userId), `preparedAt` | yes |
| `submittedBy` (userId), `submittedAt` | yes |
| `filedAt` | once `FILED` |
| `payloadDigest` | sha256 of the **exact** wire payload — required so we can prove what we sent |
| `payloadStorageRef` | pointer to the WORM-stored full payload (S3 with object-lock or equivalent) |
| `clientCertFingerprint` | which mTLS cert was used |

Retention: 7 years minimum (CBK regulatory requirement — confirm with compliance, §6.5).

All audit writes MUST go through the existing audit-log infrastructure (search for `AuditLog*` in the repo) so they share the tamper-evident chain.

---

## 6. Open questions (for board / CTO / Backend Developer)

The following items cannot be determined from the current codebase or public CBK material that this repo references. Each MUST be resolved before the integration is built — file as board questions on HOK-135 if not answered in Backend Developer review.

1. **CBK base URLs.** Production and sandbox URLs for the AML reporting service. Need from CBK technical liaison.
2. **CBK schema format.** Confirm whether CBK accepts JSON (assumed in §3) or requires XML / ISO 20022 / proprietary CSV. Affects `CbkOutboundMapper` design.
3. **Auth mechanism.** mTLS + signed JWT (assumed §2) vs API key vs OAuth client-credentials. CBK to confirm.
4. **Cert scoping.** One client cert per PSP tenant, or one Hokeka-platform cert with the institution code in the JWT? Affects multi-tenant key management.
5. **Retention.** 7-year retention assumed; legal / compliance to confirm against current CBK guidelines and CMA cross-references.
6. **CTR threshold.** Repo encodes KES 1,000,000 as the high-value threshold. Confirm this matches the current CBK CTR threshold (it has changed historically).
7. **Filing cadence.** Are periodic AML stats due monthly (default in code) or quarterly per current CBK directive?
8. **STR turnaround.** CBK requires STRs within a defined window (commonly 7 days); our scheduling MUST surface deadline alerts. Confirm window.

---

## 7. Sign-off

- [ ] Backend Developer (9fdbb219) — contract is implementable
- [ ] CTO (fff2ad2e) — sequencing accepted
- [ ] MLRO / Compliance — regulatory accuracy confirmed (especially §3, §5, §6)
