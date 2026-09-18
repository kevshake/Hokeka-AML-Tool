# Aerospike Ownership

Aerospike is owned exclusively by `aml-microservice`. The main `BACKEND` has no
Aerospike dependency or client and reaches these datasets only through the
microservice's authenticated internal HTTP API.

## Runtime Data

- Namespace: `aml`
- Sanctions set: configured by `aerospike.sets.sanctions`
- Risk profile, velocity, device, IP reputation, and transaction-score cache
  sets are managed by the microservice cache endpoints.
- PostgreSQL remains the durable source of truth. Aerospike records are
  rebuildable low-latency projections and are not regulatory evidence alone.

## Deployment

Configure hosts, namespace, credentials, timeouts, and set names in
`src/main/resources/application.yml` or environment variables. Production
health checks must verify both the microservice HTTP endpoint and Aerospike.

The production Compose network exposes Aerospike only to `aml-microservice`;
`BACKEND` connects to `aml-microservice` instead.
