# Market Surveillance

> **Status:** Implemented | **Last updated:** July 2026

Market Surveillance is a separate market-integrity engine. It does not relabel securities AML scenarios as market abuse. Orders, executions, cancellations, market context, and `MARKET_ABUSE` signals are persisted independently and correlated through the common customer, alert, case, record-trail, and reporting layers.

## Workflows

The `/market-surveillance` workspace provides:

- PSP-scoped order and execution ingestion.
- Idempotency by PSP plus external order or execution ID.
- Order cancellation assessment with a mandatory reason.
- A market-signal work queue with retained evidence.
- Investigator outcomes: under review, escalated, or dismissed, with reviewer and notes.
- Links from orders and signals into the generic record trail.
- Common alerts for every detected market-abuse signal.

## Initial Scenarios

| Scenario | Evidence |
|---|---|
| `LAYERING_ORDER_PATTERN` | Same customer and instrument, same-side open orders, distinct price levels, total notional, ten-minute window |
| `RAPID_LARGE_ORDER_CANCELLATION` | Order lifetime, notional, fill ratio, cancellation reason |
| `POSSIBLE_WASH_TRADE` | Customer identity and beneficial-owner identity on both sides |
| `REPEATED_PREARRANGED_COUNTERPARTY` | Thirty-day execution count and execution identifiers for the same counterparty and instrument |
| `OFF_MARKET_EXECUTION` | Execution price, supplied reference price, and percentage deviation |
| `MARKING_THE_CLOSE` | Price deviation and minutes remaining to the supplied market close |

Thresholds are deployment configuration, not hard-coded legal conclusions:

```properties
market.surveillance.large-order-notional=50000
market.surveillance.rapid-cancellation-seconds=120
market.surveillance.off-market-deviation-percent=5
market.surveillance.close-window-minutes=5
```

## API

All endpoints derive PSP scope from the authenticated user.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/market-surveillance/orders` | Persist and assess an order |
| `POST` | `/api/v1/market-surveillance/orders/{externalOrderId}/cancel` | Cancel and assess an order |
| `POST` | `/api/v1/market-surveillance/executions` | Persist and assess an execution |
| `GET` | `/api/v1/market-surveillance/orders` | Page the PSP order ledger |
| `GET` | `/api/v1/market-surveillance/signals` | Page the PSP market-signal queue |
| `PUT` | `/api/v1/market-surveillance/signals/{id}/review` | Record an investigator outcome |

## Reports And Traceability

- `MKT_001` reports every market signal with customer, order, execution, evidence, and review outcome.
- `MKT_002` reports order activity with execution totals, signal counts, and maximum signal score.
- Report rows include typed source identifiers, so generated report executions link back to the source records.
- A customer trail includes market orders and market-abuse occurrences. A common alert links back to its source market signal.

## Regulatory Boundary

Market-abuse signals may be correlated with AML, sanctions, fraud, cyber, or crypto-exposure evidence in one investigation, but their typology and reporting treatment remain distinct. This follows the control separation discussed in the [FCA money laundering through markets review](https://www.fca.org.uk/publication/corporate/money-laundering-through-markets-review-january-2025.pdf).

