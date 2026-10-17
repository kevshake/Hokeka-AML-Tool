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
    │  2. SCREEN: counterparty names against the in-memory sanctions list, merchant against the
    │     config blocklist (§6.2; in memory only, no I/O)
    │  3. ENRICH: derive history features from Aerospike (fail-closed if unavailable), then the
    │     compliance overlay: CTR card-day total, cash structuring, Edge Config limits (§6.1);
    │     caller-supplied fields overwrite derived ones
    ▼
EdgeEngine
    │  4. GATE:   node authorized?      no → HOLD (no rules run)
    │  5. GATE:   rule bundle present?  no → HOLD (no rules run)
    │  6. EVALUATE via the Rust core, or the Java interpreter as warm standby
    ▼
EdgeController
    │  7. MERGE the rules', the overlay's and the screening's actions: strongest wins
    │     (BLOCK > HOLD > ALERT > ALLOW); a fail-closed HOLD stays HOLD
    │     then advance this card's history and the limit usage with the FINAL action
    │     (after the decision; site-scoped keys; a BLOCK from any source uses no limit)
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

- **Screening happens before anything is counted**, so a sanctions or blocklist `BLOCK` is known
  when limit usage is committed and consumes no limit. It reads only memory (the swapped list and
  the config in force), so it costs no store round trip.
- **Enrichment happens before evaluation**, so rules see this card's history.
- **History advances after evaluation**, so stored counters never contain the transaction being
  evaluated; the derivation then adds it explicitly where the cloud's windows did (§2.2), so
  `pan_txn_count_24h` = earlier transactions + 1. The CTR card-day total is the exception: it is
  advanced atomically before evaluation, so concurrent nodes cannot both miss the crossing.
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

The node reads the card's (and its device's and IP's) history from Aerospike in **one batch get**
and merges the derived features in **before** evaluation (`CardHistoryFeatures`, R10a). Names and
arithmetic are the cloud's (`RuleFeatureEnrichmentService`), so the compiled SpEL typology catalog
sees the same values the cloud rules saw. **Counts and sums over a window ending "now" include the
transaction being evaluated**, as the cloud's did (it stored the transaction before scoring);
features the cloud computed over earlier transactions only ("before now") exclude it.

| Derived feature(s) | Source | Meaning |
|---|---|---|
| `amount` | request | `amount_cents ÷ 100` (0 when absent), as the cloud's `#tx.amount` |
| `txn_direction` | request | `direction` normalised: starts with `IN` or is `CREDIT` → `INBOUND`, else `OUTBOUND` |
| `amount_ending_pattern` | request | amount ends in `.00` or `.99` |
| `pan_txn_count_1m`, `_5m`, `_1h` | recent list | Rolling, exact, current included |
| `pan_txn_count_24h`, `pan_amount_sum_24h` | hourly buckets | Last 24 UTC hourly buckets + current; sum **in major units** |
| `is_first_transaction`, `days_since_last_activity` | card profile | No earlier evaluation on the site; whole days since the last one |
| `is_new_country`, `is_new_currency` | recent list | No earlier evaluation with this country / currency in 30 days |
| `txn_count_deviation_ratio`, `volume_deviation_ratio` | daily buckets | 7-day vs 30-day daily average (count / amount) |
| `avg_amount_spike_ratio`, `zscore_amount_vs_pan_history` | daily buckets | Amount vs the card's 30-day mean; z-score with the population standard deviation (0 without history or spread) |
| `volume_ratio_t1_t2`, `txn_count_window_ratio`, `daily_txn_count_ratio`, `avg_amount_window_ratio`, `daily_avg_amount_ratio` | hourly + daily buckets | The cloud's window ratios |
| `send_receive_ratio`, `spend_receive_spike_ratio` | daily / hourly buckets | Rapid movement: outbound vs inbound count (7 d) and amount (24 h) |
| `same_parties_volume`, `circular_trading_count`, `refund_share_pct`, `round_value_share_pct`, `round_value_txn_count`, `unique_senders`, `unique_counterparties`, `distinct_banks`, `bank_name_changes`, `distinct_payment_ids` | recent list | 7-day features over the card's remembered evaluations |
| `distinct_users_per_payment_id` | device profile | Distinct merchants seen with `device_id` (else `device_fingerprint`) |
| `distinct_users_per_ip`, `payment_id_use_count`, `distinct_countries`, `ip_outside_expected_location`, `ip_change_count` | IP profile | Merchants, 7-day uses and countries seen with `ip_address` |
| `cash_transaction`, `ctr_required`, `is_structuring_suspected`, `structuring_repeat_count_24h`, `structuring_amount_band` | Edge Config + counters | See §6.1 |
| `anonymous_payment_screening_hit`, `reference_lc`, `device_key` | request | Prepaid `card_type`, cash, or no `customer_account_reference` / `account_id` / `customer_id` / `customer_email`; lower-cased `reference`; device key |

A feature the cloud set only under a condition (e.g. `avg_amount_spike_ratio` only when the card has
a 30-day average) is set here under the same condition.

> **Unit mismatch.** You send `amount_cents` in **minor** units. The derived `amount` and
> `pan_amount_sum_24h` are in **major** units.

> **Window resolution.** 1 min / 5 min / 1 h are rolling and exact from the card's last 100
> evaluations (when 100 fall inside the window, the count is bounded below by the current and
> previous hourly buckets). 24 h is the last 24 UTC hourly buckets, so it spans 23 to 24 hours;
> "yesterday" is buckets 24–47. 7 and 30 days are the current UTC day plus the 6 / 29 previous
> ones. The cloud's 30-day z-score used at most 1 000 amounts; the Edge uses all of them. "New
> country/currency" looks at the last 100 evaluations (the cloud looked at 200).

**Cost.** Before evaluation: one batch get of at most 82 keys (48 hourly + 31 daily buckets, card,
device and IP profiles). After it: two atomic adds (hour, day) and up to three generation-checked
profile writes (card, device, IP; each ≤ 100 entries, ~8 KB). With CTR on, a cash card transaction
adds one atomic `operate`; Edge Config limits add one batch get before and one add per applicable
limit after. No ledger query runs on the evaluate path. The in-process cost of deriving every
feature and evaluating the 50-rule compiled catalog on the Java interpreter measured p50 0.03 ms /
p99 0.38 ms (`TypologyLatencyTest`, dev laptop); the store round trips come on top (on a site LAN a
batch get and an `operate` typically cost 0.1–0.5 ms each — not measured in this repository,
because the test environment has no Aerospike server).

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
| `ctrRequired`, `typologyCodes` | Set by the compliance overlay (§6.1): `CTR_REPORTABLE`, `CASH_STRUCTURING_24H`, `LIMIT_<limitId>`, and the fail-visible codes `CTR_FX_UNAVAILABLE`, `CTR_CONFIG_INVALID`, `CTR_AGGREGATE_UNAVAILABLE`, `LIMIT_FX_UNAVAILABLE`, `LIMITS_UNAVAILABLE` (R10a) |
| `sanctionsHit`, `watchlistEntryIds` | From local screening of `counterparty_name`, `cardholder_name` and `beneficiary_name` (§6.2; R10b, contract §6.5). Entry ids only, never the names |

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
| `exists` | `{"type":"exists","field":…}` | IR v2: the field is present with a scalar value (SpEL `x != null`) |
| `in_list` | `{"type":"in_list","field":…,"list":…}` | IR v2: the field's string form is in the bundle's `lists[list]`, ASCII case-insensitive (high-risk countries, payment blacklists, merchants onboarded in a high-risk country) |
| `const` | `{"type":"const","value":true\|false}` | IR v2: fixed truth value (a rule its parameters make inert) |

Comparison operators:

| Op | Behaviour |
|---|---|
| `EQ` / `NE` | Numeric comparison when both sides are numeric (tolerance 1e-9), otherwise string comparison |
| `GT` / `GTE` / `LT` / `LTE` | Numeric only. If either side is non-numeric the comparison is **false** |
| `IN` | `value` is a **comma-separated string**, e.g. `"5411,5812,5999"`, matched against the field as a trimmed string |
| `CONTAINS` | Substring match on the string form of the field and of the value |

A string counts as a number only when it is a plain decimal literal (`-12.5`, `1e3`; no spaces,
`inf`, `NaN`, hex or type suffix). A JSON `null`, array or object feature counts as absent. Numbers
compare as text in their shortest plain form (`0.5`, `0.0000001`; integral values without a
decimal point). The Rust core and the Java interpreter implement exactly this; the shared vectors
`edge-engine/test-vectors/rule_ir_parity.json` run in both (and through JNI with `-Pnative-core`).

**A bundle with an unknown condition type or operator, or an `in_list` naming a list it does not
define, is rejected at load** and the previous bundle stays active.

**Compiled SpEL catalog.** The Control Plane compiles the seeded SpEL typology rules to this IR
(`EdgeRuleCompiler`, R10a): `#params` are folded in, `#tx` / `#features` become the derived features
of §2.2, and SpEL's null ordering is kept (`null < x` and `null != x` are true in SpEL, so those
compile to `any(not exists f, cmp)`). A rule that cannot run on the Edge is listed with its reason
in the bundle's `uncompiled` section (and in the cloud's `edge_bundles.uncompiled_rules`); it is
never dropped silently. Today these are the screening rules (R-17, R-32, R-128, R-169 — R10b),
the merchant legal-name rules (R-45, R-61, R-118), the chargeback/dispute rules (R-CB-1…5), the
peer-group rule (R-127) and the scheme-stage rule (R-173).

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
(retention, support access, CTR, limits, blocklist, tunnel settings). The node consumes it (R2);
its CTR and limits sections drive §6.1.

### 6.1 Compliance overlay: CTR, FX, cash structuring, limits (R10a)

`ComplianceOverlay` runs on every evaluation, next to the rules. Its action is merged with the
rules' (strongest wins); its codes go into the decision record's `typologyCodes`; its reasons go
into the response and the ledger only. Before the first config document is accepted: CTR off, no
limits (contract §6.3).

- **Cash** = request `cash_transaction` / `is_cash` true, or `channel` in `ctr.cashChannels`.
- **CTR.** Each cash amount is converted to `ctr.reportingCurrency` with `ctr.fx`
  (`rates[C]` = base units per unit of C) and added, in micro-minor units rounded up, to an atomic
  site counter for the card and business day (`ctr.businessDayTimezone`). The transaction that takes
  the total to `thresholds[reportingCurrency]` or above sets `ctrRequired` (`CTR_REPORTABLE`); later
  cash transactions of the same card-day do not (the CTR workflow pulls the whole day with
  `filing.extract`). Every evaluated cash transaction counts, whatever its decision. Without a card
  key the check is per transaction.
- **Fail visibly, never under-report.** A cash amount that cannot be converted (no rate, zero rate,
  no currency) → `HOLD`, `ctrRequired = true`, `CTR_FX_UNAVAILABLE`. CTR enabled without a threshold
  for the reporting currency → `HOLD`, `ctrRequired`, `CTR_CONFIG_INVALID`. Counter unreadable →
  `HOLD`, `ctrRequired`, `CTR_AGGREGATE_UNAVAILABLE`.
- **Cash structuring** (the cloud detector): a cash amount in
  [`ctr.structuring.floorRatio` × threshold, threshold) — defaults 0.80 and 2 — with at least
  `minTransactions` such transactions on the card in the last 24 hourly buckets, current included →
  `HOLD`, `CASH_STRUCTURING_24H`.
- **Limits.** Each `limits[]` entry whose `merchantId` / `mcc` filter matches is checked against
  site counters keyed by limit and scope value (card, merchant, or the whole PSP): `PT1H` = 12 ×
  5-minute buckets, `P1D` = 24 hourly, `P7D` / `P30D` = 7 / 30 daily. `used + this > max` applies the
  limit's `action` with `LIMIT_<limitId>`. AMOUNT limits convert into the limit's `currency`; no rate
  → `HOLD`, `LIMIT_FX_UNAVAILABLE`. Unreadable counters → `HOLD`, `LIMITS_UNAVAILABLE`. Usage is
  recorded after the decision, except for `BLOCK` (a declined payment uses no limit). A `PSP`-scope
  limit increments one record per bucket for every transaction of the site: keep such limits few on
  high-volume sites (Aerospike serialises writes to one record).

### 6.2 Sanctions screening and merchant blocklist (R10b)

`SanctionsScreener` runs on every evaluation before the overlay and the rules
(contract §6.5). Nothing it reads leaves the Edge and nothing it does is logged with a name or a
value.

- `counterparty_name`, `cardholder_name` and `beneficiary_name` are screened against the
  in-memory sanctions list. A sanctions match scoring ≥ 0.95 → `BLOCK`; a lower match → at least
  `HOLD`. Both set `sanctionsHit` and `watchlistEntryIds`.
- A PEP/RCA match is never a sanctions hit and never changes the action: it adds a local reason
  (enhanced due diligence) only.
- A `merchant_id` on the config's merchant blocklist → `BLOCK`.
- The config names a published list but none is loaded yet → `HOLD` for an evaluation carrying a
  name (`hokeka.sanctions.hold-when-list-missing`, default true).
- A fail-closed `HOLD` stays `HOLD`; its hits are still recorded.

**Combining.** The final action is the strongest of the rules, the overlay (§6.1) and screening.
The decision record carries all of it at once: a held potential sanctions hit on a cash
transaction that crosses the CTR threshold has `decision = HOLD`, `sanctionsHit = true`, its
`watchlistEntryIds`, `ctrRequired = true` and `CTR_REPORTABLE`. A blocklisted merchant breaching a
limit is `BLOCK` with `LIMIT_<limitId>`, and the limit usage is not advanced. A CTR card-day total
counts every evaluated cash transaction, including a blocked one, so the CTR never under-reports.

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

Aerospike holds history for the derivations, compliance counters and the bundle; every key ends
with the site id. The former `txn` set is gone.

| Set | Key | Contents | TTL |
|---|---|---|---|
| `velocity` | `<pan_hash>:yyyyMMddHH:<site>` | `{count, amount_cents, amount_n, in/out count, in/out cents, band}`, atomic adds | 50 h |
| `velocity_day` | `<pan_hash>:yyyyMMdd:<site>` | `{count, cents, amount_n, sq, in/out count}`, atomic adds | 32 d |
| `card_profile` | `<pan_hash>:<site>` | first/last seen, last 100 evaluations (time, amount, merchant, country, currency, direction, device, brand) | 800 d |
| `device_profile`, `ip_profile` | `<device or ip>:<site>` | last 100 evaluations seen with it | 400 d |
| `ctr_day` | `<pan_hash>:<yyyyMMdd business day>:<site>` | cash total (reporting currency, micro-minor units), count | 4 d |
| `limit_usage` | `L<limitId>:<scope value>:<bucket>:<site>` | count, amount (limit currency, minor units) | window + 1 bucket |
| `rules` | `active` | Last verified bundle | Never expires |

The profiles hold card-linked data (merchant, country, device) for at most 800 days, on the PSP's
premises; size Aerospike memory for one primary-index entry per active card, device and IP.

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
