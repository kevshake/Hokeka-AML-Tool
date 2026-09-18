# Durable Event Pipeline

## Guarantees

The platform uses a transactional outbox for transaction, alert, audit, and case events. The producer writes the business record and `event_outbox` row in the same PostgreSQL transaction. A scheduled dispatcher locks ready rows with `FOR UPDATE SKIP LOCKED`, waits for Kafka acknowledgement, records publication evidence, and retries failures with capped exponential backoff.

Kafka producers use `acks=all` and idempotent producer mode. Consumers use intrinsic record IDs for idempotent projections: transaction IDs are Redis sorted-set members, and reporting records alert/case event receipts in PostgreSQL before incrementing monthly metrics in the same transaction.

## Ownership

There is one live scoring path. The transaction controller invokes the fraud orchestrator directly; the raw Kafka consumer only maintains projections. Alert-to-case escalation remains synchronous and configuration-driven, so Kafka redelivery cannot create duplicate compliance cases.

Neo4j consumes `transactions.raw` in its own group and reloads source-of-record data from PostgreSQL. It builds same-PSP merchant relationships from shared hashed instruments for graph analytics. Reporting consumes `alerts.generated` and `aml.case.decision`. Audit events are retained as a separate Kafka stream while signed PostgreSQL audit rows remain authoritative.

## Operations

Pending outbox rows contain attempt count, next attempt time, and the latest error. Published rows contain the Kafka acknowledgement time. Published outbox history can be retained for operational evidence while signed audit data follows the seven-year regulatory retention policy.
