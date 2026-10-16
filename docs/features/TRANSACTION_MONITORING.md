# Transaction Monitoring

## Overview

Your Edge Node evaluates every transaction. It runs on your premises, decides, and records the
transaction in its local ledger. The cloud never evaluates a transaction and, under the residency
model, never stores transaction data. It receives a **decision record** per evaluation and runs the
alert, case and SAR workflow from it.

The normative rules are in [`EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md).
The request and response of `/edge/evaluate` are in
[`edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).

## Status at a glance

| Part | Status |
|---|---|
| `POST /edge/evaluate` on the Edge Node | Current |
| Edge Ledger (PostgreSQL 16) records every evaluation and its decision record | Current |
| Durable outbox ships decision records v2 to `POST /api/v1/edge/assessments` | Current |
| Cloud raises alerts, cases and CTR deadlines from decision records | Current |
| Hourly decision rollup (`decision_stats_hourly`) | Current |
| Edge Tunnel server in the cloud (`wss://edge.hokeka.com/api/v1/edge/tunnel`) | Current |
| Edge Tunnel client on the Edge | Planned (R5) |
| Cloud data proxy: transaction GETs served live from the Edge | Planned (R6) |
| Console transaction pages pull live from the Edge | Planned (R11) |
| Edge retention purge | Planned (R2). Until then the Edge keeps every evaluation. |
| Back-fill of legacy cloud transactions to your Edge | Planned (R15) |
| Removal of cloud ingest and the cloud transaction store | Planned (R12) |

## Processing flow (current)

```text
Your API node
  -> POST /edge/evaluate (TLS, on your Edge Node)
       -> refuse a PAN-like txn_id: 422 TXN_REF_LOOKS_LIKE_PAN, nothing evaluated or stored
       -> derive velocity features from Aerospike (26 h hourly buckets)
       -> evaluate the signed rule bundle (Rust core, Java interpreter as standby)
       -> write the evaluation and its decision record to the Edge Ledger in one transaction
            (ledger write fails and fail-closed is on -> HOLD / LEDGER_UNAVAILABLE)
  <- 200 { action, score, triggeredRuleIds, reasons, edgeAssessmentId, ... }

Edge outbox shipper (background)
  -> POST /api/v1/edge/assessments (mTLS, sealed batch of decision records v2)

Control Plane
  -> validate each record against the closed v2 schema; reject unknown fields
  -> store it in assessments (ids, decision, score, rule ids; no PAN, amount or features)
  -> ALERT / HOLD / BLOCK, sanctions hit or CTR flag -> alert (one per record)
  -> sanctions hit -> SANCTIONS_HIT case; rule decision -> RULE_VIOLATION case when
     auto-create-cases is on
  -> ctrRequired -> CTR_FILING compliance deadline
  -> RISK_ALERT webhook and alerts.generated event with ids and the decision only
  -> add the record to decision_stats_hourly
```

The cloud never calls back to the Edge to enrich a record at ingest time.

## What leaves the Edge

A decision record carries: `edgeAssessmentId`, `txnRef`, `edgeNodeId`, `siteId`, `evaluatedAt`
(to the second), `decision`, `score`, triggered rule ids and rule version ids, bundle version and
hash, evaluator, fail-closed code, latency, `merchantId`, `ctrRequired`, `sanctionsHit`, watchlist
entry ids and typology codes.

It never carries the PAN or its hash, the amount, currency or country, customer, account, device or
IP identifiers, names, or free-text reasons. The cloud renders reason text from rule ids.

Hourly metrics add counts and amount sums per merchant and per currency. They are never per card or
per customer.

## Seeing transactions in the Console

- **Today.** Alerts and cases raised from decision records show in the Console with the
  `edgeAssessmentId` and `txnRef`. The transaction detail (amount, features, reasons) stays on your
  Edge. The Console transaction list still reads the legacy cloud store, so it shows only
  transactions sent through the deprecated cloud ingest.
- **Planned (R6, R11).** The Console pulls transaction detail live from your Edge over the Edge
  Tunnel. Your Edge opens that connection outbound on port 443; you open no inbound port. Each pull
  names a user and a purpose and is logged on both sides. If your Edge is offline, the Console
  shows the cloud fields and an offline notice.

## Retention

You set retention in the Console under **Settings → Data Retention**: minimum and default 5 years
(FATF R.11), maximum 25. A change needs a second approver (maker-checker). An increase takes effect
on approval. A reduction takes effect 30 days after approval. The setting reaches your Edge in the
sealed Edge config document (`GET /api/v1/edge/config`).

The cloud purges decision records past retention, except those linked to open alerts, open cases,
unfiled SARs or active holds. The Edge purge is planned (R2); until it ships, the Edge keeps every
evaluation.

## Legacy cloud ingest (deprecated)

`POST /api/v1/transactions/ingest` and `/ingest/batch` still work for existing integrations. They
store the transaction in the cloud and run the old cloud fraud pipeline (feature extraction,
scoring, Drools and SpEL rules, decision engine). Do not build new integrations on them.

- Work package R15 (planned) copies legacy cloud transactions to your Edge Ledger and verifies the
  copy.
- Work package R12 (planned) then deletes the ingest endpoints, the cloud pipeline and the cloud
  transaction tables. R12 waits for parity: every cloud-only detection must run on the Edge first.

## Decision values

`ALLOW`, `ALERT`, `HOLD` and `BLOCK`. `HOLD` is also the Edge's fail-closed answer (no bundle, node
not authorised, feature store or ledger down). Treat `HOLD` as "do not approve on your own".
