# Decision and Model Evidence

## Canonical Outcomes

Every newly processed transaction uses one outcome vocabulary:

- `ALLOW` - no intervention required.
- `ALERT` - create compliance evidence and review in the alert workbench.
- `HOLD` - pause execution pending manual review.
- `BLOCK` - reject the transaction.

Historical decision values are normalized when read so existing data remains
reportable.

## Gateway Feedback

`POST /transaction/result` requires a source `transactionId`. It validates PSP
scope, merchant and amount when supplied, persists the ISO 8583 response and
chargeback classification, preserves the strongest existing decision, and
queues a durable `transactions.audit` outbox event.

VFMP and HECM metrics are calculated from the durable `transactions` and
`chargeback_disputes` tables.

## Model Operation

External scoring is opt-in through `SCORING_SERVICE_ENABLED=true` and
`SCORING_SERVICE_URL`. When enabled, an unavailable or invalid model response
produces `HOLD` with `ML_SCORING_UNAVAILABLE`; rules still run and may strengthen
the outcome to `BLOCK`.

The scoring response may supply `model_version`, `explanation`,
`feature_contributions`, and `feature_importance`. Hokeka stores exactly what
the model supplies and marks explanation status unavailable when absent. It
does not manufacture feature weights.

DL4J anomaly detection is disabled by default. Enabling it requires
`dl4j.model.path` to point to a trained 20-input/20-output autoencoder. Startup
fails if the model is missing or incompatible.

## Record Trail

`transaction_features.risk_details` stores model, rule, anomaly, and regulatory
evidence. Transaction detail includes input features, score, model version,
latency, rule evidence, SAR/CTR flags, card/channel classifications, alerts,
and a `TRANSACTION_SCORED` occurrence.

## Observability

OpenTelemetry request tracing is opt-in through `OTEL_TRACING_ENABLED`,
`OTEL_EXPORTER_OTLP_ENDPOINT`, and `OTEL_SERVICE_NAME`. Each API request creates
a server span and returns `X-Trace-Id` when the span context is active.
