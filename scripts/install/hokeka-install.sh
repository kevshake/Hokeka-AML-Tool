#!/usr/bin/env bash
#
# Hokeka first-run bootstrap — single entry point for Control Plane, Edge, and Console roles.
#
# Usage:
#   sudo ./scripts/install/hokeka-install.sh --role control-plane|edge|console [options] [-- edge-installer-args…]
#
# Options:
#   --check          Audit prerequisites only (exit 1 if anything missing)
#   --dry-run        Print planned actions without mutating the system
#   --yes            Non-interactive (default when stdin is not a TTY)
#   --install-dir    Target directory (default: /opt/aml-fraud-detector for control-plane, repo root otherwise)
#   --build-rust     Edge only: install pinned Rust toolchain and build edge-engine before native install
#
# Environment:
#   HOKEKA_DOCKER_REGISTRY_MIRROR / DOCKER_REGISTRY_MIRROR — Docker daemon registry mirror
#   HOKEKA_DOCKER_IMAGE_TARBALLS_DIR — directory of docker save tarballs to load before pulls
#   HTTP_PROXY / HTTPS_PROXY — honored for curl/rustup/docker
#
set -euo pipefail

HOKEKA_ROLE=""
HOKEKA_CHECK_ONLY=0
HOKEKA_DRY_RUN=0
HOKEKA_ASSUME_YES=0
HOKEKA_INSTALL_DIR=""
HOKEKA_EDGE_BUILD_RUST=0
HOKEKA_MISSING_DEPS=""
HOKEKA_EDGE_MODE="container"

# Resolve absolute path to this script's directory for sourcing libraries.
HOKEKA_INSTALL_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOKEKA_REPO_ROOT="$(cd "${HOKEKA_INSTALL_SCRIPT_DIR}/../.." && pwd)"
export HOKEKA_REPO_ROOT

# Source shared libraries (each file is safe to source multiple times).
# shellcheck source=lib/common.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/lib/common.sh"
# shellcheck source=lib/docker.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/lib/docker.sh"
# shellcheck source=lib/java-node-rust.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/lib/java-node-rust.sh"
# shellcheck source=lib/control-plane-env.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/lib/control-plane-env.sh"
# shellcheck source=roles/control-plane.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/roles/control-plane.sh"
# shellcheck source=roles/edge.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/roles/edge.sh"
# shellcheck source=roles/console.sh
source "${HOKEKA_INSTALL_SCRIPT_DIR}/roles/console.sh"

# Print usage text derived from the header comment in this script.
hokeka_usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

# Parse CLI flags and collect passthrough arguments after `--` for the Edge installer.
hokeka_parse_args() {
  local edge_args=()
  local parsing_edge=0
  while [ $# -gt 0 ]; do
    if [ "$parsing_edge" = "1" ]; then
      edge_args+=("$1")
      shift
      continue
    fi
    case "$1" in
      --role) HOKEKA_ROLE="$2"; shift 2 ;;
      --check) HOKEKA_CHECK_ONLY=1; shift ;;
      --dry-run) HOKEKA_DRY_RUN=1; shift ;;
      --yes|-y) HOKEKA_ASSUME_YES=1; shift ;;
      --install-dir) HOKEKA_INSTALL_DIR="$2"; shift 2 ;;
      --build-rust) HOKEKA_EDGE_BUILD_RUST=1; shift ;;
      --native) HOKEKA_EDGE_MODE="native"; edge_args+=("$1"); shift ;;
      --help|-h) hokeka_usage ;;
      --) parsing_edge=1; shift ;;
      *)
        if [ -n "$HOKEKA_ROLE" ] && [ "$HOKEKA_ROLE" = "edge" ]; then
          edge_args+=("$1")
          shift
        else
          hokeka_die "Unknown option: $1 (use --help)"
        fi
        ;;
    esac
  done
  HOKEKA_EDGE_PASSTHROUGH=("${edge_args[@]}")
}

# Confirm destructive installs when stdin is a TTY and --yes was not passed.
hokeka_confirm() {
  [ "$HOKEKA_CHECK_ONLY" = "1" ] || [ "$HOKEKA_DRY_RUN" = "1" ] && return 0
  [ "$HOKEKA_ASSUME_YES" = "1" ] && return 0
  if [ ! -t 0 ]; then
    HOKEKA_ASSUME_YES=1
    return 0
  fi
  hokeka_warn "This will install system packages and services for role: ${HOKEKA_ROLE}"
  printf 'Continue? [y/N] '
  local ans
  read -r ans
  case "$ans" in
    y|Y|yes|YES) ;;
    *) hokeka_die "Aborted by operator" ;;
  esac
}

# Dispatch check or install for the selected role.
hokeka_main() {
  hokeka_parse_args "$@"
  [ -n "$HOKEKA_ROLE" ] || hokeka_die "--role is required (control-plane|edge|console)"

  hokeka_apply_proxy_env
  hokeka_detect_platform
  hokeka_require_root

  case "$HOKEKA_ROLE" in
    control-plane)
      if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
        hokeka_role_control_plane_check
        exit $?
      fi
      hokeka_confirm
      hokeka_role_control_plane_install
      ;;
    edge)
      if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
        export HOKEKA_EDGE_BUILD_RUST
        hokeka_role_edge_check
        exit $?
      fi
      hokeka_confirm
      export HOKEKA_EDGE_BUILD_RUST
      hokeka_role_edge_install "${HOKEKA_EDGE_PASSTHROUGH[@]:-}"
      ;;
    console)
      if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
        hokeka_role_console_check
        exit $?
      fi
      hokeka_confirm
      hokeka_role_console_install
      ;;
    *)
      hokeka_die "Unknown role: ${HOKEKA_ROLE}"
      ;;
  esac
}

hokeka_main "$@"
