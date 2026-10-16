#!/usr/bin/env bash
# Start the Control Plane (BACKEND jar, `production` profile) against the hokeka-sim data services.
# Prereqs: `docker compose -f scripts/sim/stack/docker-compose.sim.yml up -d`, `mvn -f BACKEND/pom.xml
# package -DskipTests`, and `py scripts/sim/stack/gen-secrets.py`.
#
# Deviations from a real production deploy (all deliberate, all sim-only):
#   * No nginx: the simulator sends `X-Forwarded-Proto: https` from loopback (a trusted proxy) so
#     security.require-ssl stays ON and is exercised exactly as behind nginx.
#   * RATE_LIMIT_RPM raised so volume ingest is not throttled (the auth limit is left at 10 RPM only
#     when SIM_KEEP_AUTH_RATE_LIMIT=1; default raises it too because the sim logs in many users).
#   * Cookies are Secure; the simulator manages cookies itself (it does not drop Secure cookies).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
RESULTS="$ROOT/scripts/sim/results"
mkdir -p "$RESULTS/logs"
set -a
# shellcheck disable=SC1091
source "$RESULTS/sim-secrets.env"
set +a

export SPRING_PROFILES_ACTIVE="${SIM_PROFILE:-production}"
export SERVER_PORT=2637
export DATABASE_URL="jdbc:postgresql://127.0.0.1:55433/fraud_detector_sim"
export DATABASE_USERNAME=hokeka_sim
export DATABASE_PASSWORD=hokeka_sim_pw
export AML_MICROSERVICE_URL="http://127.0.0.1:8091"
export AML_MICROSERVICE_ENABLED=true
export KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:59092"
export REDIS_HOST=127.0.0.1
export REDIS_PORT=56379
export NEO4J_ENABLED=true
export NEO4J_URI="bolt://127.0.0.1:57687"
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=hokeka_sim_neo4j
export CORS_ALLOWED_ORIGINS="http://localhost:5173"
export DOCUMENT_UPLOAD_DIR="$RESULTS/logs/documents"
export DOCUMENT_ANTIVIRUS_ENABLED=false
export DOCUMENT_ANTIVIRUS_REQUIRED=false
export ADVERSE_MEDIA_ENABLED=false
export LOG_FILE_PATH="$RESULTS/logs/control-plane-app.log"
export RATE_LIMIT_RPM="${RATE_LIMIT_RPM:-1000000}"
export RATE_LIMIT_BURST="${RATE_LIMIT_BURST:-100000}"
if [[ "${SIM_KEEP_AUTH_RATE_LIMIT:-0}" != "1" ]]; then export RATE_LIMIT_AUTH_RPM="${RATE_LIMIT_AUTH_RPM:-100000}"; fi
export LAYA_API_KEY=""            # AI layer must degrade gracefully with no key
export HOKEKA_AI_SHADOW_MODE=true
export LOGGING_LEVEL_COM_POSGATEWAY_AML="${SIM_APP_LOG_LEVEL:-INFO}"
if [[ -n "${EDGE_BUNDLE_SIGNING_KEY:-}" ]]; then export EDGE_BUNDLE_SIGNING_KEY EDGE_BUNDLE_SIGNING_PUBLIC_KEY EDGE_METRICS_INGEST_KEY EDGE_METRICS_INGEST_PUBLIC_KEY; fi

JAR="$ROOT/BACKEND/target/aml-fraud-detector-1.0.0-SNAPSHOT.jar"
cd "$RESULTS/logs"
exec java -Xms1g -Xmx3g -XX:+UseG1GC -jar "$JAR" > "$RESULTS/logs/control-plane.log" 2>&1
