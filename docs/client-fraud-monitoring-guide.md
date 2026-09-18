# Hokeka — Client Guide to Transaction Monitoring, Rules Management & Threat Identification

**Audience:** the PSP's fraud/compliance team using the Hokeka platform day to day — reviewing
alerts, investigating cases, tuning rules, and filing regulatory reports.

**Scope:** this guide covers the whole detection loop across **both halves of the platform**:

- **The edge node**, running on your own premises, which makes real-time ALLOW/ALERT/HOLD/BLOCK
  decisions on every transaction as it happens. See `docs/edge-transaction-evaluation.md` for the
  wire-level contract if your team integrates the API directly.
- **The control plane** (the Hokeka web app), where your team manages rules, reviews what the edge
  decided, investigates flagged activity, and files reports.

Rules are authored centrally and pulled down to your edge node automatically; decisions your edge
node makes are pushed back up as aggregate metrics, and — for anything above ALLOW — as full alerts
your team investigates in the app. Nothing about this loop requires touching a server yourself once
the edge node is installed (see `docs/edge-client-install-guide.md` for that one-time setup).

---

## 1. The detection loop, end to end

```
 Your customer's         Your edge node                Hokeka control plane
 payment                 (on YOUR premises)             (the web app your team uses)
 ─────────────           ───────────────                ─────────────────────────
 API node  ──txn──►  ALLOW / ALERT / HOLD / BLOCK
              │          in real time, using rules
              │          pulled from the control plane
              │
              └──aggregate metrics──────────────────►  Transaction Monitoring, Dashboard
                 (never the transaction itself)
                                                          │
                                                          ▼
                                                    ALERT/HOLD/BLOCK also raised as an
                                                    Alert (rule hit) — or via ML scoring,
                                                    sanctions match, chargeback pattern,
                                                    or network anomaly, on transactions
                                                    scored centrally
                                                          │
                                                          ▼
                                                    Your team reviews it in Alerts,
                                                    dispositions it, and — if it's a real
                                                    threat — a Case opens for investigation
                                                          │
                                                          ▼
                                                    Case resolves: cleared, SAR filed,
                                                    entity blocked, or escalated
```

Two things worth internalising before anything else:

- **Real-time decisioning happens once, at your edge node, in milliseconds.** By the time a
  transaction shows up in the control plane's Transaction Monitoring page, the decision has already
  been made and enforced. The control plane is where you *review and investigate*, not where you
  *decide*.
- **A threat can surface from five different signals**, not just a rule you wrote: a rule hit, an ML
  risk score, a sanctions/PEP match, a chargeback pattern, or a network-graph anomaly (shared
  devices, shared beneficial owners, transaction rings). Rules Management only controls the first
  one. The others run continuously in the background regardless of what rules you've configured.

---

## 2. Where things live in the app

| Route | What it's for |
|---|---|
| `/dashboard` | Top-level health and volume view |
| `/transaction-monitoring` | Live feed, analytics, reports, and SAR tracking over scored transactions |
| `/alerts` | Every ALERT/HOLD/BLOCK decision and rule/ML/sanctions hit, awaiting disposition |
| `/cases` | Investigations opened from alerts — queues, all-cases, network graph, timeline |
| `/rules-generation` | Author and manage AML, velocity, and threshold rules (incl. AI-assisted drafting) |
| `/screening` | Sanctions/PEP screening results for customers and merchants |
| `/risk-analytics` | Risk scoring trends and model behaviour |
| `/regulatory-reports` | SAR/CTR filing status and history |
| `/edge-nodes` | Your on-prem edge node(s) — enrollment, approval, health, fleet analytics |

---

## 3. How a transaction becomes a decision (the edge)

Every transaction your API nodes send to your edge node is evaluated against the current rule
bundle in real time. This is documented in full in `docs/edge-transaction-evaluation.md`; the
essentials for a fraud analyst:

- **Every rule in the bundle is checked, not just the first match.** The response's
  `triggeredRuleIds` lists all of them, and `reasons` explains each in plain language.
- **The final action is the single highest-severity rule that fired** — `ALLOW < ALERT < HOLD <
  BLOCK`. One BLOCK rule among ten ALLOW rules still blocks. There is no "override" rule that can
  downgrade a stronger action.
- **The score is additive** across every triggered rule, used for triage ranking — it does **not**
  influence the action itself. A high score with an ALLOW action means several minor signals fired
  together, worth a look but not itself the reason for a threat.
- **Rules see the card's recent history**, not just the single transaction: how many times this card
  has transacted in the last hour and the last 24 hours, and the amount total, computed from what
  your edge node has recorded locally. This is what makes velocity-based rules (rapid repeat
  transactions, structuring) possible without a round-trip to Hokeka.
- **An edge node that cannot verify what rules it should be enforcing never approves anything.** If
  it has not been approved in the portal yet, or has lost contact with the control plane long enough
  that its rule bundle is stale, it answers HOLD for every transaction rather than guessing. This is
  intentional fail-closed behaviour, not a bug — see `/edge-nodes` for the node's current status if
  you see a spike in HOLDs with no obvious cause.

### 3.1 Monitoring the health of your own edge node

Before trusting what an edge node reports, check `/edge-nodes` for these three signals — a node can
answer every request while quietly degraded on one of them:

| Signal | Healthy value | If not |
|---|---|---|
| Authorization | approved / active | An unapproved node HOLDs everything — approve it, or check why it was suspended/revoked |
| Evaluator | native | `fallback-java-interpreter` means the fast Rust kernel isn't loaded — decisions are still correct but slower |
| Feature store | connected | If disconnected, velocity-based rules silently stop firing — the node still answers, just with less information |

---

## 4. Transaction Monitoring — reviewing what the edge decided

`/transaction-monitoring` gives you five views:

- **Live** — the real-time feed of scored transactions as they arrive.
- **Analytics** — volume, decision mix (ALLOW/ALERT/HOLD/BLOCK), and trend lines over time.
- **Reports** — scheduled/exportable summaries.
- **SARs** — transactions flagged as SAR-relevant, tracked through to filing.

Every settled transaction is scored, not only the ones that flowed through your edge in real time —
a daily batch job re-scores anything that missed real-time scoring and routes it through the same
decision path, so **overnight/settled monitoring raises the same alerts and cases real-time
monitoring does.** Nothing scored in batch is a second-class citizen; it goes through identical rule
evaluation and, where warranted, produces the same Alert/Case trail described below.

---

## 5. Alerts — the first stop for anything above ALLOW

Every ALERT, HOLD, or BLOCK decision — from a rule hit, an ML score, a sanctions match, a chargeback
pattern, or a network anomaly — surfaces in `/alerts` for a human to review.

### 5.1 What you're looking at

| Field | Meaning |
|---|---|
| `severity` | `INFO`, `WARN`, or `CRITICAL` |
| `status` | `open`, `closed`, or `false_positive` |
| `triggeredRules` | Which rule(s) fired, in plain terms |
| `sarRequired` / `ctrRequired` | Whether this alert's disposition is required to feed into a regulatory filing |
| `investigator` | Who is working it |

The severity an alert carries generally tracks the action it came from: a BLOCK-originated alert is
CRITICAL, a HOLD-originated alert is HIGH-risk, and an ALERT/REVIEW-originated alert is
MEDIUM — use this as your default triage order when a queue is backed up.

### 5.2 Disposing an alert

Every alert is closed with a **disposition code**, not a free-text status. These matter — they feed
directly into your true-positive/false-positive rate and your regulatory record:

| Category | Codes |
|---|---|
| **False positive** | `FALSE_POSITIVE`, `DUPLICATE`, `TECHNICAL_ERROR` |
| **True positive** | `TRUE_POSITIVE_SAR_FILED`, `TRUE_POSITIVE_BLOCKED`, `TRUE_POSITIVE_REPORTED` |
| **Needs more work** | `ESCALATED`, `PENDING_INFORMATION`, `ONGOING_MONITORING` |
| **Cleared with reason** | `CLEARED_LOW_RISK`, `CLEARED_CUSTOMER_EXPLANATION`, `CLEARED_KNOWN_PATTERN` |
| **Administrative** | `MERGED_WITH_CASE`, `SUPERSEDED`, `EXPIRED` |

**Pick the disposition that is actually true, not the one that closes the queue fastest.**
`CLEARED_LOW_RISK` and `FALSE_POSITIVE` look similar in a hurry, but only one of them tells you your
rule needs tuning. Your true-positive rate is the single number that tells you whether your rules
are working — it is generated from these codes, so sloppy disposition makes that number meaningless.

An alert that turns out to be genuinely suspicious is where a **Case** opens.

---

## 6. Cases — where an investigation actually happens

`/cases` is where an alert (or several related alerts) becomes a documented investigation, with
evidence, notes, an assigned investigator, and a resolution that stands up to audit.

### 6.1 Lifecycle

```
NEW ──► ASSIGNED ──► IN_PROGRESS ──► PENDING_REVIEW ──► CLOSED_*
              │              │
              ▼              ▼
       PENDING_INFO     ESCALATED (to MLRO / senior investigator)
                                │
                                ▼
                          REOPENED (from any closed state, if new evidence surfaces)
```

Closed states are specific about the outcome, not just "done": `CLOSED_CLEARED`,
`CLOSED_SAR_FILED`, `CLOSED_BLOCKED`, `CLOSED_REJECTED`. Pick the one that matches what actually
happened — this is your audit trail if a regulator asks why a case closed the way it did.

### 6.2 Priority and SLA

| Priority | When it applies |
|---|---|
| `LOW` | Routine checks, low risk score |
| `MEDIUM` | Standard alerts, medium risk score |
| `HIGH` | High risk score, PEP matches, sanctions hits |
| `CRITICAL` | Immediate attention — potential large-scale fraud or terrorist financing |

Every case carries an SLA deadline and tracks its own age (`daysOpen`). A case that blows its SLA is
a compliance exposure independent of whether the underlying activity turns out to be a real threat —
treat SLA breaches as their own alert, not just a queue-management annoyance.

### 6.3 Five ways a case can open

| Trigger | What it means |
|---|---|
| Rule | A specific rule fired on a transaction |
| ML score | The risk model scored the transaction/customer above threshold |
| Sanctions | A name/entity matched a sanctions or PEP list — always opens **HIGH priority** regardless of other factors |
| Chargeback pattern | Dispute/chargeback activity crossed a threshold for a merchant |
| Network/graph anomaly | Shared devices, shared beneficial owners, or a transaction ring detected across otherwise-unrelated accounts |

**A sanctions-triggered case is deliberately never lower than HIGH priority, and it is created even
with no linked transaction** — a screening hit on a merchant or counterparty during onboarding is
itself the threat, independent of any specific payment. Don't wait for a transaction to show up
before treating a sanctions case as real.

### 6.4 Investigating

Each case carries **evidence** (documents, exports, screenshots your team attaches), **notes**
(the running investigation log — this is what a regulator or auditor reads to understand your
reasoning), **linked alerts**, and **linked decisions**. Related cases can be cross-referenced
directly — useful when the same counterparty or device shows up across multiple otherwise-separate
investigations, which is often the first sign of a coordinated pattern rather than isolated activity.

---

## 7. Rules Management — tuning what the edge enforces

`/rules-generation` is where your rules are authored. Three kinds, on three tabs:

| Tab | What it's for |
|---|---|
| **AML** | Expression-based rules (SpEL or a Java-bean rule), with a category and typology tag |
| **Velocity** | Rules over transaction frequency/amount within a time window — the same shape as the edge's built-in `pan_txn_count_1h`/`pan_txn_count_24h` features |
| **Threshold** | Simple amount/limit-based rules |

An **AI-assisted draft generator** is available on this page: describe the pattern you're trying to
catch in plain language, and it proposes a rule (name, condition, typology, action) for you to
review and adjust before saving — it does not publish anything on your behalf.

### 7.1 What "editing a rule" actually changes

Editing an existing rule loads its current parameters into the editor — nothing is silently reset by
opening a rule to look at it. Saving replaces the rule's stored condition set with exactly what's in
the editor at that moment, so double-check the parameter list before saving if you were only meaning
to toggle the active flag or rename it.

### 7.2 From rule to enforcement — and back

A rule you save here is compiled centrally and distributed to every enrolled edge node as part of
the next verified rule bundle — this typically reaches an active node within seconds to minutes,
not immediately, and **not at all** to a node that isn't yet approved (§3, §8). Once live, every
transaction your edge node evaluates against that rule shows up in `triggeredRuleIds` in the raw
decision, and — for anything above ALLOW — as an Alert here in the control plane with the rule's
name and description as the reason (§5.1).

**Test before you tighten.** A rule that's too broad doesn't just create noise — every ALERT/HOLD/BLOCK
it produces becomes work for your investigators and, at BLOCK severity, an outcome your customer's
transaction actually experiences. Use the AI-assisted preview and a narrow rollout (adjust score
weight before hardening to BLOCK) rather than shipping a strict new rule straight to production.

### 7.3 Severity is a design decision, not a default

Choose a rule's action deliberately:

- **ALERT** — worth a human's attention, does not interrupt the transaction.
- **HOLD** — the transaction does not auto-approve; it's routed to review or step-up. Use this for
  patterns you're not yet confident enough about to outright block.
- **BLOCK** — declines the transaction outright. Reserve this for patterns you're confident enough
  about that you accept blocking a legitimate transaction occasionally to stop the illegitimate ones
  reliably.

Remember §3's severity-max rule: a BLOCK rule you add will out-rank every other rule on any
transaction it matches, however many ALLOW-leaning rules also apply. Author BLOCK rules narrowly.

---

## 8. Edge Nodes — approving and monitoring your own installations

`/edge-nodes` is the fleet view for every edge node enrolled under your PSP account.

### 8.1 Lifecycle

```
PENDING ──approve──► APPROVED ──first boot──► ACTIVE ──suspend──► SUSPENDED
   │                                              │
 reject                                        revoke
   ▼                                              ▼
REJECTED (terminal)                          REVOKED (terminal)
```

A node is requested here (which issues a one-time enrollment code, shown exactly once), approved by
a platform admin, then completes activation itself once it boots with that code — see
`docs/edge-client-install-guide.md` §6.2 for the installation-side half of this handshake.
**Only an ACTIVE node receives rule bundles and has its metrics accepted.** `SUSPENDED` and
`REVOKED` both stop distribution — suspension can be reversed by re-enrolling; revocation is
permanent and needs a fresh enrollment with new keys.

### 8.2 Fleet analytics

The node detail view and the fleet-wide analytics rollup show, per node or aggregated across your
whole PSP: total transactions evaluated, and the ALLOW/ALERT/HOLD/BLOCK split, over your chosen
lookback window (default 7 days). This is the aggregate view described in §1 — it is built entirely
from the counts your edge nodes push up, never from the transactions themselves.

**A node with an unusually high HOLD rate is very often a node problem, not a fraud problem.** Check
§3.1's three health signals before assuming your rules got stricter — an unapproved, suspended, or
feature-store-degraded node produces exactly this pattern.

---

## 9. Screening — sanctions and PEP

`/screening` shows the sanctions/PEP screening results for your customers and merchants, run against
watchlists synchronised centrally. A match here is a **case trigger on its own** (§6.3) — it doesn't
need a transaction to matter. PEP status (current, former, or a relative/close associate — `RCA`) is
tagged from the same screening pipeline and feeds into risk scoring, so a customer flagged here will
generally also show elevated risk on `/risk-analytics` and in any case opened against them.

---

## 10. Regulatory reporting

`/regulatory-reports` tracks SAR/CTR filings end to end — from the alert or case that generated the
obligation (`sarRequired`/`ctrRequired` on the alert, or a `CLOSED_SAR_FILED` case resolution)
through to filing status. A disposition of `TRUE_POSITIVE_SAR_FILED` on an alert, or a case resolved
`CLOSED_SAR_FILED`, is what should drive an entry appearing here — if you're filing outside this
flow, the two records will drift apart and your audit trail won't reconcile.

---

## 11. A worked example — following one threat through the whole loop

1. A card transacts three times within the same hour at your edge node. The third transaction's
   derived `pan_txn_count_1h` feature reads 2 (the two prior transactions, not itself — see §3), a
   velocity rule you configured matches, and the edge returns **ALERT**, score 30, with
   `triggeredRuleIds: [1002]` and a plain-language reason.
2. That decision appears in `/transaction-monitoring` (Live) within your normal ingestion latency,
   and simultaneously as a new row in `/alerts` — `severity: WARN`ish (ALERT-originated), `status:
   open`, `triggeredRules` naming your velocity rule.
3. An investigator opens the alert, checks the customer's transaction history, and finds it's a
   legitimate pattern (a merchant paying several small invoices back-to-back). They dispose it
   `CLEARED_KNOWN_PATTERN`. No case opens. Done — but the disposition is recorded, contributing
   accurately to your false-positive rate for that rule.
4. A month later, the same card trips the same rule, but this time alongside a sanctions match that
   just landed in `/screening`. The sanctions hit alone opens a **HIGH-priority case** (§6.3),
   independent of the velocity alert. The investigator links the earlier velocity alert to the new
   case from the case detail view — now the pattern reads very differently in context.
5. The case moves `NEW → ASSIGNED → IN_PROGRESS`, evidence and notes accumulate, and it's escalated
   to the MLRO given the sanctions dimension. The MLRO reviews, a SAR is filed, and the case closes
   `CLOSED_SAR_FILED`. That filing now needs to reconcile against an entry in `/regulatory-reports`
   (§10).
6. Nothing in steps 2–5 required touching the edge node — it made its one real-time decision in
   milliseconds and moved on. Everything after that is the control plane doing what it's for:
   giving your team the context a single transaction never has on its own.

---

## 12. Practical playbook

- **Triage by severity first, sanctions-origin second.** A CRITICAL/BLOCK-originated alert or any
  sanctions-triggered case jumps the queue regardless of what else is backed up.
- **Dispose honestly, not quickly.** `FALSE_POSITIVE` vs `CLEARED_LOW_RISK` vs
  `CLEARED_KNOWN_PATTERN` are different signals for tuning rules later — conflating them blinds you
  to which rules are actually working.
- **Check node health before blaming a rule.** An unapproved, suspended, or feature-store-degraded
  edge node produces symptoms (all-HOLD, or missing velocity detections) that look like a rules
  problem but are an installation problem — §3.1 and §8.2.
- **Tighten rules gradually.** ALERT → observe → HOLD → observe → BLOCK, not straight to BLOCK on an
  untested pattern, given severity-max means one bad BLOCK rule dominates every transaction it
  touches.
- **Cross-reference related cases.** Network/graph anomalies (§6.3) and repeated linked alerts across
  cases are frequently the first visible sign of a coordinated pattern, not isolated bad luck.
- **Keep the SAR/case/alert trail internally consistent.** A disposition or resolution that implies a
  filing should always have a corresponding entry in `/regulatory-reports` — that consistency is what
  an audit actually checks.

---

## 13. Related documents

- `docs/edge-client-install-guide.md` — installing and sizing your edge node
- `docs/edge-transaction-evaluation.md` — the real-time decision contract, for integrators
- `docs/edge-install-troubleshooting.md` — edge installation failure modes
