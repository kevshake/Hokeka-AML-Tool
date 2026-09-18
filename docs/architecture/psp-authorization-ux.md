# PSP authorization & edge setup — interface spec

Design-level contract for the authorization surfaces. Implemented in `FRONTEND/src/pages/EdgeNodes/`
against the endpoints in `docs/architecture/edge-channel-contract.md` §5 and the admin API.

## 1. Why this exists

Two authorization gates must be visible and operable:

1. **PSP-level** — a PSP account is `PENDING` until a platform admin approves it. Until then it must
   not be able to use the platform. (Today `Psp.isActive()` is never called, so this gate is not
   enforced — being fixed alongside this UI.)
2. **Node-level** — each on-prem edge node the PSP installs must be individually authorized before
   the control plane will send it rule bundles. A PSP with 4 sites has 4 separately-revocable nodes.

A node can never be more privileged than its PSP: if the PSP is not `ACTIVE`, none of its nodes may
reach `ACTIVE`.

## 2. Two audiences, one page

| | Platform admin (Hokeka) | PSP user |
|---|---|---|
| Scope | every node, every PSP | own nodes only |
| Can approve / reject | ✅ | ❌ |
| Can revoke | ✅ any | ✅ own |
| Can request a node | ✅ on behalf of | ✅ |
| Sees the enrollment code | once, at issue | once, at issue |

PSP scoping is enforced server-side (`PspIsolationService`), never by hiding UI.

## 3. Node lifecycle as the user sees it

```
  ①  Request node        PSP fills name + site  →  status PENDING
        ↓
  ②  Hokeka approves     admin reviews          →  status APPROVED
        ↓                                          one-time enrollment code revealed
  ③  Install on-prem     operator runs install.sh with the code
        ↓                                          node generates its keys, calls /edge/enroll
  ④  Node activates      keys pinned             →  status ACTIVE, bundles start flowing
        ↓
  ⑤  Suspend / Revoke    either side             →  distribution stops on next poll
```

### State presentation

| Status | Chip | Meaning shown to the user |
|--------|------|---------------------------|
| `PENDING` | muted / neutral | "Awaiting Hokeka approval" |
| `APPROVED` | gold | "Ready to install — code expires in {n}h" |
| `ACTIVE` | teal | "Healthy · last seen {relative}" |
| `SUSPENDED` | amber | "Paused — not receiving rules" |
| `REVOKED` | red | "Revoked — re-enrollment required" |

Colours come from the shared tokens (gold/teal/amber), never hardcoded per page.

## 4. The setup wizard (step ②→③)

Shown once, immediately after approval. Four steps:

1. **Node identity** — display name, site/region, expected hostname.
2. **Authorization** — reveals the one-time enrollment code with a copy button and an explicit
   warning: *"This code is shown once and expires in 24 hours. It authorizes one node."* Re-opening
   the wizard must NOT re-reveal it — the server only stores a hash.
3. **Install command** — a copy-ready, platform-switched command:
   - Linux: `sudo ./install.sh --pspid {psp} --edgeid {edge} --enrollment-code {code} --controlplane {url}`
   - Windows: `.\install.ps1 -PspId {psp} -EdgeId {edge} -EnrollmentCode {code} -ControlPlane {url}`
   With a link to the PDF install guide and the prerequisite/sizing table.
4. **Waiting for activation** — polls node status; flips to a success state the moment the node
   completes activation, showing the pinned key fingerprint for the operator to verify out-of-band.

## 5. Node detail

- **Channel panel** — the point of the whole exercise. Shows, per node: TLS version negotiated,
  mutual-auth status, HSE-1 envelope context, pinned key fingerprints (control-plane Ed25519 and the
  node's X25519), last successful bundle pull, current bundle version, last metrics upload. Anything
  not verifiably encrypted must render as an explicit error state, never as a neutral blank.
- **Rule bundle** — active version, delivered-at, rule count, diff vs the catalog.
- **Metrics** — aggregate only: decision counts, rule-hit counts, p50/p95/p99. The panel must state
  plainly that no transaction data leaves the PSP premises, because that is the product promise.
- **Danger zone** — suspend / revoke, with a typed-confirmation for revoke (irreversible).

## 6. Non-negotiables

- Revoke is irreversible and must say so before the click, not after.
- The enrollment code is never logged, never in a URL, never re-displayable.
- A key-pinning mismatch on activation surfaces as a **security alert**, not a generic failure — it
  means someone presented different keys for a known edge id.
- Every state must be reachable and legible in the dark editorial theme; no white-on-white chips.
