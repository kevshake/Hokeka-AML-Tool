#!/usr/bin/env bash
# Shared detection, logging, retries, and package helpers for hokeka-install (sourced, not executed).
# All roles (control-plane, edge, console) source this library once.

# Emit a timestamped progress line to stdout for operator visibility.
hokeka_log() {
  printf '\033[0;36m[hokeka-install]\033[0m %s\n' "$*"
}

# Emit a success line with a checkmark prefix.
hokeka_ok() {
  printf '\033[0;32m  ✔\033[0m %s\n' "$*"
}

# Emit a non-fatal warning line.
hokeka_warn() {
  printf '\033[0;33m  !\033[0m %s\n' "$*"
}

# Print an error to stderr and exit with the given code (default 1).
hokeka_die() {
  printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2
  exit "${HOKEKA_DIE_CODE:-1}"
}

# Resolve the repository root from HOKEKA_REPO_ROOT or the install script layout.
hokeka_repo_root() {
  if [ -n "${HOKEKA_REPO_ROOT:-}" ]; then
    printf '%s' "$HOKEKA_REPO_ROOT"
    return 0
  fi
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
  if [ -f "$here/docker-compose.prod.yml" ]; then
    printf '%s' "$here"
    return 0
  fi
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  printf '%s' "$here"
}

# Apply standard proxy-related environment variables to curl/wget/docker when set.
hokeka_apply_proxy_env() {
  export HTTP_PROXY="${HTTP_PROXY:-${http_proxy:-}}"
  export HTTPS_PROXY="${HTTPS_PROXY:-${https_proxy:-}}"
  export NO_PROXY="${NO_PROXY:-${no_proxy:-localhost,127.0.0.1}}"
  export http_proxy="$HTTP_PROXY"
  export https_proxy="$HTTPS_PROXY"
  export no_proxy="$NO_PROXY"
}

# Run a command up to max_attempts times with exponential backoff between failures.
hokeka_retry() {
  local max_attempts="${1:-5}"
  shift
  local delay=2 attempt=1
  while [ "$attempt" -le "$max_attempts" ]; do
    if "$@"; then
      return 0
    fi
    if [ "$attempt" -eq "$max_attempts" ]; then
      return 1
    fi
    hokeka_warn "Command failed (attempt ${attempt}/${max_attempts}), retrying in ${delay}s: $*"
    sleep "$delay"
    delay=$(( delay * 2 ))
    [ "$delay" -gt 60 ] && delay=60
    attempt=$(( attempt + 1 ))
  done
  return 1
}

# Detect Linux distribution id and package manager into HOKEKA_DISTRO and HOKEKA_PKG.
hokeka_detect_platform() {
  HOKEKA_DISTRO="unknown"
  HOKEKA_PKG=""
  if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    HOKEKA_DISTRO="${ID:-unknown}"
  fi
  for m in apt-get dnf yum zypper pacman apk; do
    if command -v "$m" >/dev/null 2>&1; then
      HOKEKA_PKG="$m"
      break
    fi
  done
  HOKEKA_LIBC="glibc"
  if ldd --version 2>&1 | head -1 | grep -qi musl; then
    HOKEKA_LIBC="musl"
  fi
  hokeka_log "Platform: distro=${HOKEKA_DISTRO} pkg=${HOKEKA_PKG:-none} libc=${HOKEKA_LIBC} arch=$(uname -m)"
}

# Install distribution packages non-interactively using the detected package manager.
hokeka_pkg_install() {
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install packages: $*"; return 0; }
  case "$HOKEKA_PKG" in
    apt-get)
      DEBIAN_FRONTEND=noninteractive hokeka_retry 3 apt-get update -y
      DEBIAN_FRONTEND=noninteractive hokeka_retry 3 apt-get install -y "$@"
      ;;
    dnf) hokeka_retry 3 dnf install -y "$@" ;;
    yum) hokeka_retry 3 yum install -y "$@" ;;
    zypper) hokeka_retry 3 zypper --non-interactive install -y "$@" ;;
    pacman) hokeka_retry 3 pacman -Sy --noconfirm "$@" ;;
    apk) hokeka_retry 3 apk add --no-cache "$@" ;;
    *) hokeka_die "No supported package manager; install manually: $*" ;;
  esac
}

# Return 0 when the script runs with root privileges (required for package installation).
hokeka_require_root() {
  if [ "$HOKEKA_CHECK_ONLY" = "1" ] || [ "$HOKEKA_DRY_RUN" = "1" ]; then
    return 0
  fi
  [ "$(id -u)" -eq 0 ] || hokeka_die "Re-run as root (sudo) to install system packages and services."
}

# Read total installed memory in megabytes from /proc/meminfo or sysctl.
hokeka_mem_mb() {
  if [ -r /proc/meminfo ]; then
    awk '/MemTotal/ { printf "%d", $2/1024 }' /proc/meminfo
    return 0
  fi
  if command -v sysctl >/dev/null 2>&1; then
    sysctl -n hw.memsize 2>/dev/null | awk '{printf "%d", $1/1048576}'
    return 0
  fi
  printf '0'
}

# Read available disk space in gigabytes for the given mount path.
hokeka_disk_gb_free() {
  local path="${1:-.}"
  df -BG "$path" 2>/dev/null | awk 'NR==2 { gsub(/G/,"",$4); print $4 }' || printf '0'
}

# Validate CPU architecture and minimum RAM/disk for the selected install role.
hokeka_check_resources() {
  local role="$1"
  local min_ram_mb min_disk_gb
  min_ram_mb=4096
  min_disk_gb=30
  case "$role" in
    edge) min_ram_mb=8192; min_disk_gb=20 ;;
    console) min_ram_mb=4096; min_disk_gb=15 ;;
    control-plane) min_ram_mb=8192; min_disk_gb=40 ;;
  esac

  local arch ram disk
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64|aarch64|arm64) ;;
    *)
      hokeka_warn "Untested CPU architecture: ${arch}"
      ;;
  esac

  ram="$(hokeka_mem_mb)"
  disk="$(hokeka_disk_gb_free "${HOKEKA_INSTALL_DIR:-.}")"

  if [ "$ram" -gt 0 ] && [ "$ram" -lt "$min_ram_mb" ]; then
    local msg="RAM ${ram}MB is below recommended minimum ${min_ram_mb}MB for role ${role}"
    if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
      hokeka_warn "$msg"
    else
      hokeka_die "$msg (use --check to audit only, or increase RAM)"
    fi
  else
    hokeka_ok "Memory: ${ram}MB (minimum ${min_ram_mb}MB for ${role})"
  fi

  if [ "$disk" -gt 0 ] && [ "$disk" -lt "$min_disk_gb" ]; then
    local msg2="Free disk ${disk}GB is below minimum ${min_disk_gb}GB on ${HOKEKA_INSTALL_DIR:-.}"
    if [ "$HOKEKA_CHECK_ONLY" = "1" ]; then
      hokeka_warn "$msg2"
    else
      hokeka_die "$msg2"
    fi
  else
    hokeka_ok "Disk free: ${disk}GB (minimum ${min_disk_gb}GB)"
  fi
}

# Return 0 when command name exists on PATH.
hokeka_have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

# Record a missing prerequisite name in HOKEKA_MISSING_DEPS (space-separated list).
hokeka_mark_missing() {
  HOKEKA_MISSING_DEPS="${HOKEKA_MISSING_DEPS} $1"
}

# Return 0 when HOKEKA_MISSING_DEPS is empty after a dependency audit.
hokeka_missing_empty() {
  [ -z "${HOKEKA_MISSING_DEPS// /}" ]
}

# Print the aggregated missing dependency list and exit 1 when any are missing (check mode).
hokeka_report_missing() {
  if hokeka_missing_empty; then
    hokeka_ok "All checked prerequisites are present"
    return 0
  fi
  hokeka_warn "Missing prerequisites:${HOKEKA_MISSING_DEPS}"
  return 1
}

# Generate a random base64 secret without printing it to the terminal.
hokeka_random_b64() {
  local nbytes="${1:-32}"
  if hokeka_have_cmd openssl; then
    openssl rand -base64 "$nbytes" | tr -d '\n'
    return 0
  fi
  head -c "$((nbytes * 2))" /dev/urandom | base64 | tr -d '\n' | head -c "$((nbytes * 2))"
}

# Write KEY=value lines to a file with restrictive permissions, never echoing secret values.
hokeka_env_set() {
  local file="$1" key="$2" value="$3"
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would set ${key} in ${file}"; return 0; }
  install -d -m 700 "$(dirname "$file")"
  if [ -f "$file" ] && grep -q "^${key}=" "$file" 2>/dev/null; then
    return 0
  fi
  umask 077
  printf '%s=%s\n' "$key" "$value" >> "$file"
}

# Ensure git is installed when missing (used by all roles that track releases from git).
hokeka_ensure_git() {
  if hokeka_have_cmd git; then
    hokeka_ok "git present"
    return 0
  fi
  hokeka_mark_missing git
  [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
  hokeka_log "Installing git…"
  hokeka_pkg_install git
  hokeka_ok "git installed"
}
