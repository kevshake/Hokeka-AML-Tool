#!/usr/bin/env bash
# Java 21, Node.js LTS, and Rust toolchain helpers for hokeka-install roles.

# Return 0 when java on PATH reports version 21 or newer.
hokeka_java21_ok() {
  hokeka_have_cmd java || return 1
  local ver
  ver="$(java -version 2>&1 | head -1)"
  echo "$ver" | grep -Eq 'version "([2-9][1-9]|[2-9][0-9])' && return 0
  echo "$ver" | grep -Eq 'version "21' && return 0
  return 1
}

# Install Temurin JRE 21 via Adoptium repositories when Java 21+ is missing.
hokeka_ensure_java21() {
  if hokeka_java21_ok; then
    hokeka_ok "Java 21+ present"
    return 0
  fi
  hokeka_mark_missing java-21
  [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install Temurin JRE 21"; return 0; }

  hokeka_log "Installing Temurin JRE 21…"
  case "$HOKEKA_PKG" in
    apt-get)
      hokeka_pkg_install wget apt-transport-https gnupg ca-certificates
      wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
      # shellcheck disable=SC1091
      . /etc/os-release
      echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
        > /etc/apt/sources.list.d/adoptium.list
      hokeka_pkg_install temurin-21-jre
      ;;
    dnf|yum)
      cat > /etc/yum.repos.d/adoptium.repo <<'R'
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/rhel/$releasever/$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
R
      hokeka_pkg_install temurin-21-jre
      ;;
    apk)
      hokeka_pkg_install openjdk21-jre-headless
      ;;
    *)
      hokeka_warn "Install Temurin JRE 21 manually for ${HOKEKA_PKG}, then re-run"
      ;;
  esac
  hokeka_java21_ok || hokeka_die "Java 21+ still missing after install attempt"
  hokeka_ok "Java 21 installed"
}

# Return 0 when node on PATH is major version 20 or newer (LTS track).
hokeka_node_lts_ok() {
  hokeka_have_cmd node || return 1
  local major
  major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  [ "${major:-0}" -ge 20 ]
}

# Install Node.js 22 LTS via NodeSource on Debian/RHEL families when Node is missing or too old.
hokeka_ensure_node_lts() {
  if hokeka_node_lts_ok; then
    hokeka_ok "Node.js LTS present ($(node -v))"
    return 0
  fi
  hokeka_mark_missing node-lts
  [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install Node.js 22 LTS"; return 0; }

  hokeka_log "Installing Node.js 22 LTS…"
  case "$HOKEKA_PKG" in
    apt-get)
      hokeka_pkg_install ca-certificates curl gnupg
      curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
      hokeka_pkg_install nodejs
      ;;
    dnf|yum)
      curl -fsSL https://rpm.nodesource.com/setup_22.x | bash -
      hokeka_pkg_install nodejs
      ;;
    *)
      hokeka_warn "Install Node.js 20+ manually, then re-run"
      ;;
  esac
  hokeka_node_lts_ok || hokeka_die "Node.js LTS still missing after install attempt"
  hokeka_ok "Node.js installed ($(node -v))"
}

# Read the Rust channel pin from edge-engine/rust-toolchain.toml (defaults to 1.85.0).
hokeka_rust_pin() {
  local repo="$1"
  local file="$repo/edge-engine/rust-toolchain.toml"
  if [ -f "$file" ]; then
    sed -n 's/^channel = "\(.*\)"/\1/p' "$file" | head -1
    return 0
  fi
  printf '1.85.0'
}

# Install build dependencies and rustup with the pinned toolchain for edge-engine builds.
hokeka_ensure_rust_toolchain() {
  local repo="$1"
  local pin
  pin="$(hokeka_rust_pin "$repo")"
  if hokeka_have_cmd rustc && rustc --version | grep -q "$pin"; then
    hokeka_ok "Rust ${pin} present"
    return 0
  fi
  hokeka_mark_missing "rust-${pin}"
  [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install Rust ${pin}"; return 0; }

  hokeka_log "Installing Rust toolchain ${pin} (edge-engine pin)…"
  case "$HOKEKA_PKG" in
    apt-get) hokeka_pkg_install build-essential pkg-config curl ;;
    dnf|yum) hokeka_pkg_install gcc gcc-c++ make pkgconfig curl ;;
    apk) hokeka_pkg_install build-base curl ;;
  esac
  hokeka_apply_proxy_env
  hokeka_retry 3 bash -c 'curl --proto "=https" -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain '"$pin"' --component clippy,rustfmt'
  # shellcheck disable=SC1091
  [ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
  export PATH="${HOME}/.cargo/bin:${PATH}"
  rustc --version | grep -q "$pin" || hokeka_die "Rust ${pin} not active after rustup install"
  hokeka_ok "Rust ${pin} installed"
}

# Install Playwright browser binaries for the Console role when npx is available.
hokeka_ensure_playwright_browsers() {
  local frontend_dir="$1"
  [ -d "$frontend_dir" ] || return 0
  if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
    if [ -d "$HOME/.cache/ms-playwright" ] || [ -d "${frontend_dir}/node_modules/@playwright" ]; then
      hokeka_ok "Playwright browsers likely present"
    else
      hokeka_mark_missing playwright-browsers
    fi
    return 0
  fi
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install Playwright chromium"; return 0; }
  if [ ! -d "$frontend_dir/node_modules" ]; then
    hokeka_log "Running npm ci in FRONTEND (required before Playwright install)…"
    ( cd "$frontend_dir" && npm ci )
  fi
  ( cd "$frontend_dir" && npx playwright install chromium --with-deps )
  hokeka_ok "Playwright chromium installed"
}
