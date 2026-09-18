#!/usr/bin/env bash
# =============================================================================
# init-kafka-topics.sh
#
# One-shot Kafka topic provisioner for the Hokeka AML / fraud-detector stack.
#
# Why this exists:
#   docker-compose.infrastructure.yml runs the broker with
#   KAFKA_AUTO_CREATE_TOPICS_ENABLE=false, so every topic the backend produces
#   to or consumes from MUST be created explicitly before the backend boots.
#
# Run mode:
#   - Designed to run as the `kafka-topics-init` one-shot service inside the
#     `aml-network` docker network, mounted at /init-kafka-topics.sh.
#   - Idempotent: uses --if-not-exists, safe to rerun on every stack up.
#   - Exits 0 on success (so depends_on completion semantics are clean).
#
# Topic catalogue (canonical):
#   Drawn from
#     - source-code/BACKEND/src/main/java/com/posgateway/aml/config/KafkaConfig.java
#     - source-code/BACKEND/src/main/java/com/posgateway/aml/service/kafka/*
#     - architecture-v2/kafka/KafkaTopicsConfig.java
#     - architecture-v2/docs/ARCHITECTURE-V2.md  (Layer 1 ingestion table)
# =============================================================================

set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
WAIT_TIMEOUT_SECONDS="${KAFKA_WAIT_TIMEOUT_SECONDS:-180}"
WAIT_INTERVAL_SECONDS="${KAFKA_WAIT_INTERVAL_SECONDS:-3}"

# -----------------------------------------------------------------------------
# Logging + error trap
# -----------------------------------------------------------------------------
log()   { printf '[init-kafka-topics] %s\n' "$*"; }
warn()  { printf '[init-kafka-topics] WARN: %s\n' "$*" >&2; }
fatal() { printf '[init-kafka-topics] FATAL: %s\n' "$*" >&2; exit 1; }

on_error() {
    local exit_code=$?
    local line_no=${1:-?}
    warn "failed at line ${line_no} with exit code ${exit_code}"
    exit "${exit_code}"
}
trap 'on_error $LINENO' ERR

# -----------------------------------------------------------------------------
# Wait for kafka broker to be reachable
# -----------------------------------------------------------------------------
wait_for_broker() {
    log "waiting for broker at ${BOOTSTRAP} (timeout ${WAIT_TIMEOUT_SECONDS}s)"
    local elapsed=0
    until kafka-topics --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
        if (( elapsed >= WAIT_TIMEOUT_SECONDS )); then
            fatal "broker ${BOOTSTRAP} not reachable after ${WAIT_TIMEOUT_SECONDS}s"
        fi
        sleep "${WAIT_INTERVAL_SECONDS}"
        elapsed=$(( elapsed + WAIT_INTERVAL_SECONDS ))
    done
    log "broker ${BOOTSTRAP} reachable after ~${elapsed}s"
}

# -----------------------------------------------------------------------------
# Topic creation helper
#
# Args:
#   $1 topic name
#   $2 partition count
#   $3 replication factor
#   $4 retention.ms (optional, empty string = use broker default)
#   $5 cleanup.policy (optional, empty string = use broker default)
# -----------------------------------------------------------------------------
create_topic() {
    local name="$1"
    local partitions="$2"
    local rf="$3"
    local retention_ms="${4:-}"
    local cleanup_policy="${5:-}"

    local -a args=(
        --bootstrap-server "${BOOTSTRAP}"
        --create --if-not-exists
        --topic "${name}"
        --partitions "${partitions}"
        --replication-factor "${rf}"
    )
    if [[ -n "${retention_ms}" ]]; then
        args+=(--config "retention.ms=${retention_ms}")
    fi
    if [[ -n "${cleanup_policy}" ]]; then
        args+=(--config "cleanup.policy=${cleanup_policy}")
    fi

    log "ensuring topic: ${name} (partitions=${partitions}, rf=${rf}, retention.ms=${retention_ms:-default}, cleanup.policy=${cleanup_policy:-default})"
    kafka-topics "${args[@]}" >/dev/null
}

# -----------------------------------------------------------------------------
# Canonical topic list
#
# Format: "name|partitions|replication|retention.ms|cleanup.policy"
#   retention.ms / cleanup.policy may be empty (= broker default).
#
# Retention values come from architecture-v2/docs/ARCHITECTURE-V2.md
# Layer 1 ingestion table; partition counts come from the same table
# and from KafkaTopicsConfig.java. Replication factor 1 is mandatory in
# this single-broker compose (broker default min.insync.replicas=1).
#
# Constants (ms):
#   1 day      =          86_400_000
#   7 days     =         604_800_000
#   30 days    =       2_592_000_000
#   90 days    =       7_776_000_000
#   1 year     =      31_536_000_000
#   7 years    =     220_752_000_000
# -----------------------------------------------------------------------------
TOPICS=(
    # --- Legacy / current backend (KafkaConfig.java + service/kafka/*) -------
    "aml.case.lifecycle|3|1||delete"
    "aml.case.decision|3|1||delete"
    "aml.compliance.alert|3|1||delete"
    "aml.transaction.alerts|3|1||delete"

    # --- Architecture V2 ingestion (ARCHITECTURE-V2.md, Layer 1) ------------
    "transactions.raw|32|1|604800000|delete"
    "transactions.enriched|32|1|604800000|delete"
    "features.updates|32|1|86400000|delete"
    "transactions.decisions|32|1|2592000000|delete"
    "alerts.generated|16|1|7776000000|delete"
    "cases.events|8|1|31536000000|delete"
    "transactions.audit|32|1|220752000000|delete"
    "transactions.dlq|32|1|2592000000|delete"
)

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
main() {
    wait_for_broker

    log "provisioning ${#TOPICS[@]} topics"
    for spec in "${TOPICS[@]}"; do
        IFS='|' read -r name partitions rf retention_ms cleanup_policy <<<"${spec}"
        create_topic "${name}" "${partitions}" "${rf}" "${retention_ms}" "${cleanup_policy}"
    done

    # -------------------------------------------------------------------------
    # Final summary - describe every topic we manage
    # -------------------------------------------------------------------------
    log "======================================================================"
    log "Provisioning complete. Topic summary:"
    log "----------------------------------------------------------------------"
    printf '%-32s %-12s %-12s\n' "TOPIC" "PARTITIONS" "REPLICAS"
    for spec in "${TOPICS[@]}"; do
        IFS='|' read -r name _p _rf _r _c <<<"${spec}"
        # Pull live partition count + replica count from the broker so the
        # summary reflects reality (not just our intent).
        live=$(kafka-topics --bootstrap-server "${BOOTSTRAP}" \
                            --describe --topic "${name}" 2>/dev/null \
               | awk '/PartitionCount/ {
                        for (i=1;i<=NF;i++) {
                            if ($i ~ /PartitionCount:/) { split($i,a,":"); pc=a[2] }
                            if ($i ~ /ReplicationFactor:/) { split($i,a,":"); rf=a[2] }
                        }
                        print pc" "rf
                      }')
        if [[ -n "${live}" ]]; then
            printf '%-32s %-12s %-12s\n' "${name}" "${live% *}" "${live#* }"
        else
            printf '%-32s %-12s %-12s\n' "${name}" "?" "?"
        fi
    done
    log "----------------------------------------------------------------------"
    log "All required topics ensured. Exiting 0."
}

main "$@"
