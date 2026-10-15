#!/usr/bin/env bash
# Console / developer machine role: Node, Docker, Playwright browsers.

# Run prerequisite checks for local Console development.
hokeka_role_console_check() {
  local repo
  repo="$(hokeka_repo_root)"
  hokeka_ensure_git
  hokeka_node_lts_ok || hokeka_mark_missing node-lts
  hokeka_ensure_docker
  hokeka_ensure_playwright_browsers "$repo/FRONTEND"
  hokeka_report_missing
}

# Install Console development dependencies without starting services.
hokeka_role_console_install() {
  local repo frontend
  repo="$(hokeka_repo_root)"
  frontend="$repo/FRONTEND"
  HOKEKA_INSTALL_DIR="${HOKEKA_INSTALL_DIR:-$repo}"
  export HOKEKA_INSTALL_DIR

  hokeka_check_resources console
  hokeka_ensure_git
  hokeka_ensure_docker
  hokeka_ensure_node_lts
  hokeka_ensure_playwright_browsers "$frontend"

  if [ "$HOKEKA_DRY_RUN" != "1" ] && [ -f "$frontend/package.json" ]; then
    hokeka_log "Installing FRONTEND npm dependencies…"
    ( cd "$frontend" && npm ci )
    hokeka_ok "FRONTEND dependencies installed"
  fi

  hokeka_log "Console bootstrap complete. Start dev server: cd FRONTEND && npm run dev"
  hokeka_log "See docs/install/05-local-developer-stack.md"
}
