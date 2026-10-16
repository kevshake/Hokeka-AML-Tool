#!/usr/bin/env bash
# Production .env generation for docker-compose.prod.yml (secrets never printed).

# Create or merge /opt/aml-fraud-detector/.env with strong random secrets (idempotent for existing keys).
hokeka_write_control_plane_env() {
  local install_dir="$1"
  local env_file="$install_dir/.env"
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would ensure ${env_file} secrets"; return 0; }

  install -d -m 750 "$install_dir"
  umask 077
  touch "$env_file"
  chmod 600 "$env_file"

  local tmp_aml
  if ! grep -q '^AML_MS_INTERNAL_KEY=' "$env_file" 2>/dev/null; then
    tmp_aml="$(hokeka_random_b64 48)"
    printf 'AML_MS_INTERNAL_KEY=%s\nAML_INTERNAL_API_KEY=%s\n' "$tmp_aml" "$tmp_aml" >> "$env_file"
    unset tmp_aml
  fi

  if ! grep -q '^DATABASE_USERNAME=' "$env_file" 2>/dev/null; then
    printf 'DATABASE_USERNAME=%s\n' "hokeka_$(hokeka_random_b64 6 | tr '/+=' '_')" >> "$env_file"
  fi
  if ! grep -q '^DATABASE_PASSWORD=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "DATABASE_PASSWORD" "$(hokeka_random_b64 32)"
  fi
  if ! grep -q '^NEO4J_PASSWORD=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "NEO4J_PASSWORD" "$(hokeka_random_b64 32)"
  fi
  if ! grep -q '^AUDIT_HMAC_KEY=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "AUDIT_HMAC_KEY" "$(hokeka_random_b64 32)"
  fi
  if ! grep -q '^PII_LOOKUP_HMAC_KEY=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "PII_LOOKUP_HMAC_KEY" "$(hokeka_random_b64 32)"
  fi
  if ! grep -q '^JWT_SECRET=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "JWT_SECRET" "$(hokeka_random_b64 48)"
  fi
  if ! grep -q '^ENCRYPTION_KEY=' "$env_file" 2>/dev/null; then
    hokeka_env_set "$env_file" "ENCRYPTION_KEY" "$(hokeka_random_b64 32)"
  fi
  if ! grep -q '^CORS_ALLOWED_ORIGINS=' "$env_file" 2>/dev/null; then
    printf 'CORS_ALLOWED_ORIGINS=%s\n' "${CORS_ALLOWED_ORIGINS:-https://aml.hokeka.com,https://testaml.hokeka.com}" >> "$env_file"
  fi
  if ! grep -q '^KAFKA_BOOTSTRAP_SERVERS=' "$env_file" 2>/dev/null; then
    printf 'KAFKA_BOOTSTRAP_SERVERS=%s\n' "kafka-prod:29092" >> "$env_file"
  fi
  if ! grep -q '^SPRING_PROFILES_ACTIVE=' "$env_file" 2>/dev/null; then
    printf 'SPRING_PROFILES_ACTIVE=%s\n' "production" >> "$env_file"
  fi

  hokeka_ok "Wrote ${env_file} (mode 600; secret values not logged)"
}

# Open host firewall ports 80 and 443 when ufw or firewalld is active (idempotent).
hokeka_configure_public_firewall() {
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would open ports 80/443 on host firewall"; return 0; }
  if hokeka_have_cmd ufw; then
    ufw allow 80/tcp >/dev/null 2>&1 || true
    ufw allow 443/tcp >/dev/null 2>&1 || true
    hokeka_ok "ufw: allowed 80/tcp and 443/tcp (if ufw is enabled)"
  elif hokeka_have_cmd firewall-cmd; then
    firewall-cmd --permanent --add-service=http >/dev/null 2>&1 || true
    firewall-cmd --permanent --add-service=https >/dev/null 2>&1 || true
    firewall-cmd --reload >/dev/null 2>&1 || true
    hokeka_ok "firewalld: allowed http/https services"
  else
    hokeka_warn "No ufw/firewalld detected — open ports 80/443 at your cloud provider"
  fi
}

# Install a systemd unit that starts docker-compose.prod.yml on boot (idempotent).
hokeka_install_control_plane_systemd() {
  local install_dir="$1"
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would install hokeka-control-plane.service"; return 0; }
  if ! hokeka_have_cmd systemctl; then
    hokeka_warn "systemd not found — start stack manually: cd ${install_dir} && docker compose -f docker-compose.prod.yml up -d"
    return 0
  fi
  cat > /etc/systemd/system/hokeka-control-plane.service <<EOF
[Unit]
Description=Hokeka Control Plane (docker compose production stack)
After=docker.service network-online.target
Wants=network-online.target
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${install_dir}
EnvironmentFile=-${install_dir}/.env
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d --remove-orphans
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable hokeka-control-plane.service >/dev/null 2>&1 || true
  hokeka_ok "systemd unit enabled: hokeka-control-plane.service"
}

# Wait for backend-prod health endpoint on localhost:2637 after compose up.
hokeka_verify_control_plane_health() {
  local install_dir="$1"
  [ "$HOKEKA_DRY_RUN" = "1" ] && return 0
  hokeka_log "Waiting for Control Plane health (http://127.0.0.1:2637/actuator/health)…"
  local i
  for i in $(seq 1 60); do
    if curl -sf http://127.0.0.1:2637/actuator/health >/dev/null 2>&1; then
      hokeka_ok "Control Plane reports healthy"
      return 0
    fi
    sleep 5
  done
  hokeka_warn "Control Plane not healthy yet — inspect: cd ${install_dir} && docker compose -f docker-compose.prod.yml ps && docker compose logs backend-prod"
  return 1
}

# Bring up postgres first, then the full stack; Flyway runs when backend-prod starts.
hokeka_compose_up_control_plane() {
  local install_dir="$1"
  local repo="$2"
  [ "$HOKEKA_DRY_RUN" = "1" ] && { hokeka_log "[dry-run] would docker compose up in ${install_dir}"; return 0; }

  if [ "$install_dir" != "$repo" ] && [ ! -f "$install_dir/docker-compose.prod.yml" ]; then
    hokeka_log "Syncing compose project into ${install_dir}…"
    install -d -m 755 "$install_dir"
    rsync -a --delete \
      --exclude node_modules --exclude target --exclude .git \
      "$repo/" "$install_dir/"
  fi

  (
    cd "$install_dir" || exit 1
    set -a
    # shellcheck disable=SC1091
    [ -f .env ] && . ./.env
    set +a
    hokeka_log "Starting data services (postgres, kafka, neo4j, redis, clamav, aml-ms)…"
    docker compose -f docker-compose.prod.yml up -d postgres-prod zookeeper-prod kafka-prod redis-prod neo4j-prod clamav-prod aerospike-prod aml-ms-prod
    hokeka_log "Building and starting backend-prod and frontend-prod (Flyway migrates on backend boot)…"
    docker compose -f docker-compose.prod.yml up -d --build backend-prod frontend-prod website-prod
  )
  hokeka_ok "docker compose stack started (database migrations run via Flyway on backend startup)"
}
