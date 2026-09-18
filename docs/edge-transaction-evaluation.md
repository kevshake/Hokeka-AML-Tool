# Hokeka Edge — Transaction Evaluation & Rule Resolution

**Audience:** the PSP engineer integrating their authorisation flow with the edge node, and anyone
who needs to explain *why* a given transaction got the decision it did.

This document is the behavioural contract of `POST /edge/evaluate`. It is written from the
implementation (`EdgeController`, `EdgeEngine`, `EdgeRuleInterpreter`, `AerospikeFeatureStore`), not
from intent — where the code has a sharp edge, it is called out rather than smoothed over.

---

## 1. The request path end to end

```
Your API node
    │  POST /edge/evaluate      TLS 1.3, one virtual thread per request
    ▼
EdgeController
    │  1. read pan_hash from the body
    │  2. ENRICH: derive velocity features from the local store
    │  3. MERGE:  caller-supplied fields overwrite derived ones
    ▼
EdgeEngine
    │  4. GATE:   node authorized?      no → HOLD (no rules run)
    │  5. GATE:   rule bundle present?  no → HOLD (no rules run)
    │  6. EVALUATE via the Rust core, or the Java interpreter as warm standby
    ▼
EdgeController
    │  7. RECORD the transaction + advance velocity counters (after the decision)
    │  8. count the decision into the aggregate metrics
    ▼
    └─► 200 OK  { action, score, triggeredRuleIds, reasons }
```

Two ordering details are deliberate and worth knowing:

- **Enrichment happens before evaluation**, so rules see this card's history, not just the request.
- **Recording happens after evaluation**, so a transaction never inflates its own velocity counters.
  The transaction you just sent is *not* included in the `pan_txn_count_1h` its own rules saw.

---

## 2. The request

`POST /edge/evaluate`, `Content-Type: application/json`.

The body is a **flat JSON object of features**. There is no fixed schema and no rejection of unknown
fields: the body is a feature bag that rules are evaluated against. That flexibility has a sharp
edge — see §2.3.

### 2.1 Fields the engine itself uses

These are the names the node reads directly. Everything else is passed through to the rules untouched.

| Field | Type | Used for | If absent |
|---|---|---|---|
| `pan_hash` | string | Velocity enrichment key; stored on the txn record | **No velocity enrichment happens at all** — velocity rules cannot fire |
| `txn_id` | string | Primary key of the stored transaction | The transaction is **not persisted** (velocity counters still advance) |
| `amount_cents` | integer (minor units) | Velocity amount counters; stored | Treated as 0 in the counters |
| `merchant_id` | string | Stored on the txn record | Stored as `""` |
| `currency` | string | Stored on the txn record | Stored as `""` |
| `country_code` | string | Stored on the txn record | Stored as `""` |
| `mcc` | string | Stored on the txn record; rules commonly target it | Stored as `""` |

**Send the PAN hash, never the PAN.** The node has no use for a raw card number and storing one
would put cardholder data in the local store.

### 2.2 Features the node derives for you

Read from the local store and merged in **before** evaluation, keyed on `pan_hash`:

| Derived feature | Type | Meaning |
|---|---|---|
| `pan_txn_count_1h` | integer | Transactions on this card in the current hourly bucket |
| `pan_txn_count_24h` | integer | Transactions on this card across the last 24 hourly buckets |
| `pan_amount_sum_24h` | decimal | Sum of amounts over the same 24 buckets, **in major units** (cents ÷ 100) |

> **Unit mismatch to be aware of.** You send `amount_cents` in **minor** units; the derived
> `pan_amount_sum_24h` is in **major** units, matching the control plane's enrichment convention. A
> rule comparing the two without accounting for the 100× difference will be wrong by two orders of
> magnitude.

> **Bucket semantics.** `pan_txn_count_1h` is the *current clock hour bucket*, not a rolling 60
> minutes. At 10:59 it covers 59 minutes; at 11:01 it resets to near zero. `pan_txn_count_24h` sums
> 24 such buckets, so it spans between 23 and 24 hours of real time. Rules that need a strict rolling
> window need that expressed differently.

### 2.3 Caller values override derived values

The merge is: derived features first, then the request body on top. **Anything you send wins.**

This is intentional — it lets an integrator supply a richer value than the local store can compute,
and makes testing straightforward. It also means that if your API node sends its own
`pan_txn_count_24h`, the locally-derived one is discarded silently. Do not send fields whose names
collide with §2.2 unless you mean to replace them.

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

`channel` and `is_cross_border` are not engine fields — they are passed straight through and are
available to rules. That is how you extend the feature set without a code change.

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
  ]
}
```

| Field | Meaning |
|---|---|
| `action` | The decision — one of `ALLOW`, `ALERT`, `HOLD`, `BLOCK` |
| `score` | **Sum** of the `score` of every triggered rule (see §4.3) |
| `triggeredRuleIds` | Ids of all rules whose condition matched, in bundle order |
| `reasons` | Each triggered rule's `description`, falling back to its `name` |

### 3.1 What the actions mean for your flow

| Action | Severity | Expected handling |
|---|---|---|
| `ALLOW` | 0 | Proceed with the authorisation |
| `ALERT` | 1 | Proceed, but raise a case / flag for review |
| `HOLD` | 2 | Do not auto-approve; route to manual review or step-up |
| `BLOCK` | 3 | Decline |

**Treat `HOLD` as non-approval.** It is also the engine's fail-closed answer (§5), so a `HOLD` may
mean "this transaction is suspicious" *or* "this node is currently unable to decide safely". Both
require the same handling: do not approve on your own.

---

## 4. How a decision is resolved

### 4.1 Every rule is evaluated — there is no short-circuit

The engine iterates the **entire** rule list. It does not stop at the first match. So
`triggeredRuleIds` and `reasons` are complete: they list every rule that fired, which is what makes
the decision explainable after the fact.

### 4.2 The action is the maximum severity, not the last match

Each triggered rule contributes its action, and the result is the **highest severity** across all of
them, starting from `ALLOW`:

```
ALLOW(0) < ALERT(1) < HOLD(2) < BLOCK(3)
```

One `BLOCK` among fifty `ALLOW`s yields `BLOCK`. Rule order does not affect the action — only
severity does. There is no way for a later rule to downgrade an earlier one; there are no "allow-list
override" semantics. A rule cannot rescue a transaction another rule blocked.

### 4.3 The score is additive, and independent of the action

`score` is the plain sum of the `score` field of every triggered rule. It does **not** influence the
action — there is no threshold logic in the engine. A transaction can score 500 and still be
`ALLOW` if every rule that fired was an `ALLOW` rule. Use `score` for triage ranking, not as a
decision input.

### 4.4 Condition language

Conditions are a small nested boolean tree:

| Type | Shape | Semantics |
|---|---|---|
| `all` | `{"type":"all","all":[…]}` | AND — every child must match |
| `any` | `{"type":"any","any":[…]}` | OR — at least one child must match |
| `not` | `{"type":"not","not":{…}}` | Negation |
| `cmp` | `{"type":"cmp","field":…,"op":…,"value":…}` | Leaf comparison |

Comparison operators:

| Op | Behaviour |
|---|---|
| `EQ` / `NE` | Numeric comparison when both sides are numeric (tolerance 1e-9), otherwise string comparison |
| `GT` / `GTE` / `LT` / `LTE` | Numeric only. If either side is non-numeric the comparison is **false** |
| `IN` | `value` is a **comma-separated string**, e.g. `"5411,5812,5999"`; matched against the field as a string, trimmed |
| `CONTAINS` | Substring match on the string form of the field |

**An unknown condition type or operator evaluates to `false`** — the rule simply does not fire.

### 4.5 A missing feature never fires a rule

If the field named by a `cmp` is absent from the merged feature bag, or is null, the comparison is
**false**. This is the single most common cause of "my rule isn't working": the rule is fine, the
feature name never arrived.

The consequence is asymmetric and worth stating plainly:

> Because an absent field makes a comparison false, a rule intended to *catch* something will
> silently *not catch* it when the feature is missing or misspelled. A typo in your integration
> weakens enforcement rather than causing a visible error. Nothing logs this.

Negation inverts it: `not` around a comparison on an absent field evaluates to **true**, so a
misspelled field inside a `not` can make a rule fire on everything. Validate your feature names
against §2 before go-live, and check `triggeredRuleIds` in a pilot rather than assuming.

### 4.6 Type coercion

Values are compared with coercion rather than strict typing: booleans become `1.0`/`0.0` numerically
and `"true"`/`"false"` as strings; whole-valued numbers stringify without a decimal point (`5.0` →
`"5"`). Sending `"1000"` where a rule expects `1000` works for numeric operators. It is still better
to send correct JSON types.

---

## 5. Fail-closed and fail-soft behaviour

The node distinguishes between *not being allowed to decide* and *not having full information*. The
two behave very differently, and the distinction matters for how you handle the response.

### 5.1 Fail-CLOSED — the node will not decide, and answers HOLD

Two gates sit in front of every evaluation. If either is shut, **no rules run at all** and the
answer is `HOLD` with an explanatory reason:

| Gate | Condition | Reason string |
|---|---|---|
| Authorization | Node not approved by the control plane | `edge node is not authorized by the control plane (fail-closed): …` |
| Bundle | No rule bundle has ever been verified | `edge engine has no active rule bundle (fail-closed)` |

An unapproved or unarmed node therefore **never issues an ALLOW**. This is the intended safety
property: a node that cannot prove what rules it should be enforcing does not get to approve
payments.

### 5.2 Feature store unavailable — fail-closed by default

When `featurestore.fail-closed=true` (**default**) and `pan_hash` is present, an unavailable local
store causes `/edge/evaluate` to return **`HOLD`** immediately (no rules run):

```
feature store unavailable (fail-closed): pre-auth evaluation withheld
```

This matches the architecture intent: a node that cannot read velocity history must not silently
approve payments.

Set `featurestore.fail-closed=false` only in dev/test if you explicitly want legacy fail-soft
behaviour (evaluate on caller-supplied features alone — velocity rules silently stop firing; see
§4.5).

**Alert on `GET /edge/status` → `featureStore: unavailable`** regardless of fail-closed setting.

### 5.2.1 Cloud compliance requires dual-post

Edge evaluation alone does **not** create cloud alerts, cases, SAR, or webhooks. PSPs that need those
artifacts must also POST `/api/v1/transactions/ingest` — see
[`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

### 5.3 Native core degradation

Evaluation normally runs in the Rust core. If a native evaluation faults, the node latches into a
degraded state and serves from the Java interpreter using an identical copy of the verified bundle —
correct decisions, lower throughput, rather than holding all traffic. It is latched (one native
attempt per bundle publish) so it cannot flap per transaction.

`GET /edge/status` reports `evaluator: native` vs `fallback-java-interpreter`, plus `nativeDegraded`
and `nativeDegradedReason`. If the standby could not parse the bundle, `standbyBundleReady` is false
and a native fault would fall through to `HOLD` — that combination is worth alerting on.

---

## 6. How rules arrive

Rules are **pulled**, never pushed by you:

1. The node polls the control plane every **15 s** over mTLS.
2. Each bundle is sealed (HSE-1): Ed25519-signed, encrypted, replay-guarded.
3. The signature is verified against the **pinned** control-plane key before anything is loaded.
4. Verification and load are *verify-then-swap*: a bundle that fails validation leaves the previous
   bundle active and does not advance the ETag, so the next poll retries rather than being answered
   `304`.
5. The verified bundle is persisted locally, so a restart resumes enforcing immediately instead of
   holding traffic until the next successful poll.

Structural validation is fail-closed by design and runs **before the bundle ever reaches either
evaluator** — native or Java — so rejection quality does not depend on which one happens to be
active. A bundle failing any of these is rejected outright, naming the specific problem:

| Required | Why |
|---|---|
| `version` (number), `psp_id` (number), `rules` (array) | Top-level shape the Rust `RuleBundle` struct requires |
| Every rule has `id` (number), `name` (string, present), `condition`, a known `action` | Matches the Rust `Rule` struct's required fields |
| Every `cmp` condition has `field`, `op` (a known operator), `value`; every `all`/`any` has its children array | Matches the Rust `Condition` enum's per-variant requirements |

> **`psp_id` is easy to miss when hand-constructing a bundle** (e.g. via `POST /edge/bundle` in
> `dev` — see below) — it identifies which PSP the bundle belongs to and is required even though
> nothing else in this document's request/response shapes uses it. Omit it and the rejection names
> it explicitly rather than failing with an opaque "malformed IR".

Without this check, a malformed-but-parseable publish would replace a good bundle with an empty rule
list — and since evaluation starts at `ALLOW` and iterates an empty list, it would silently disarm
the node to allow-everything.

`POST /edge/bundle` (plaintext upload) exists **only** under the `dev` profile. In a real deployment
it is a 404. There is no supported way to inject rules locally.

---

## 7. What is stored locally, and what is sent upward

### 7.1 Stored on your premises (Aerospike, namespace `hokeka`)

| Set | Key | Contents | TTL |
|---|---|---|---|
| `txn` | `txn_id` | `pan_hash`, `amount_cents`, `ts`, `merchant_id`, `currency`, `country_code`, `mcc`, **plus the decision, score and triggered rule ids** | Retention (see install guide §2.4) |
| `velocity` | `<pan_hash>:yyyyMMddHH` | `{count, amount_cents}`, incremented atomically | 26 h |
| `rules` | `active` | Last verified bundle | Never expires |

The decision and its rule attribution are stored with the transaction deliberately: without them the
record cannot answer *"what did we do, and why?"* — which is the question an audit asks.

### 7.2 Sent to Hokeka

Aggregate counts only, every 60 s, inside mTLS and encrypted with the pinned control-plane key. No
transaction, no `pan_hash`, no amount. If the pinned keys are not configured, no metrics are shipped
at all — the node keeps evaluating regardless.

---

## 8. Integration checklist

- [ ] Feature names match §2 exactly, in `snake_case` (`pan_hash`, not `panHash`)
- [ ] `pan_hash` is sent on every request — without it there is no velocity enrichment
- [ ] `txn_id` is sent and unique — without it the transaction is not persisted
- [ ] `amount_cents` is in **minor** units; rules using `pan_amount_sum_24h` account for major units
- [ ] You are not accidentally overriding a derived feature name (§2.3)
- [ ] `HOLD` is handled as non-approval, including the fail-closed case (§5.1)
- [ ] `BLOCK` and `HOLD` paths are tested against a real node, not mocked
- [ ] Monitoring alerts on `featureStore: unavailable` and `evaluator != native` (§5.2, §5.3)
- [ ] A pilot has confirmed `triggeredRuleIds` is non-empty for transactions you expect to trip rules
- [ ] Timeouts and a local fallback exist for the case where the node is unreachable entirely

> **Decide your own posture if the node is unreachable.** The engine's fail-closed behaviour only
> applies when it can answer. If your API node cannot reach the edge at all, what happens next is
> your decision to make, and it should be made deliberately rather than defaulting to "approve".

---

## 9. Related documents

- `docs/edge-client-install-guide.md` — installation, sizing, network requirements
- `docs/edge-install-troubleshooting.md` — installation failure modes and root causes
- `docs/architecture/edge-channel-contract.md` — the normative control-plane ↔ edge wire contract
- `docs/architecture/edge-distribution-and-installation.md` — build, publish and upgrade chain
