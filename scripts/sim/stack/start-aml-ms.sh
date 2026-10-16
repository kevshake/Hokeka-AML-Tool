#!/usr/bin/env bash
# Start aml-microservice (sanctions cache) against the hokeka-sim Aerospike (namespace aml_cache).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
RESULTS="$ROOT/scripts/sim/results"
mkdir -p "$RESULTS/logs"
set -a
# shellcheck disable=SC1091
source "$RESULTS/sim-secrets.env"
set +a
export AEROSPIKE_HOST=127.0.0.1
export AEROSPIKE_PORT=53000
export AEROSPIKE_NAMESPACE=aml_cache
exec java -Xmx384m -jar "$ROOT/aml-microservice/target/aml-microservice-1.0.0.jar" > "$RESULTS/logs/aml-ms.log" 2>&1
