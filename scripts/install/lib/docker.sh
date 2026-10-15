#!/usr/bin/env bash
# Docker Engine, Compose v2, registry mirror, and offline image loading for hokeka-install.

# Return 0 when Docker CLI responds and the daemon is reachable.
hokeka_docker_ready() {
  hokeka_have_cmd docker && docker info >/dev/null 2>&1
}

# Configure /etc/docker/daemon.json with an optional registry mirror (idempotent merge).
hokeka_configure_docker_mirror() {
  local mirror="${HOKEKA_DOCKER_REGISTRY_MIRROR:-${DOCKER_REGISTRY_MIRROR:-}}"
  [ -n "$mirror" ] || return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would set Docker registry mirror: ${mirror}"; return 0; }
  install -d -m 755 /etc/docker
  local tmp
  tmp="$(mktemp)"
  if [ -f /etc/docker/daemon.json ]; then
    python3 - <<PY "$mirror" "$tmp"
import json, sys
mirror, out = sys.argv[1], sys.argv[2]
try:
    with open("/etc/docker/daemon.json") as f:
        data = json.load(f)
except Exception:
    data = {}
mirrors = data.get("registry-mirrors") or []
if mirror not in mirrors:
    mirrors.append(mirror)
data["registry-mirrors"] = mirrors
with open(out, "w") as f:
    json.dump(data, f, indent=2)
PY
  else
    printf '{"registry-mirrors":["%s"]}\n' "$mirror" > "$tmp"
  fi
  install -m 644 "$tmp" /etc/docker/daemon.json
  rm -f "$tmp"
  systemctl restart docker 2>/dev/null || true
  hokeka_ok "Docker registry mirror configured"
}

# Load every .tar/.tar.gz image archive from HOKEKA_DOCKER_IMAGE_TARBALLS_DIR into the local daemon.
hokeka_load_offline_images() {
  local dir="${HOKEKA_DOCKER_IMAGE_TARBALLS_DIR:-}"
  [ -n "$dir" ] && [ -d "$dir" ] || return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would load images from ${dir}"; return 0; }
  local f
  for f in "$dir"/*.tar "$dir"/*.tar.gz; do
    [ -f "$f" ] || continue
    hokeka_log "Loading offline image: $(basename "$f")"
    hokeka_retry 3 docker load -i "$f"
  done
}

# Install Docker Engine and the compose plugin when absent, using get.docker.com with retries.
hokeka_ensure_docker() {
  if hokeka_docker_ready; then
    hokeka_ok "Docker Engine ready"
    if docker compose version >/dev/null 2>&1; then
      hokeka_ok "Docker Compose v2 plugin present"
    else
      hokeka_mark_missing docker-compose-v2
      [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
      hokeka_log "Installing docker-compose-plugin…"
      case "$HOKEKA_PKG" in
        apt-get) hokeka_pkg_install docker-compose-plugin ;;
        dnf|yum) hokeka_pkg_install docker-compose-plugin ;;
        *) hokeka_warn "Install the Docker Compose v2 plugin manually" ;;
      esac
    fi
    hokeka_configure_docker_mirror
    hokeka_load_offline_images
    return 0
  fi

  hokeka_mark_missing docker
  [ "$HOKEKA_CHECK_ONLY" = "1" ] && return 0
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install Docker Engine"; return 0; }

  hokeka_log "Installing Docker Engine (official convenience script)…"
  hokeka_apply_proxy_env
  hokeka_retry 3 bash -c 'curl -fsSL https://get.docker.com | sh'
  systemctl enable --now docker 2>/dev/null || true
  hokeka_configure_docker_mirror
  hokeka_load_offline_images
  hokeka_docker_ready || hokeka_die "Docker installed but daemon is not reachable"
  hokeka_ok "Docker Engine installed"
}

# Pull an image with retries; honors mirror configuration on the daemon side.
hokeka_docker_pull() {
  local image="$1"
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would pull ${image}"; return 0; }
  hokeka_retry 5 docker pull "$image"
}
