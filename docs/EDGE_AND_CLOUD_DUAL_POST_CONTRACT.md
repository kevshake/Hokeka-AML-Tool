# Edge + Cloud Dual-Post Integrator Contract

> **SUPERSEDED (2026-10-16)** by [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md).
> Do not build new integrations on anything this page used to describe.

This page is a stub. The dual-post model is retired. Under it, a PSP called `POST /edge/evaluate`
before authorisation and then sent the same transaction to the cloud with
`POST /api/v1/transactions/ingest`. The cloud stored the transaction and re-ran its own pipeline.

The owner decision of 2026-10-16 replaced that model:

| Then (dual-post) | Now (Edge data residency) | Status |
|---|---|---|
| PSP posts each transaction to the cloud (`/api/v1/transactions/ingest`) | PSP calls only `POST /edge/evaluate`. The Edge Node is the only evaluator | Edge evaluation: built |
| Cloud stores the transaction and raises alerts and cases from it | The Edge records the evaluation on its Postgres Edge Ledger and ships a **decision record v2** (ids, decision, score, rule ids; no PAN, amount or features) to `POST /api/v1/edge/assessments`. The cloud raises alerts and cases from the record, by transaction id | Built (R1, R7) |
| Console reads transactions from the cloud database | Console reads transaction details live from the PSP's Edge over the outbound Edge Tunnel | Cloud tunnel server built (R4); Edge tunnel client (R5), data proxy (R6) and Console live views (R11) planned |
| PSP passed `edgeAssessmentId` on ingest so the cloud adopted the Edge decision | `edgeAssessmentId` is the shared key of the ledger row and the decision record | Built |
| Edge posted the feature bag to `/api/v1/edge/decision` for AI advisory | No feature data leaves the Edge | Removed (R10c): no Edge client, no cloud endpoint |

**Still running until removal.** `POST /api/v1/transactions/ingest`, `POST /api/v1/transactions/ingest/batch`
and the v1 JSON `/api/v1/edge/assessments` envelope still work (`/api/v1/edge/decision` was removed in R10c). They are
legacy. Work package R12 deletes them after parity sign-off (planned). Existing cloud transaction
data is back-filled to each PSP's Edge and then purged (R15, then R12; planned).

**Where to look now:**

- Integration: [`PSP_API_GUIDE.md`](PSP_API_GUIDE.md) and [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md)
- Normative contract: [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md)
- Plan and work packages: [`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md)
- The full former text is in git history before this change.
