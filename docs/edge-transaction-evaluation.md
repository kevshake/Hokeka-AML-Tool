# Hokeka Edge — Transaction Evaluation & Rule Resolution

**Audience:** the PSP engineer who wires the authorisation flow to the Edge Node, and anyone who
needs to explain *why* a transaction got its decision.

This document describes the behaviour of `POST /edge/evaluate`. It follows the code
(`EdgeController`, `EdgeEngine`, `EdgeRuleInterpreter`, `AerospikeFeatureStore`,
`com.hokeka.edge.ledger`), not intent. Where the code has a sharp edge, this document says so.

The Edge Node is the **only** component that evaluates transactions. The cloud does not
re-evaluate, re-score or override an Edge decision. The data rules that follow from this are in
[`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md).

---

## 1. The request path end to end

```
Your API node
    │  POST /edge/evaluate      TLS 1.3, one virtual thread per request
    ▼
EdgeController
    │  0. CHECK txn_id: PAN-like → 422 TXN_REF_LOOKS_LIKE_PAN; malformed → 422 TXN_REF_INVALID
    │  1. read pan_hash from the body
    │  2. ENRICH: derive velocity features from Aerospike (fail-closed if unavailable)
    │  3. MERGE:  caller-supplied fields overwrite derived ones
    ▼
EdgeEngine
    │  4. GATE:   node authorized?      no → HOLD (no rules run)
    │  5. GATE:   rule bundle present?  no → HOLD (no rules run)
    │  6. EVALUATE via the Rust core, or the Java interpreter as warm standby
    ▼
EdgeController
    │  7. advance this card's velocity counters (after the decision; site-scoped keys)
    │  8. MINT edgeAssessmentId (UUID)
    │  9. RECORD on the Edge Ledger: evaluation row + txn_ref index + decision-record outbox row,
    │     one SQL statement, one local transaction. Failure → HOLD / LEDGER_UNAVAILABLE (§5.3)
    │ 10. count the standing decision into the aggregate metrics
    ▼
    └─► 200 OK  { action, score, triggeredRuleIds, reasons, triggeredRuleVersionIds,
                  edgeAssessmentId, ruleBundleVersion, ruleBundleHash }

Later, in the background:
    OutboxShipper drains edge_outbox → sealed decision record v2 batch →
    POST /api/v1/edge/assessments (Control Plane raises alerts and cases by reference)
```

Ordering details to know:

- **Enrichment happens before evaluation**, so rules see this card's history.
- **Velocity counters advance after evaluation**, so a transaction never inflates its own counters.
  The transaction you just sent is *not* in the `pan_txn_count_1h` its rules saw.
- **The ledger write happens before the answer.** If the PSP gets an `edgeAssessmentId`, that
  evaluation is on the ledger, or it is a fail-closed `HOLD` waiting in the retry buffer (§5.3).

---

## 2. The request

`POST /edge/evaluate`, `Content-Type: application/json`.

The body is a **flat JSON object of features**. There is no fixed schema and unknown fields are not
rejected: the body is a feature bag that rules run against. That has a sharp edge; see §2.3.

### 2.1 Fields the engine itself uses

The node reads these names directly. It passes every other field to the rules unchanged.

| Field | Type | Used for | If absent |
|---|---|---|---|
| `pan_hash` | string | Velocity key; stored in its own ledger column (never in `features`, never sent to the cloud) | **No velocity enrichment.** Velocity rules cannot fire |
| `txn_id` | string or integer | Your transaction reference. Stored as `txn_ref`, unique per Edge Site, sent to the cloud as `txnRef` | Evaluated and stored with no reference. You can then find it only by `edgeAssessmentId` |
| `amount_cents` | integer (minor units) | Velocity amount counters; stored as `amount_cents` | Counters add 0; column is null |
| `merchant_id` | string `^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$` | Stored; sent to the cloud as `merchantId` | Column is null |
| `currency` | ISO 4217, 3 letters | Stored | Column is null |
| `country_code` | ISO 3166, 2 letters | Stored | Column is null |
| `mcc` | 4 digits (an integer is zero-padded) | Stored; rules often target it | Column is null |
| `channel` | `^[A-Z][A-Z0-9_]{0,31}$` | Stored | Column is null |

A value that does not match its format is stored as null in its column. It still reaches the rules
as you sent it.

**`txn_id` rules.** It must be 1 to 128 printable ASCII characters with no spaces. If it looks like a
card number (13 to 19 digits after removing spaces and hyphens, and it passes the Luhn check), the
node answers `422 {"error":"TXN_REF_LOOKS_LIKE_PAN"}`. Any other invalid value gets
`422 {"error":"TXN_REF_INVALID"}`. In both cases the node evaluates nothing and stores nothing. If you
send the same `txn_id` twice, both evaluations are stored and the reference points at the newest one.

**Send the PAN hash, never the PAN.** The node strips card secrets before it stores features
(`pan`, `card_number`, `cvv`, `cvc`, `track1`, `track2`, `pin`, `pin_block`, `expiry` and similar keys,
plus any value the Luhn guard flags). Do not rely on that: a raw card number has no use here.

### 2.2 Features the node derives for you

The node reads these from Aerospike, keyed on `pan_hash`, and merges them in **before** evaluation:

| Derived feature | Type | Meaning |
|---|---|---|
| `pan_txn_count_1h` | integer | Transactions on this card in the current hourly bucket |
| `pan_txn_count_24h` | integer | Transactions on this card across the last 24 hourly buckets |
| `pan_amount_sum_24h` | decimal | Sum of amounts over the same 24 buckets, **in major units** (cents ÷ 100) |

> **Unit mismatch.** You send `amount_cents` in **minor** units. The derived `pan_amount_sum_24h` is
> in **major** units. A rule that compares the two without the 100× factor is wrong by two orders of
> magnitude.

> **Bucket semantics.** `pan_txn_count_1h` is the *current clock-hour bucket*, not a rolling 60
> minutes. At 10:59 it covers 59 minutes. At 11:01 it resets to near zero. `pan_txn_count_24h` sums
> 24 buckets, so it spans 23 to 24 hours. A rule that needs a strict rolling window must say so
> differently.

Velocity is site-scoped. Buckets are keyed `<pan_hash>:<yyyyMMddHH>:<site_id>` (site id =
`EDGE_SITE_ID`, or `EDGE_EDGEID` for a single-node site). All nodes of one Edge Site share one
Aerospike cluster, so they share counters, and a node reads only its own site's keys. Across
several sites of one PSP, counters are per site unless the PSP links the sites' Aerospike clusters
with XDR; without XDR, velocity is per site (`edge-host/deploy/README.md`).

### 2.3 Caller values override derived values

The node merges derived features first, then the request body on top. **Anything you send wins.**

This is on purpose. It lets you supply a better value than the local store can compute, and it
makes testing easy. It also means that if you send your own `pan_txn_count_24h`, the node discards
its own value without telling you. Do not send fields whose names collide with §2.2 unless you mean
to replace them.

### 2.4 Example

```json
{
  "txn_id": "9f2c1e70-...",
  "pan_hash": "sha256:4a7d...",
  "amount_cents": 250000,
  "currency": "KES",
  "country_code": "KE",
  "merchant_id": "mrc_00421",
  "mcc": "5411",
  "channel": "POS",
  "is_cross_border": false
}
```

`is_cross_border` is not an engine field. The node passes it to the rules and stores it in the
ledger `features` bag. That is how you extend the feature set without a code change. Feature keys
must be `snake_case` (`^[a-z][a-z0-9_]{0,63}$`) with scalar values of at most 512 characters to be
stored; rules still see the rest.

---

## 3. The response

```json
{
  "action": "ALERT",
  "score": 45,
  "triggeredRuleIds": [1204, 1311],
  "reasons": [
    "Card velocity above 10 transactions per hour",
    "High-value transaction at a grocery MCC"
  ],
  "triggeredRuleVersionIds": [88012, 88140],
  "edgeAssessmentId": "3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44",
  "ruleBundleVersion": 37,
  "ruleBundleHash": "sha256:9b1f…"
}
```

| Field | Meaning |
|---|---|
| `action` | The decision: `ALLOW`, `ALERT`, `HOLD` or `BLOCK` |
| `score` | **Sum** of the `score` of every triggered rule (§4.3) |
| `triggeredRuleIds` | Ids of every rule whose condition matched, in bundle order |
| `reasons` | Each triggered rule's `description`, or its `name` if it has none |
| `triggeredRuleVersionIds` | Rule version ids that fired, when the bundle carries them |
| `edgeAssessmentId` | UUID minted for this evaluation, including fail-closed `HOLD`. It is the ledger key and the id of the decision record the cloud receives. Store it next to your transaction |
| `ruleBundleVersion` / `ruleBundleHash` | The bundle the decision used (`-1` / `""` before the first bundle) |

### 3.1 What the actions mean for your flow

| Action | Severity | Expected handling |
|---|---|---|
| `ALLOW` | 0 | Proceed with the authorisation |
| `ALERT` | 1 | Proceed. The cloud raises an alert for review |
| `HOLD` | 2 | Do not auto-approve. Route to manual review or step-up |
| `BLOCK` | 3 | Decline |

**Treat `HOLD` as non-approval.** It is also the fail-closed answer (§5). A `HOLD` can mean "this
transaction is suspicious" *or* "this node cannot decide safely right now". Handle both the same
way: do not approve on your own.

### 3.2 What reaches the cloud

You make one call: `POST /edge/evaluate`. The node ships a **decision record v2** for every
evaluation from its outbox to `POST /api/v1/edge/assessments`. The Control Plane stores it and
raises alerts and cases from it (`ALERT`, `HOLD`, `BLOCK`). Those alerts and cases reference the
transaction by `edgeAssessmentId` and `txnRef` only.

The decision record carries only these fields:

| Field | Notes |
|---|---|
| `v` | `2` |
| `edgeAssessmentId` | UUID |
| `txnRef` | your `txn_id`, when you sent a valid one |
| `edgeNodeId`, `siteId` | node and site that evaluated |
| `evaluatedAt` | truncated to the second |
| `decision`, `score` | |
| `triggeredRuleIds`, `ruleVersionIds`, `bundleVersion`, `bundleHash` | |
| `evaluator` | `native`, `java`, or `none` for fail-closed |
| `failClosedCode` | present only on fail-closed `HOLD` |
| `latencyMs` | |
| `merchantId` | when valid |
| `ctrRequired`, `sanctionsHit`, `watchlistEntryIds`, `typologyCodes` | `sanctionsHit` / `watchlistEntryIds` come from local screening of `counterparty_name`, `cardholder_name` and `beneficiary_name` (R10b, contract §6.5). `ctrRequired` / `typologyCodes`: R10a |

It never carries the PAN hash, amount, currency, country, MCC, channel, other features, or the
`reasons` text. The node validates each record against the closed schema
[`contracts/decision-record-v2.schema.json`](contracts/decision-record-v2.schema.json) before it
queues it. The cloud renders reason text from rule ids.

**Cloud ingest is legacy.** `POST /api/v1/transactions/ingest` still exists and still stores the
transaction in the cloud. New integrations must not use it. Work package R12 deletes it (planned).
See the superseded [`EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

---

## 4. How a decision is resolved

### 4.1 Every rule is evaluated — there is no short-circuit

The engine runs the **entire** rule list. It does not stop at the first match. So
`triggeredRuleIds` and `reasons` are complete: they list every rule that fired. That is what makes
the decision explainable later.

### 4.2 The action is the maximum severity, not the last match

Each triggered rule contributes its action. The result is the **highest severity** across all of
them, starting from `ALLOW`:

```
ALLOW(0) < ALERT(1) < HOLD(2) < BLOCK(3)
```

One `BLOCK` among fifty `ALLOW`s gives `BLOCK`. Rule order does not change the action; only
severity does. A later rule cannot downgrade an earlier one. There is no "allow-list override". A
rule cannot rescue a transaction another rule blocked.

The ledger and the decision record use the four contract decisions. An engine `FLAG` is recorded as
`ALERT`, and `SUSPEND` as `HOLD`.

### 4.3 The score is additive, and independent of the action

`score` is the plain sum of the `score` of every triggered rule. It does **not** change the action:
the engine has no threshold logic. A transaction can score 500 and still be `ALLOW` if every rule
that fired was an `ALLOW` rule. Use `score` to rank triage, not to decide.

### 4.4 Condition language

Conditions are a small nested boolean tree:

| Type | Shape | Semantics |
|---|---|---|
| `all` | `{"type":"all","all":[…]}` | AND: every child must match |
| `any` | `{"type":"any","any":[…]}` | OR: at least one child must match |
| `not` | `{"type":"not","not":{…}}` | Negation |
| `cmp` | `{"type":"cmp","field":…,"op":…,"value":…}` | Leaf comparison |

Comparison operators:

| Op | Behaviour |
|---|---|
| `EQ` / `NE` | Numeric comparison when both sides are numeric (tolerance 1e-9), otherwise string comparison |
| `GT` / `GTE` / `LT` / `LTE` | Numeric only. If either side is non-numeric the comparison is **false** |
| `IN` | `value` is a **comma-separated string**, e.g. `"5411,5812,5999"`, matched against the field as a trimmed string |
| `CONTAINS` | Substring match on the string form of the field |

**An unknown condition type or operator evaluates to `false`.** The rule does not fire.

### 4.5 A missing feature never fires a rule

If the field a `cmp` names is absent from the merged feature bag, or is null, the comparison is
**false**. This is the most common cause of "my rule isn't working": the rule is fine, but the
feature never arrived.

The effect is asymmetric:

> Because an absent field makes a comparison false, a rule meant to *catch* something does *not*
> catch it when the feature is missing or misspelled. A typo in your integration weakens
> enforcement instead of causing a visible error. Nothing logs this.

Negation flips it: `not` around a comparison on an absent field is **true**, so a misspelled field
inside a `not` can make a rule fire on everything. Check your feature names against §2 before
go-live, and check `triggeredRuleIds` in a pilot.

### 4.6 Type coercion

Values are compared with coercion, not strict typing. Booleans become `1.0`/`0.0` as numbers and
`"true"`/`"false"` as strings. Whole numbers stringify without a decimal point (`5.0` → `"5"`).
Sending `"1000"` where a rule expects `1000` works for numeric operators. Send correct JSON types
anyway.

---

## 5. Fail-closed and fail-soft behaviour

The node separates *not being allowed to decide* from *not having full information*. They behave
differently.

### 5.1 Fail-CLOSED — the node will not decide, and answers HOLD

Two gates sit in front of every evaluation. If either is shut, **no rules run** and the answer is
`HOLD`:

| Gate | Condition | `failClosedCode` | Reason string |
|---|---|---|---|
| Authorization | Node not approved by the Control Plane | `NODE_NOT_AUTHORIZED` | `edge node is not authorized by the control plane (fail-closed): …` |
| Bundle | No rule bundle has ever been verified | `NO_BUNDLE` | `edge engine has no active rule bundle (fail-closed)` |

An unapproved or unarmed node **never issues an ALLOW**. A node that cannot prove which rules it
should enforce does not approve payments.

### 5.2 Feature store unavailable — fail-closed by default

When `featurestore.fail-closed=true` (**default**) and `pan_hash` is present, an unavailable
Aerospike makes `/edge/evaluate` return **`HOLD`** at once, with `failClosedCode`
`FEATURE_STORE_UNAVAILABLE` and no rules run:

```
feature store unavailable (fail-closed): pre-auth evaluation withheld
```

A node that cannot read velocity history must not approve payments.

Set `featurestore.fail-closed=false` only in dev or test. The node then evaluates on caller
features alone, and velocity rules stop firing without notice (§4.5).

**Alert on `GET /edge/status` → `featureStore: unavailable`** whatever the setting.

### 5.3 Ledger unavailable — fail-closed by default

The node writes the evaluation row, its `txn_ref` index entry and its outbox row in one SQL
statement (one local transaction). With `ledger.fail-closed=true` (**default**,
`EDGE_LEDGER_FAIL_CLOSED`), a failed write turns the answer into:

```
HOLD, score 0, no triggered rules, failClosedCode LEDGER_UNAVAILABLE
```

The node keeps that `HOLD` row in a bounded in-memory buffer (`EDGE_LEDGER_RETRY_BUFFER_SIZE`,
default 10 000) and writes it when the ledger is back. The buffer is lost if the node restarts
before the ledger returns. `GET /edge/status` → `ledger` shows `state`, `writeFailures`,
`retryBacklog`, `retryDropped` and `outboxBacklog`. `state` is `OK`, `DOWN` or `NOT_CONFIGURED`. Alert on any
`state` other than `OK` and on a growing `retryBacklog`.

A database that is down at boot does not stop the node. The ledger stays down, the node retries
it, and every evaluation answers `HOLD / LEDGER_UNAVAILABLE` until it connects.

The dev profile sets `fail-closed=false` so you can run without Postgres. Never do that in
production.

### 5.4 Native core degradation

Evaluation normally runs in the Rust core. If a native evaluation faults, the node latches into a
degraded state and serves from the Java interpreter using an identical copy of the verified bundle.
Decisions stay correct; throughput drops. It latches (one native attempt per bundle publish), so it
cannot flap per transaction.

`GET /edge/status` reports `evaluator: native` vs `fallback-java-interpreter`, plus `nativeDegraded`
and `nativeDegradedReason`. If the standby could not parse the bundle, `standbyBundleReady` is false
and a native fault falls through to `HOLD` with `failClosedCode` `EVALUATOR_FAULT`. An unexpected
interpreter exception gets the same answer. Alert on that combination.

---

## 6. How rules arrive

The node **pulls** rules. You never push them:

1. The node polls the Control Plane every **15 s** over mTLS (`EDGE_BUNDLE_POLL_INTERVAL`).
2. Each bundle is sealed (HSE-1): Ed25519-signed, encrypted, replay-guarded.
3. The node verifies the signature against the **pinned** Control Plane key before it loads anything.
4. Load is *verify-then-swap*. A bundle that fails validation leaves the previous bundle active and
   does not advance the ETag, so the next poll retries instead of getting `304`.
5. The node keeps the verified bundle locally, so a restart enforces at once instead of holding
   traffic until the next good poll.

Structural validation is fail-closed and runs **before the bundle reaches either evaluator**, native
or Java. A bundle that fails any of these checks is rejected, and the error names the problem:

| Required | Why |
|---|---|
| `version` (number), `psp_id` (number), `rules` (array) | Top-level shape the Rust `RuleBundle` struct requires |
| Every rule has `id` (number), `name` (string), `condition`, a known `action` | The Rust `Rule` struct's required fields |
| Every `cmp` has `field`, `op` (a known operator), `value`; every `all`/`any` has its children array | The Rust `Condition` enum's per-variant requirements |

> **`psp_id` is easy to miss when you build a bundle by hand** (e.g. `POST /edge/bundle` in `dev`,
> below). It names the PSP the bundle belongs to and is required. Omit it and the rejection names
> it.

Without this check, a malformed but parseable publish would replace a good bundle with an empty
rule list. Evaluation starts at `ALLOW`, so an empty list would disarm the node to allow-everything.

`POST /edge/bundle` (plaintext upload) exists **only** in the `dev` profile. In a real deployment it
returns 404. There is no supported way to inject rules locally.

The Control Plane also serves a sealed per-PSP **Edge Config** document at `GET /api/v1/edge/config`
(retention, support access, CTR, limits, blocklist, tunnel settings). The node does not consume it
yet. The config consumer is planned (R2).

---

## 7. What is stored locally, and what is sent upward

### 7.1 Edge Ledger (PostgreSQL 16, on your premises)

The ledger is the Edge Site's system of record for transaction data. All nodes of a site can share
one ledger database. The node runs its own Flyway migrations (`db/ledger`) into the schema named by
`EDGE_LEDGER_SCHEMA` (default `edge_ledger`).

| Table | Contents |
|---|---|
| `edge_evaluations` | One row per evaluation, range-partitioned by UTC month on `evaluated_at`. Ids (`edge_assessment_id`, `txn_ref`, `site_id`, `node_id`), transaction columns (`pan_hash`, `amount_cents`, `currency`, `merchant_id`, `mcc`, `country_code`, `channel`), the cleaned `features` bag, and the decision (`action`, `score`, rule ids, `reasons`, bundle, evaluator, latency, fail-closed code). The node creates partitions 3 months ahead (`EDGE_LEDGER_PARTITION_MONTHS_AHEAD`) |
| `edge_txn_refs` | Site-wide unique `txn_ref` → newest `edge_assessment_id` |
| `edge_outbox` | Decision records v2 waiting for, or already given, delivery |
| `edge_legal_holds`, `edge_held_evaluations` | Legal holds and held rows. The tables exist; hold handling is planned (R2) |
| `edge_config`, `edge_purge_log`, `edge_access_log` | Config history, purge history, tunnel access audit. The tables exist; their writers are planned (R2, R5) |

**Retention.** Each PSP sets retention in the Console: minimum and default 5 years (FATF R.11),
maximum 25. The Control Plane stores it and puts it in the Edge Config document. The Edge purge job
is planned (R2). Until it ships, the ledger keeps every row. The contract also forbids any purge
before the node has accepted its first config document. Size the disk for at least 5 years; see the
install guide.

### 7.2 Aerospike (namespace `hokeka`)

Aerospike now holds only velocity and the bundle. The former `txn` set is gone.

| Set | Key | Contents | TTL |
|---|---|---|---|
| `velocity` | `<pan_hash>:yyyyMMddHH` | `{count, amount_cents}`, incremented atomically | 26 h |
| `rules` | `active` | Last verified bundle | Never expires |

### 7.3 Sent to Hokeka

| What | Endpoint | Contents |
|---|---|---|
| Decision records v2 | `POST /api/v1/edge/assessments`, mTLS, HSE-1 sealed (context `hokeka.decision.records`) | §3.2. Batches of up to 500, drained from `edge_outbox` in order, at least once, every 2 s by default (`EDGE_ASSESSMENT_SHIP_INTERVAL`). A restart or a cloud outage loses nothing |
| Aggregate metrics | `POST /api/v1/edge/metrics`, mTLS, HSE-1 sealed | Counts only, every 60 s. No transaction, no `pan_hash`, no amount. Metrics report v2 (hourly per-merchant and per-currency aggregates) is specified in the contract; the node still sends v1 |

Nothing else is sent. The former Hokeka AI recommendation request (`POST /api/v1/edge/decision`,
which posted the full feature bag for every `ALERT` or `HOLD`) was removed in R10c, so the
response's `reasons` contain rule reasons only and never an AI recommendation.

If the pinned Control Plane keys are not configured, nothing sealed is shipped. The node keeps
evaluating; decision records stay in the outbox.

### 7.4 What the cloud can read back

The Console will read transaction details live from the ledger over the Edge Tunnel, an outbound
WebSocket the node opens to the Control Plane on port 443. The Control Plane side is built (R4).
The Edge tunnel client is planned (R5), the cloud data proxy (R6) and the Console live views (R11)
are planned. Until they ship, the Console cannot show ledger data for Edge-only transactions.

---

## 8. Integration checklist

- [ ] Feature names match §2 exactly, in `snake_case` (`pan_hash`, not `panHash`)
- [ ] `pan_hash` is sent on every request; without it there is no velocity enrichment
- [ ] `txn_id` is sent, unique, and never a card number (else `422`)
- [ ] `amount_cents` is in **minor** units; rules using `pan_amount_sum_24h` account for major units
- [ ] You do not override a derived feature name by accident (§2.3)
- [ ] You store `edgeAssessmentId` with your transaction
- [ ] `HOLD` is handled as non-approval, including every fail-closed case (§5)
- [ ] `BLOCK` and `HOLD` paths are tested against a real node, not mocked
- [ ] Monitoring alerts on `featureStore: unavailable`, `evaluator != native` and ledger state (§5)
- [ ] A pilot confirmed `triggeredRuleIds` is non-empty for transactions you expect to trip rules
- [ ] Timeouts and a local fallback exist for when the node is unreachable
- [ ] You do not call `POST /api/v1/transactions/ingest` (legacy, removed in R12)

> **Decide your own posture if the node is unreachable.** Fail-closed only applies when the node
> can answer. If your API node cannot reach the Edge at all, the next step is your decision. Make it
> on purpose; do not default to "approve".

---

## 9. Related documents

- [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md): normative data, decision record, config and tunnel contract
- [`edge-client-install-guide.md`](edge-client-install-guide.md): installation, sizing, network requirements
- [`edge-install-troubleshooting.md`](edge-install-troubleshooting.md): installation failure modes and causes
- [`architecture/edge-channel-contract.md`](architecture/edge-channel-contract.md): Control Plane ↔ Edge wire contract (HSE-1, mTLS, lifecycle)
- [`architecture/edge-distribution-and-installation.md`](architecture/edge-distribution-and-installation.md): build, publish and upgrade chain
