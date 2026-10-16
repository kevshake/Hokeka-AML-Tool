#!/usr/bin/env bash
# Control Plane role: Docker stack, secrets, systemd, and health verification.

# Run prerequisite checks only for the control-plane role.
hokeka_role_control_plane_check() {
  hokeka_ensure_git
  hokeka_ensure_docker
  hokeka_node_lts_ok || hokeka_mark_missing node-lts
  hokeka_report_missing
}

# Install and start the production Control Plane stack under HOKEKA_INSTALL_DIR.
hokeka_role_control_plane_install() {
  local repo
  repo="$(hokeka_repo_root)"
  HOKEKA_INSTALL_DIR="${HOKEKA_INSTALL_DIR:-/opt/aml-fraud-detector}"
  export HOKEKA_INSTALL_DIR

  hokeka_check_resources control-plane
  hokeka_ensure_git
  hokeka_ensure_docker
  hokeka_ensure_node_lts
  hokeka_configure_public_firewall
  hokeka_write_control_plane_env "$HOKEKA_INSTALL_DIR"
  hokeka_compose_up_control_plane "$HOKEKA_INSTALL_DIR" "$repo"
  hokeka_install_control_plane_systemd "$HOKEKA_INSTALL_DIR"
  hokeka_verify_control_plane_health "$HOKEKA_INSTALL_DIR" || true
  hokeka_log "Control Plane bootstrap complete. Rotate any placeholder CORS origins before public traffic."
  hokeka_log "See docs/install/03-control-plane.md and docs/deploy/DEPLOYMENT-READINESS-RUNBOOK.md for verification."
}
