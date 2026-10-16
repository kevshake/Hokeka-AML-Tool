#!/usr/bin/env bash
# Edge deployment smoke: Rust workspace tests, Java edge-host tests, optional docker runtime.
# Exit 0 when compile/unit paths pass; exit 2 when skipped (no toolchain); exit 1 on failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "== edge-engine: cargo test (uses edge-engine/rust-toolchain.toml) =="
if ! command -v cargo >/dev/null 2>&1; then
  echo "SKIP: cargo not installed"
  exit 2
fi
(cd edge-engine && cargo test --workspace)

echo "== edge-host: mvn test (JNI optional) =="
(cd edge-host && mvn -q -B test -Dtest='!*TlsIntegration*')

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "== optional: aerospike + edge-host runtime (docker) =="
  echo "Run docs/install/README.md edge section with docker-compose when validating dual-post live."
else
  echo "NOTE: Docker unavailable — skipped live Aerospike + edge-host runtime (CI / VPS required)."
fi

echo "PASS edge-runtime-smoke (unit/interop paths)"
