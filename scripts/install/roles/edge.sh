#!/usr/bin/env bash
# Edge role: JRE 21, optional Rust toolchain, delegates to edge-host/deploy/install.sh.

# Run prerequisite checks for edge (container or native path).
hokeka_role_edge_check() {
  local repo mode
  repo="$(hokeka_repo_root)"
  mode="${HOKEKA_EDGE_MODE:-container}"
  hokeka_ensure_git
  if [ "$mode" = "container" ]; then
    hokeka_ensure_docker
  else
    hokeka_ensure_java21
    hokeka_mark_missing aerospike-ce
  fi
  if [ "${HOKEKA_EDGE_BUILD_RUST:-0}" = "1" ]; then
    hokeka_ensure_rust_toolchain "$repo" || true
  fi
  hokeka_report_missing
}

# Install Edge Node by reusing the canonical edge-host/deploy/install.sh with passthrough args.
hokeka_role_edge_install() {
  local repo edge_installer
  repo="$(hokeka_repo_root)"
  edge_installer="$repo/edge-host/deploy/install.sh"
  [ -f "$edge_installer" ] || hokeka_die "Missing edge installer at ${edge_installer}"

  hokeka_check_resources edge
  hokeka_ensure_git

  local mode="container"
  local passthrough=()
  local arg
  for arg in "$@"; do
    case "$arg" in
      --native) mode="native" ;;
    esac
    passthrough+=("$arg")
  done

  if [ "$mode" = "native" ]; then
    hokeka_ensure_java21
    if [ "${HOKEKA_EDGE_BUILD_RUST:-0}" = "1" ]; then
      hokeka_ensure_rust_toolchain "$repo"
      hokeka_log "Building edge-engine release library (HOKEKA_EDGE_BUILD_RUST=1)…"
      ( cd "$repo/edge-engine" && cargo build --release -p edge-jni )
      install -d -m 755 "$repo/edge-host/deploy"
      cp "$repo/edge-engine/target/release/libedge_engine.so" "$repo/edge-host/deploy/libedge_engine.so" 2>/dev/null || true
    fi
  else
    hokeka_ensure_docker
  fi

  hokeka_log "Delegating to edge-host/deploy/install.sh (canonical Edge installer)…"
  exec bash "$edge_installer" "${passthrough[@]}"
}
