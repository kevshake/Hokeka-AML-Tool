# Transaction Monitoring

## Overview

The transaction API persists each PSP transaction, resolves its merchant and tenant, computes merchant and transaction risk, and invokes the fraud-detection orchestrator. The orchestrator extracts current features, runs model scoring and the complete rules layer, then persists the final decision and any alert before returning the response.

## Processing Flow

```text
POST /api/v1/transactions/ingest
  -> validate numeric platform merchant ID and resolve PSP
  -> validate positive amountCents and ISO-4217 currency
  -> hash PAN with SHA-256; raw PAN is never persisted
  -> persist tokenized customer account reference and encrypted customer email
  -> enrich from merchant, GeoIP, and BIN data
  -> persist transaction and transactions.raw outbox event atomically
  -> FraudDetectionOrchestrator
       -> FeatureExtractionService
       -> ScoringService
       -> RulesExecutionService
            -> regulatory Drools baseline, once per transaction
            -> enabled database SpEL rules
       -> DecisionEngine
            -> limits, sanctions, blacklist, rules, model thresholds
            -> persist decision, alert, and alerts.generated outbox event
            -> synchronously create or update a case when configured
  -> return decision, score, reasons, and triggered rules
```

The `transactions.raw` consumer updates Redis velocity features using the transaction ID as the sorted-set member. Kafka redelivery is therefore idempotent and cannot inflate velocity counts. It does not rescore the transaction, because scoring already occurs in the API path.

## Durable Events

Business rows and Kafka events are committed together through `event_outbox`. The dispatcher publishes pending events with bounded exponential retry. Active topics are:

| Topic | Owner | Purpose |
|---|---|---|
| `transactions.raw` | Transaction ingestion | Redis feature projection and Neo4j projection |
| `alerts.generated` | Decision engine | Idempotent reporting projection |
| `transactions.audit` | Audit service | Immutable audit stream |
| `aml.case.lifecycle` | Case service | Case notifications and downstream projections |
| `aml.case.decision` | Case decision service | Reporting and notifications |

The former `transactions.enriched`, `features.updates`, `aml.compliance.alert`, and `aml.transaction.alerts` scaffolding was removed because it had no complete owner and would duplicate the synchronous scoring or case path.

## Rules

The static regulatory Drools rules always run once per transaction. They cover CTR, structuring, high-risk country, ML thresholds, graph-risk signals, and velocity/volume rules. Enabled database SpEL rules run after the baseline and their outcomes are merged using the strongest decision. If DRL compilation is unavailable, the complete equivalent programmatic baseline is used.

## Graph Projection

When Neo4j is enabled, a separate Kafka consumer group projects committed raw transactions into Neo4j. It reloads the PostgreSQL transaction and merchant records, uses real legal name, trading name, MCC, country, risk, channel, amount, and decision values, and upserts nodes by stable IDs. Merchants within the same PSP are connected when the same hashed payment instrument appears across them, giving PageRank and community detection real tenant-safe relationships. Projection failures are rethrown for Kafka retry.

PostgreSQL remains the system of record. Neo4j is an investigation and analytics projection. Aerospike remains exclusively in the separately deployed `aml-microservice` and is accessed by the backend over its authenticated HTTP contract.

## Production Dependencies

`docker-compose.prod.yml` includes PostgreSQL, Kafka/Zookeeper, Redis, Neo4j, ClamAV, Aerospike, and the Aerospike-owning AML microservice on the private production network. The backend does not connect directly to Aerospike.

## Decision and Evidence Integrity

New transactions use `ALLOW`, `ALERT`, `HOLD`, and `BLOCK`; report queries also
recognize historical decision values. Scoring, sanctions, and enabled anomaly
model outages hold for review rather than silently allowing activity.

The transaction detail trail includes model inputs, returned explanation,
model version, score, latency, Drools and SpEL evidence, SAR/CTR flags, card and
channel classifications, and related alerts. Gateway authorization and
chargeback callbacks update the durable transaction and cannot weaken a
stronger prior decision.

## Ingestion Contract

Required fields are `merchantId`, `amountCents`, and `currency`. The optional
`pan` value is accepted only for immediate hashing. `customerAccountReference`
must be a PSP-issued opaque token rather than a raw account number, and
`customerEmail` is encrypted at rest. `channelType`, `countryCode`,
`acquirerResponse`, and these customer fields are retained as source evidence
for CBK endpoint 17; missing required source evidence causes that submission to
fail before any regulator call.
