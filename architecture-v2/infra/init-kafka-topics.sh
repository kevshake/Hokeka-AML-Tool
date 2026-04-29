#!/usr/bin/env bash
# Create Kafka topics for the AML Transaction microservice.
# Idempotent: --if-not-exists is used so reruns are safe.
#
# Usage:
#   ./architecture-v2/infra/init-kafka-topics.sh
#
# Requires the aml-kafka container to be healthy.

set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-aml-kafka}"
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"

create_topic() {
  local name="$1" partitions="$2" retention_ms="$3"
  echo "==> creating topic: $name (partitions=$partitions retention_ms=$retention_ms)"
  docker exec "$KAFKA_CONTAINER" kafka-topics \
    --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config "retention.ms=$retention_ms" \
    --config "compression.type=lz4"
}

# transactions.ingested : 12 partitions, 7-day retention  (keyed by psp_id)  HOK-76 contract
# aml.txn.scored        : 12 partitions, 7-day retention  (keyed by psp_id)
# aml.txn.decision      :  6 partitions, 30-day retention (keyed by txn_id)
# rules.updated         :  1 partition,  7-day retention  (single key 'rules' — global notifications)
create_topic "transactions.ingested" 12 $((7 * 24 * 3600 * 1000))
create_topic "aml.txn.scored"        12 $((7 * 24 * 3600 * 1000))
create_topic "aml.txn.decision"       6 $((30 * 24 * 3600 * 1000))
create_topic "rules.updated"          1 $((7 * 24 * 3600 * 1000))

echo "==> topics:"
docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$BOOTSTRAP" --list
