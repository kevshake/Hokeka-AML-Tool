#!/usr/bin/env bash
#
# Hokeka Edge Engine — cross-distro Linux installer.
#
# Installs every prerequisite (container runtime OR native Java + Aerospike), brings up the edge and
# its feature store, secures the database so ONLY the edge app can reach it, and generates the TLS
# keystore the transaction API terminates on (the edge refuses to start without one).
#
#   Default (recommended): containerised, distro-agnostic, DB isolated on a private network.
#     sudo ./install.sh --pspid acme --edgeid acme-eu-1 --controlplane https://edge.hokeka.com \
#          --enrollment-code ABCD-1234
#
#   Native (no containers): installs Temurin JRE 25 + Aerospike CE, binds the DB to 127.0.0.1,
#   firewalls port 3000, installs a systemd service.
#     sudo ./install.sh --native --pspid acme --edgeid acme-eu-1
#
#   One-line bootstrap (fetches the app + native core from Hokeka's package server, verifies every
#   artifact against SHA256SUMS, installs the systemd service):
#     curl -fsSL https://packages.hokeka.com/edge/install.sh | sudo bash -s -- #          --native --pspid 42 --edgeid acme-eu-1 --enrollment-code ABCD-1234
#
#   Air-gapped: mirror the repo and pass --repo-url, or drop the artifacts beside this script and
#   pass --offline. Pin a build with --version 1.2.3; track pre-releases with --channel beta.
#
#   Retention + sizing: --retention-days N (default 90) drives BOTH the edge's retention and the
#   Aerospike namespace default-ttl, so they cannot drift. --peak-tps N sizes the data file from
#   throughput × retention (with 30% headroom) and reports the RAM the primary index will need;
#   --data-file-size 256G overrides it explicitly. Undersizing means silent eviction of history.
#
#   Artifact trust: SHA256SUMS is GPG-signed by the Hokeka release key, whose public half is pinned
#   in this script. The signature is verified BEFORE any digest is trusted, so a tampered origin or
#   mirror is detected rather than merely a corrupted download. --insecure-skip-signature disables
#   that check and should only ever be used against a mirror you already trust by other means.
#
set -euo pipefail

# ── defaults ─────────────────────────────────────────────────────────────────────────────────────
MODE="container"
EDGE_IMAGE="registry.hokeka.com/hokeka/edge-engine:0.1.0"
BIND="127.0.0.1"           # host interface the transaction API binds to
PSPID="acme"
EDGEID="acme-1"
CONTROLPLANE="https://edge.hokeka.com"
DATA_DIR="/opt/hokeka"
# Where release artifacts are fetched from. Overridable for air-gapped/mirrored estates.
REPO_URL="${HOKEKA_REPO_URL:-https://packages.hokeka.com/edge}"
CHANNEL="stable"           # stable | beta — selects which manifest is fetched
VERSION=""                 # pin an exact version; empty = whatever the channel manifest names
OFFLINE=0                  # 1 = never touch the network, use artifacts beside the installer
ALLOW_SUBNET=""            # e.g. 10.0.0.0/24 — restrict the 8443 API to your PSP subnet
ENROLL_CODE=""             # operator-supplied, single-use, 24h TTL — needed for first-boot activation
TLS_CLIENT_AUTH="none"     # none | want | need (mutual TLS from your PSP API nodes)
TLS_PASS=""                # filled by ensure_tls_cert
SKIP_SIGNATURE=0           # 1 = do not verify SHA256SUMS.asc (see --insecure-skip-signature)
# ── retention + sizing ───────────────────────────────────────────────────────────────────────────
# RETENTION_DAYS is the single source of truth: it drives BOTH the namespace `default-ttl` and the
# edge's EDGE_RETENTION_DAYS. They must never be set independently — see aerospike/aerospike.conf.
RETENTION_DAYS=90
PEAK_TPS=""                # optional: compute the data file size from throughput × retention
DATA_FILE_SIZE=""          # explicit override, e.g. 256G
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── pinned release signing key ───────────────────────────────────────────────────────────────────
# The trust anchor for the whole distribution chain. SHA256SUMS is only meaningful if we know who
# wrote it: an attacker who can rewrite an artifact on the origin or a mirror can rewrite the digest
# beside it just as easily. The detached signature is what makes tampering detectable, and it is
# only worth anything against a key pinned HERE, in the script, rather than fetched alongside.
#
# Paste the armored public half of the Hokeka release key between the markers, and set the
# fingerprint to match what the release workflow asserts (RELEASE_GPG_FINGERPRINT).
HOKEKA_SIGNING_FINGERPRINT=""
HOKEKA_SIGNING_KEY="" # -----BEGIN PGP PUBLIC KEY BLOCK----- ... -----END PGP PUBLIC KEY BLOCK-----

log()  { printf '\033[0;36m[hokeka]\033[0m %s\n' "$*"; }
ok()   { printf '\033[0;32m  ✔\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m  ! \033[0m%s\n' "$*"; }
die()  { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

usage() { grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

# ── args ─────────────────────────────────────────────────────────────────────────────────────────
while [ $# -gt 0 ]; do
  case "$1" in
    --native) MODE="native" ;;
    --repo-url) REPO_URL="$2"; shift ;;
    --channel) CHANNEL="$2"; shift ;;
    --version) VERSION="$2"; shift ;;
    --offline) OFFLINE=1 ;;
    --edge-image) EDGE_IMAGE="$2"; shift ;;
    --bind) BIND="$2"; shift ;;
    --pspid) PSPID="$2"; shift ;;
    --edgeid) EDGEID="$2"; shift ;;
    --controlplane) CONTROLPLANE="$2"; shift ;;
    --data-dir) DATA_DIR="$2"; shift ;;
    --allow-subnet) ALLOW_SUBNET="$2"; shift ;;
    --enrollment-code) ENROLL_CODE="$2"; shift ;;
    --tls-client-auth) TLS_CLIENT_AUTH="$2"; shift ;;
    --insecure-skip-signature) SKIP_SIGNATURE=1 ;;
    --retention-days) RETENTION_DAYS="$2"; shift ;;
    --peak-tps) PEAK_TPS="$2"; shift ;;
    --data-file-size) DATA_FILE_SIZE="$2"; shift ;;
    -h|--help) usage ;;
    *) die "unknown option: $1 (use --help)" ;;
  esac
  shift
done

[ "$(id -u)" -eq 0 ] || die "please run as root (sudo)."

# ── retention + capacity sizing ──────────────────────────────────────────────────────────────────
# Two numbers decide whether this node keeps the history its rules depend on:
#
#   default-ttl  how long a transaction is kept
#   filesize     how much room there is to keep them in
#
# If filesize is too small for the retention, Aerospike EVICTS once the file fills. Nothing errors;
# velocity history just quietly shrinks and velocity rules weaken. That silence is why this is
# computed and checked here rather than left to a comment in a config file.
#
# Bytes per stored transaction record, used for the estimate. Derived from the bins the edge writes
# (pan_hash, amount, ts, merchant, currency, country, mcc, decision, score, rule ids) plus
# Aerospike's per-record overhead, rounded up to a whole write block. This is an ESTIMATE — validate
# against real volumes during a pilot before committing to a tier.
BYTES_PER_TXN=512
# Aerospike keeps the primary index in RAM at a fixed 64 bytes per record.
INDEX_BYTES_PER_RECORD=64

validate_sizing() {
  case "$RETENTION_DAYS" in
    ''|*[!0-9]*) die "--retention-days must be a whole number of days (got '$RETENTION_DAYS')" ;;
  esac
  [ "$RETENTION_DAYS" -ge 1 ] && [ "$RETENTION_DAYS" -le 3650 ] \
    || die "--retention-days must be between 1 and 3650 (got $RETENTION_DAYS)"

  if [ -n "$PEAK_TPS" ]; then
    case "$PEAK_TPS" in
      ''|*[!0-9]*) die "--peak-tps must be a whole number (got '$PEAK_TPS')" ;;
    esac
    [ "$PEAK_TPS" -ge 1 ] || die "--peak-tps must be at least 1"
  fi

  # Explicit size wins; otherwise derive from throughput; otherwise keep the shipped default.
  if [ -n "$DATA_FILE_SIZE" ]; then
    printf '%s' "$DATA_FILE_SIZE" | grep -Eq '^[0-9]+[GT]$' \
      || die "--data-file-size must look like 128G or 2T (got '$DATA_FILE_SIZE')"
    return
  fi

  [ -n "$PEAK_TPS" ] || return   # nothing to compute from; the shipped default stands

  # records = tps × seconds/day × days; bytes = records × per-record; +30% headroom, min 16G.
  local records bytes gib
  records=$(( PEAK_TPS * 86400 * RETENTION_DAYS ))
  bytes=$(( records * BYTES_PER_TXN ))
  gib=$(( bytes / 1073741824 ))
  gib=$(( gib * 13 / 10 ))
  [ "$gib" -lt 16 ] && gib=16
  DATA_FILE_SIZE="${gib}G"

  local index_gib
  index_gib=$(( records * INDEX_BYTES_PER_RECORD / 1073741824 ))
  log "Sizing for ${PEAK_TPS} TPS × ${RETENTION_DAYS}d: ~$(( records / 1000000 ))M records"
  log "  data file : ${DATA_FILE_SIZE} (incl. 30% headroom)"
  log "  index RAM : ~${index_gib} GB — Aerospike holds the primary index in MEMORY."
  if [ "$index_gib" -ge 4 ]; then
    warn "This node needs roughly ${index_gib} GB of RAM for the Aerospike index ALONE, before the"
    echo  "  JVM, Aerospike overhead and the OS. Confirm the host has it, or reduce --retention-days."
  fi
}

validate_sizing

# Write RETENTION_DAYS and (when known) the data file size into the namespace config, so the
# database and the edge cannot drift apart. Idempotent: re-running replaces the same two directives.
apply_aerospike_sizing() {
  local conf="$1"
  [ -f "$conf" ] || { warn "aerospike config not found at $conf — skipping sizing"; return 0; }
  sed -i -E "s/^([[:space:]]*)default-ttl[[:space:]]+[0-9]+d.*/\\1default-ttl ${RETENTION_DAYS}d        # managed by install.sh --retention-days/" "$conf"
  if [ -n "$DATA_FILE_SIZE" ]; then
    sed -i -E "s/^([[:space:]]*)filesize[[:space:]]+[0-9]+[GT].*/\\1filesize ${DATA_FILE_SIZE}      # managed by install.sh --data-file-size \\/ --peak-tps/" "$conf"
  fi
  ok "Aerospike namespace: default-ttl ${RETENTION_DAYS}d, filesize $(grep -Eo 'filesize[[:space:]]+[0-9]+[GT]' "$conf" | awk '{print $2}')"
}

# ── detect distro + package manager ──────────────────────────────────────────────────────────────
# Which C library the host runs. This decides whether the NATIVE path is viable at all: the Rust
# core we publish for Linux is built against glibc, and a glibc .so cannot be loaded on a musl
# system. Container mode is unaffected — the engine runs inside a glibc image there.
detect_libc() {
  LIBC="glibc"
  if [ -n "$(find /lib /usr/lib -maxdepth 1 -name 'ld-musl-*.so.1' 2>/dev/null | head -1)" ]; then
    LIBC="musl"
  elif ldd --version 2>&1 | head -1 | grep -qi musl; then
    LIBC="musl"
  elif [ "${DISTRO}" = "alpine" ]; then
    LIBC="musl"
  fi
}

detect_platform() {
  DISTRO="unknown"; PKG=""
  if [ -r /etc/os-release ]; then . /etc/os-release; DISTRO="${ID:-unknown}"; fi
  for m in apt-get dnf yum zypper pacman apk; do
    if command -v "$m" >/dev/null 2>&1; then PKG="$m"; break; fi
  done
  detect_libc
  log "Detected distro: ${DISTRO} · package manager: ${PKG:-none} · libc: ${LIBC}"

  # Refuse --native on musl rather than installing something that cannot work. Previously this
  # combination proceeded to the end and produced a node that either failed to start or silently
  # served every transaction on the SLOW Java interpreter — a degradation with no error anywhere in
  # the install output, on a box making payment decisions.
  # A musl host needs the musl-linked core; a glibc .so cannot load there at all. That artifact is
  # published for x86_64 only, so musl on ARM still has nothing to run natively.
  if [ "$MODE" = "native" ] && [ "$LIBC" = "musl" ] && [ "$(uname -m)" != "x86_64" ] \
     && [ "$(uname -m)" != "amd64" ]; then
    die "--native is not supported on musl with $(uname -m) (detected: ${DISTRO}).

       The musl-linked native core is published for x86_64 only, and a glibc library cannot load
       on musl. Use container mode here — the engine runs inside a glibc image:

           sudo ./install.sh --pspid ${PSPID} --edgeid ${EDGEID} \\
                --controlplane ${CONTROLPLANE} --enrollment-code <code>"
  fi
}

pkg_install() {
  case "$PKG" in
    apt-get) DEBIAN_FRONTEND=noninteractive apt-get update -y && apt-get install -y "$@" ;;
    dnf)     dnf install -y "$@" ;;
    yum)     yum install -y "$@" ;;
    zypper)  zypper --non-interactive install -y "$@" ;;
    pacman)  pacman -Sy --noconfirm "$@" ;;
    apk)     apk add --no-cache "$@" ;;
    *) die "unsupported package manager; install these manually: $*" ;;
  esac
}

# ── container runtime ────────────────────────────────────────────────────────────────────────────
ensure_container_runtime() {
  if command -v docker >/dev/null 2>&1; then RUNTIME="docker"; COMPOSE="docker compose"; ok "docker present"; return; fi
  if command -v podman >/dev/null 2>&1; then RUNTIME="podman"; COMPOSE="podman compose"; ok "podman present"; return; fi
  log "No container runtime found — installing Docker via the official convenience script…"
  if curl -fsSL https://get.docker.com | sh; then
    systemctl enable --now docker || true
    RUNTIME="docker"; COMPOSE="docker compose"; ok "docker installed"
  else
    die "could not install Docker automatically. Install docker or podman, then re-run — or use --native."
  fi
}

# ── TLS material for the transaction API ─────────────────────────────────────────────────────────
# The edge terminates TLS itself and REFUSES to start without a keystore (no plaintext fallback).
# We generate a self-signed PKCS#12 so a fresh install is encrypted from the first request; replace
# it with a certificate from your own CA before going live (see the note printed at the end).
random_password() {
  local pw=""
  if command -v openssl >/dev/null 2>&1; then
    pw="$(openssl rand -base64 48 2>/dev/null | LC_ALL=C tr -dc 'A-Za-z0-9' | head -c 32)"
  fi
  if [ "${#pw}" -lt 32 ]; then
    pw="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)"
  fi
  printf '%s' "$pw"
}

ensure_tls_cert() {
  local store="$HERE/secrets/edge-tls.p12"
  local passfile="$HERE/secrets/edge-tls.pass"

  if [ -s "$store" ] && [ -s "$passfile" ]; then
    TLS_PASS="$(cat "$passfile")"
    ok "TLS keystore already present ($store) — leaving it untouched"
    return
  fi

  TLS_PASS="$(random_password)"
  local dname="CN=hokeka-edge,O=Hokeka Edge,OU=${PSPID}"
  local san="SAN=dns:localhost,ip:127.0.0.1"
  [ "$BIND" = "127.0.0.1" ] || san="${san},ip:${BIND}"

  log "Generating a self-signed TLS keystore for the transaction API…"
  if command -v keytool >/dev/null 2>&1; then
    keytool -genkeypair -alias edge -keyalg EC -groupname secp384r1 -sigalg SHA384withECDSA \
      -validity 825 -storetype PKCS12 -keystore "$store" -storepass "$TLS_PASS" -keypass "$TLS_PASS" \
      -dname "$dname" -ext "$san" >/dev/null 2>&1 || die "keytool failed to create $store"
  elif command -v openssl >/dev/null 2>&1; then
    local tmp; tmp="$(mktemp -d)"
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 -sha384 -days 825 -nodes \
      -keyout "$tmp/key.pem" -out "$tmp/cert.pem" -subj "/CN=hokeka-edge/O=Hokeka Edge/OU=${PSPID}" \
      -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" >/dev/null 2>&1 \
      || die "openssl failed to create the edge certificate"
    openssl pkcs12 -export -name edge -inkey "$tmp/key.pem" -in "$tmp/cert.pem" \
      -out "$store" -passout "pass:${TLS_PASS}" >/dev/null 2>&1 || die "openssl failed to build $store"
    rm -rf "$tmp"
  elif [ -n "${RUNTIME:-}" ]; then
    # No JDK/openssl on the host — borrow keytool from the edge image itself.
    $RUNTIME run --rm --entrypoint keytool -v "$HERE/secrets:/secrets" "$EDGE_IMAGE" \
      -genkeypair -alias edge -keyalg EC -groupname secp384r1 -sigalg SHA384withECDSA \
      -validity 825 -storetype PKCS12 -keystore /secrets/edge-tls.p12 \
      -storepass "$TLS_PASS" -keypass "$TLS_PASS" -dname "$dname" -ext "$san" >/dev/null 2>&1 \
      || die "could not generate the TLS keystore with keytool from ${EDGE_IMAGE}"
  else
    die "no keytool, openssl or container runtime available to generate the TLS keystore."
  fi

  printf '%s' "$TLS_PASS" > "$passfile"
  chmod 600 "$store" "$passfile"
  ok "TLS keystore created (self-signed, P-384, 825 days) — mode 600"
}

# ── firewall the local transaction API (optional) ────────────────────────────────────────────────
firewall_api() {
  [ -n "$ALLOW_SUBNET" ] || { warn "API bound to ${BIND}; pass --allow-subnet to add a host firewall rule"; return; }
  if command -v ufw >/dev/null 2>&1; then
    ufw allow from "$ALLOW_SUBNET" to any port 8443 proto tcp && ufw deny 8443/tcp && ok "ufw: 8443 limited to ${ALLOW_SUBNET}"
  elif command -v firewall-cmd >/dev/null 2>&1; then
    firewall-cmd --permanent --add-rich-rule="rule family=ipv4 source address=${ALLOW_SUBNET} port port=8443 protocol=tcp accept" \
      && firewall-cmd --reload && ok "firewalld: 8443 limited to ${ALLOW_SUBNET}"
  else
    warn "no ufw/firewalld found; restrict port 8443 to ${ALLOW_SUBNET} at your network layer"
  fi
}

# ── container install (default) ──────────────────────────────────────────────────────────────────
install_container() {
  ensure_container_runtime
  install -d -m 700 "$HERE/config" "$HERE/secrets" "$HERE/data"
  ok "created config/secrets/data (mode 700)"
  ensure_tls_cert
  # Keep the namespace config and the edge's retention in lockstep before the stack comes up.
  apply_aerospike_sizing "$HERE/aerospike/aerospike.conf"

  cat > "$HERE/.env" <<EOF
EDGE_IMAGE=${EDGE_IMAGE}
EDGE_BIND=${BIND}
EDGE_PSPID=${PSPID}
EDGE_EDGEID=${EDGEID}
CONTROLPLANE_URL=${CONTROLPLANE}
AEROSPIKE_TAG=6.4
# Retention for raw transactions. MUST match default-ttl in aerospike/aerospike.conf — both are
# written from --retention-days. Changing one alone breaks capacity planning silently.
EDGE_RETENTION_DAYS=${RETENTION_DAYS}
# TLS on the transaction API — the edge refuses to start without these.
EDGE_TLS_KEYSTORE_PASSWORD=${TLS_PASS}
EDGE_TLS_KEY_ALIAS=edge
EDGE_TLS_CLIENT_AUTH=${TLS_CLIENT_AUTH}
# First-boot activation + pinned control-plane keys (from portal.hokeka.com).
EDGE_ENROLLMENT_CODE=${ENROLL_CODE}
CONTROLPLANE_ED25519_PUBLIC_KEY=
CONTROLPLANE_X25519_PUBLIC_KEY=
# mTLS client identity issued to this edge (drop the PKCS#12 into secrets/ and set the password).
EDGE_MTLS_KEYSTORE=/opt/hokeka/secrets/edge-client.p12
EDGE_MTLS_KEYSTORE_PASSWORD=
# Optional: custom trust anchors for the control plane. Empty = the JDK default trust store.
EDGE_MTLS_TRUSTSTORE=
EDGE_MTLS_TRUSTSTORE_PASSWORD=
EOF
  chmod 600 "$HERE/.env"
  ok "wrote .env (mode 600)"

  log "Starting the isolated stack (Aerospike has NO published port — reachable only by the edge)…"
  ( cd "$HERE" && $COMPOSE up -d )
  firewall_api

  log "Waiting for health…"
  for i in $(seq 1 30); do
    if $RUNTIME exec hokeka-edge curl -fsk https://localhost:8443/actuator/health >/dev/null 2>&1; then
      ok "edge is HEALTHY"; break
    fi
    sleep 3
    [ "$i" = 30 ] && warn "edge not healthy yet — check: $COMPOSE logs edge"
  done

  print_summary "container"
}


# ── artifact distribution ────────────────────────────────────────────────────────────────────────
# Artifacts are pulled from Hokeka's package server rather than expected to sit beside the script,
# so a client can install with a single command. Layout on the server:
#
#   $REPO_URL/$CHANNEL.json                          -> {"version":"1.2.3"}
#   $REPO_URL/<version>/SHA256SUMS                   -> checksums for every artifact
#   $REPO_URL/<version>/edge-host.jar
#   $REPO_URL/<version>/libedge_engine-linux-<arch>.so
#
# EVERY downloaded file is checksum-verified against SHA256SUMS before it is installed. An artifact
# that does not match is deleted and the install aborts — a truncated proxy response or a tampered
# mirror must never end up executing on a client's server.
ARCH=""
RESOLVED_VERSION=""
DOWNLOAD_DIR=""

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64)  ARCH="x86_64" ;;
    aarch64|arm64) ARCH="aarch64" ;;
    *) die "unsupported CPU architecture: $(uname -m) (supported: x86_64, aarch64)" ;;
  esac
}

fetch() { # fetch <url> <dest>
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 --retry-delay 2 --connect-timeout 15 -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --tries=3 --timeout=15 -O "$2" "$1"
  else
    die "neither curl nor wget is available — install one, or use --offline with local artifacts"
  fi
}

resolve_version() {
  if [ -n "$VERSION" ]; then RESOLVED_VERSION="$VERSION"; return; fi
  local tmp; tmp="$(mktemp)"
  fetch "$REPO_URL/$CHANNEL.json" "$tmp"     || die "could not reach $REPO_URL/$CHANNEL.json — check connectivity, or use --offline / --version"
  # Deliberately no jq dependency: the manifest is a tiny, fixed-shape document.
  RESOLVED_VERSION="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*//p' "$tmp" | head -1)"
  rm -f "$tmp"
  [ -n "$RESOLVED_VERSION" ] || die "could not parse a version from the $CHANNEL manifest"
}

# Verify the detached signature over SHA256SUMS using the pinned public key.
#
# This must run BEFORE any digest from that file is trusted — otherwise we would be authenticating
# artifacts against a manifest of unknown provenance, which is no protection against a compromised
# origin at all.
#
# Fail-closed by design: an unset pinned key, a missing gpg, a missing .asc or a bad signature all
# abort the install. Skipping requires the operator to type --insecure-skip-signature.
verify_signature() { # verify_signature <path-to-SHA256SUMS>
  local sums="$1" sig="$1.asc" gnupg_home

  if [ "$SKIP_SIGNATURE" = "1" ]; then
    warn "SIGNATURE VERIFICATION DISABLED (--insecure-skip-signature)."
    warn "Checksums still detect corruption, but NOT a tampered origin or mirror. Do not use this"
    warn "against packages.hokeka.com; it exists for internal mirrors you already trust."
    return 0
  fi

  [ -n "$HOKEKA_SIGNING_KEY" ] || die \
"this installer has no pinned release signing key, so it cannot verify the authenticity of what it
       downloads. This build of install.sh is not fit to install from a public mirror. Obtain an
       official installer from Hokeka, or — only if you already trust this artifact source by other
       means — re-run with --insecure-skip-signature."

  command -v gpg >/dev/null 2>&1 || pkg_install gnupg || true
  command -v gpg >/dev/null 2>&1 || die "gpg is required to verify the release signature — install gnupg and re-run"

  fetch "$REPO_URL/$RESOLVED_VERSION/SHA256SUMS.asc" "$sig" \
    || die "could not fetch SHA256SUMS.asc for $RESOLVED_VERSION — refusing to install unverified artifacts"

  # Throwaway keyring: never touch the machine's real GnuPG trust state.
  gnupg_home="$(mktemp -d)"
  chmod 700 "$gnupg_home"

  if ! printf '%s' "$HOKEKA_SIGNING_KEY" | gpg --homedir "$gnupg_home" --batch --quiet --import 2>/dev/null; then
    rm -rf "$gnupg_home"; die "the pinned signing key is malformed — refusing to continue"
  fi

  # Guard against a wrong-but-valid key being pasted in: the fingerprint must match too.
  if [ -n "$HOKEKA_SIGNING_FINGERPRINT" ]; then
    if ! gpg --homedir "$gnupg_home" --list-keys --with-colons 2>/dev/null \
         | awk -F: '/^fpr:/ {print $10}' | grep -qx "$HOKEKA_SIGNING_FINGERPRINT"; then
      rm -rf "$gnupg_home"
      die "the pinned key does not match the pinned fingerprint $HOKEKA_SIGNING_FINGERPRINT — refusing to continue"
    fi
  fi

  if ! gpg --homedir "$gnupg_home" --batch --quiet --verify "$sig" "$sums" 2>/dev/null; then
    rm -rf "$gnupg_home"
    rm -f "$sums" "$sig"
    die "SHA256SUMS FAILED SIGNATURE VERIFICATION for $RESOLVED_VERSION.
       The checksum manifest was not signed by the Hokeka release key. Someone may be tampering with
       your connection, the mirror, or the package server. NOTHING has been installed."
  fi

  rm -rf "$gnupg_home"
  ok "SHA256SUMS signature verified against the pinned Hokeka release key"
}

# Download one artifact and verify it against the release SHA256SUMS.
fetch_verified() { # fetch_verified <filename>
  local name="$1" dest="$DOWNLOAD_DIR/$1"
  fetch "$REPO_URL/$RESOLVED_VERSION/$name" "$dest" || die "download failed: $name"
  local expected actual
  expected="$(awk -v f="$name" '$2 == f || $2 == "*"f {print $1}' "$DOWNLOAD_DIR/SHA256SUMS" | head -1)"
  [ -n "$expected" ] || { rm -f "$dest"; die "$name is not listed in SHA256SUMS — refusing to install it"; }
  actual="$(sha256sum "$dest" | awk '{print $1}')"
  if [ "$expected" != "$actual" ]; then
    rm -f "$dest"
    die "checksum MISMATCH for $name (expected $expected, got $actual) — aborting"
  fi
  ok "verified $name"
}

# Populate $DOWNLOAD_DIR with the jar + native core for this machine.
download_artifacts() {
  DOWNLOAD_DIR="$(mktemp -d)"
  if [ "$OFFLINE" = "1" ]; then
    log "Offline mode — using artifacts beside the installer"
    cp "$HERE"/edge-host*.jar "$DOWNLOAD_DIR/edge-host.jar" 2>/dev/null || true
    cp "$HERE"/libedge_engine*.so "$DOWNLOAD_DIR/libedge_engine.so" 2>/dev/null || true
    return
  fi
  detect_arch
  resolve_version
  log "Installing Hokeka Edge ${RESOLVED_VERSION} (${CHANNEL}, ${ARCH}) from ${REPO_URL}"
  fetch "$REPO_URL/$RESOLVED_VERSION/SHA256SUMS" "$DOWNLOAD_DIR/SHA256SUMS"     || die "could not fetch SHA256SUMS for $RESOLVED_VERSION"
  # Authenticate the manifest before trusting a single digest inside it.
  verify_signature "$DOWNLOAD_DIR/SHA256SUMS"
  fetch_verified "edge-host.jar"
  # Pick the core matching this host's C library, not just its CPU. A glibc build cannot load on
  # musl and vice versa, and the failure is a silent fallback to the slow Java interpreter.
  local core="libedge_engine-linux-${ARCH}.so"
  [ "$LIBC" = "musl" ] && core="libedge_engine-linux-${ARCH}-musl.so"
  fetch_verified "$core"
  mv "$DOWNLOAD_DIR/$core" "$DOWNLOAD_DIR/libedge_engine.so"
}

cleanup_downloads() { [ -n "$DOWNLOAD_DIR" ] && rm -rf "$DOWNLOAD_DIR"; }
trap cleanup_downloads EXIT

# ── native install (--native) ────────────────────────────────────────────────────────────────────
install_native() {
  warn "Native mode: on Windows/macOS use install.ps1 / containers — this path is Linux-only."
  command -v curl >/dev/null 2>&1 || pkg_install curl
  download_artifacts
  command -v java >/dev/null 2>&1 || install_temurin25
  install_aerospike_native
  harden_aerospike_native
  install_edge_native
  print_summary "native"
}

install_temurin25() {
  log "Installing Temurin JRE 25…"
  case "$PKG" in
    apt-get)
      pkg_install wget apt-transport-https gnupg
      wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
      . /etc/os-release
      echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
        > /etc/apt/sources.list.d/adoptium.list
      pkg_install temurin-25-jre ;;
    dnf|yum)
      cat > /etc/yum.repos.d/adoptium.repo <<'R'
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/rhel/$releasever/$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
R
      pkg_install temurin-25-jre ;;
    apk)
      # Adoptium publishes no apk repository. Alpine's own OpenJDK 21 satisfies the floor — the
      # engine needs 21+ for virtual threads — and is musl-linked, which is the point on Alpine.
      log "Adoptium has no apk repo; using Alpine's OpenJDK 21 (meets the Java 21+ floor)."
      pkg_install openjdk21-jre-headless ;;
    *) warn "install a JRE 25 (Temurin) for ${PKG} manually, then re-run" ;;
  esac
  if command -v java >/dev/null 2>&1; then
    ok "Java: $(java -version 2>&1 | head -1)"
  else
    die "no Java runtime on PATH after installation — install a JRE 21+ manually and re-run"
  fi
}

install_aerospike_native() {
  log "Installing Aerospike Community Edition…"
  local tmp; tmp="$(mktemp -d)"
  case "$PKG" in
    apt-get) curl -fsSL "https://enterprise.aerospike.com/enterprise/download/server/latest/artifact/ubuntu22_amd64" -o "$tmp/aero.tgz" || \
             warn "adjust the Aerospike download URL for your distro (see aerospike.com/download)";;
    *) warn "download Aerospike CE for ${DISTRO} from aerospike.com/download and install, then re-run";;
  esac
  install -d -m 750 -o root "$DATA_DIR/aerospike/data" 2>/dev/null || true
  install -m 640 "$HERE/aerospike/aerospike.conf" /etc/aerospike/aerospike.conf 2>/dev/null || \
    { install -d /etc/aerospike; install -m 640 "$HERE/aerospike/aerospike.conf" /etc/aerospike/aerospike.conf; }
  # Apply retention/sizing to the INSTALLED copy, so the namespace matches EDGE_RETENTION_DAYS.
  apply_aerospike_sizing /etc/aerospike/aerospike.conf
  ok "Aerospike config staged"
}

harden_aerospike_native() {
  log "Hardening the database to app-only access…"
  # 1) Bind Aerospike to loopback so nothing off-box can connect.
  sed -i 's/address any\b.*/address 127.0.0.1   # hardened: loopback only/' /etc/aerospike/aerospike.conf || true
  # 2) Firewall port 3000 to localhost only, regardless of bind.
  if command -v ufw >/dev/null 2>&1; then
    ufw deny 3000/tcp && ok "ufw: port 3000 denied from the network (localhost still works)"
  elif command -v firewall-cmd >/dev/null 2>&1; then
    firewall-cmd --permanent --remove-port=3000/tcp 2>/dev/null || true; firewall-cmd --reload || true
    ok "firewalld: port 3000 not exposed"
  elif command -v iptables >/dev/null 2>&1; then
    iptables -A INPUT -p tcp --dport 3000 ! -s 127.0.0.1 -j DROP && ok "iptables: 3000 dropped except localhost"
  else
    warn "no firewall tool found — ensure port 3000 is blocked from the network"
  fi
  systemctl enable --now aerospike 2>/dev/null || warn "start Aerospike once installed: systemctl start aerospike"
  warn "Aerospike CE has no DB auth. For DB-level user/password + TLS, use Aerospike ENTERPRISE and enable the security{} stanza."
}

# Stage the Rust native core next to the host so the JVM can load it.
# System.loadLibrary("edge_engine") resolves the platform filename automatically
# (libedge_engine.so here, edge_engine.dll on Windows), so the only job is putting the right
# artifact on java.library.path. Optional: without it the host runs on its Java interpreter
# fallback, which is functionally equivalent but slower.
install_native_core() {
  local lib="libedge_engine.so" src=""
  install -d -m 700 "$DATA_DIR/lib"
  [ -n "$DOWNLOAD_DIR" ] && [ -f "$DOWNLOAD_DIR/$lib" ] && src="$DOWNLOAD_DIR/$lib"
  [ -z "$src" ] && [ -f "$HERE/$lib" ] && src="$HERE/$lib"
  if [ -n "$src" ]; then
    install -m 644 "$src" "$DATA_DIR/lib/$lib"
    log "Staged the native core: $DATA_DIR/lib/$lib"
  else
    warn "$lib not found next to the installer — the edge will run on the Java interpreter fallback."
    warn "Download it from the CI artifact 'libedge_engine-linux-x86_64' and re-run, or place it in $DATA_DIR/lib."
  fi
}

install_edge_native() {
  log "Installing the edge host (tarball)…"
  install -d -m 700 "$DATA_DIR"/{config,secrets,bin,data,lib}
  install_native_core
  install -d -m 700 "$HERE/secrets"
  ensure_tls_cert
  install -m 600 "$HERE/secrets/edge-tls.p12" "$DATA_DIR/secrets/edge-tls.p12"
  cat > "$DATA_DIR/config/edge.env" <<EOF
EDGE_BIND_ADDRESS=${BIND}
EDGE_TLS_KEYSTORE=${DATA_DIR}/secrets/edge-tls.p12
EDGE_TLS_KEYSTORE_PASSWORD=${TLS_PASS}
EDGE_TLS_KEY_ALIAS=edge
EDGE_TLS_CLIENT_AUTH=${TLS_CLIENT_AUTH}
EDGE_PSPID=${PSPID}
EDGE_EDGEID=${EDGEID}
CONTROLPLANE_URL=${CONTROLPLANE}
EDGE_ENROLLMENT_CODE=${ENROLL_CODE}
CONTROLPLANE_ED25519_PUBLIC_KEY=
CONTROLPLANE_X25519_PUBLIC_KEY=
EDGE_MTLS_KEYSTORE=${DATA_DIR}/secrets/edge-client.p12
EDGE_MTLS_KEYSTORE_PASSWORD=
EDGE_IDENTITY_FILE=${DATA_DIR}/data/edge-identity.json
# MUST match default-ttl in /etc/aerospike/aerospike.conf — both written from --retention-days.
EDGE_RETENTION_DAYS=${RETENTION_DAYS}
EOF
  chmod 600 "$DATA_DIR/config/edge.env"
  ok "TLS keystore + ${DATA_DIR}/config/edge.env staged (mode 600)"
  install_edge_jar
  write_systemd_unit
}

# The Spring Boot fat jar is the runnable artifact in native mode. Ship it beside the installer
# (or drop it into $DATA_DIR/bin) — mirrors how the native core .so is staged.
install_edge_jar() {
  local jar=""
  [ -n "$DOWNLOAD_DIR" ] && [ -f "$DOWNLOAD_DIR/edge-host.jar" ] && jar="$DOWNLOAD_DIR/edge-host.jar"
  [ -z "$jar" ] && jar=$(ls -1 "$HERE"/edge-host*.jar 2>/dev/null | head -1 || true)
  if [ -n "$jar" ]; then
    install -m 640 "$jar" "$DATA_DIR/bin/edge-host.jar"
    ok "Staged the edge host: $DATA_DIR/bin/edge-host.jar"
  elif [ -f "$DATA_DIR/bin/edge-host.jar" ]; then
    ok "Edge host jar already present: $DATA_DIR/bin/edge-host.jar"
  else
    warn "edge-host jar not found next to the installer — place it at $DATA_DIR/bin/edge-host.jar,"
    warn "then: systemctl restart hokeka-edge"
  fi
}

# Install a real systemd unit so the engine actually runs (and restarts) on the client's server.
# Previously the installer only PRINTED "systemctl enable --now hokeka-edge" without ever writing a
# unit, so native mode staged config and produced nothing runnable.
write_systemd_unit() {
  if ! command -v systemctl >/dev/null 2>&1; then
    warn "systemd not present — start the edge manually:"
    warn "  java -Djava.library.path=$DATA_DIR/lib -jar $DATA_DIR/bin/edge-host.jar"
    return
  fi
  id -u hokeka >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin hokeka || true
  chown -R hokeka:hokeka "$DATA_DIR" 2>/dev/null || true

  cat > /etc/systemd/system/hokeka-edge.service <<EOF
[Unit]
Description=Hokeka Edge Engine (on-premises AML rule evaluation)
After=network-online.target aerospike.service
Wants=network-online.target

[Service]
Type=simple
User=hokeka
Group=hokeka
EnvironmentFile=${DATA_DIR}/config/edge.env
# -Djava.library.path lets the JVM find the Rust native core (libedge_engine.so).
ExecStart=/usr/bin/java -Djava.library.path=${DATA_DIR}/lib --enable-native-access=ALL-UNNAMED -jar ${DATA_DIR}/bin/edge-host.jar
Restart=always
RestartSec=5
# Hardening: the engine needs no privileges beyond its own data directory.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=${DATA_DIR}

[Install]
WantedBy=multi-user.target
EOF
  chmod 644 /etc/systemd/system/hokeka-edge.service
  systemctl daemon-reload
  systemctl enable hokeka-edge >/dev/null 2>&1 || true
  if [ -f "$DATA_DIR/bin/edge-host.jar" ]; then
    systemctl restart hokeka-edge || warn "could not start hokeka-edge — check: journalctl -u hokeka-edge -n 50"
    ok "systemd service installed and started: hokeka-edge"
  else
    ok "systemd service installed (enabled): hokeka-edge — starts once the jar is in place"
  fi
}

print_summary() {
  echo
  log "──────────── Installation summary (${1}) ────────────"
  ok  "Edge transaction API : https://${BIND}:8443  (PSP API nodes only)"
  ok  "Database access      : Aerospike is reachable ONLY by the edge app"
  if [ "$1" = "container" ]; then
    echo "                         (private Docker network, no published port)"
  else
    echo "                         (bound to 127.0.0.1 + firewalled on port 3000)"
  fi
  ok  "Control plane        : ${CONTROLPLANE}  (outbound only, mTLS + HSE-1)"
  ok  "Transport security   : TLS 1.3 only, self-signed keystore at secrets/edge-tls.p12"
  echo
  warn "The generated certificate is SELF-SIGNED. Before production, replace it with one from your"
  echo  "  own CA (same alias 'edge'), keeping the same path and updating EDGE_TLS_KEYSTORE_PASSWORD:"
  echo  "    keytool -importkeystore -srckeystore your-ca-issued.p12 -srcstoretype PKCS12 \\"
  echo  "            -destkeystore secrets/edge-tls.p12 -deststoretype PKCS12 -alias edge"
  echo  "  The edge refuses to start if the keystore is missing — it never falls back to plain HTTP."
  echo
  if [ -z "$ENROLL_CODE" ]; then
    warn "No --enrollment-code given: this node stays UNAUTHORIZED and will only return HOLD."
  fi
  log "Next: request an enrollment code at portal.hokeka.com, set EDGE_ENROLLMENT_CODE plus the pinned"
  log "      CONTROLPLANE_*_PUBLIC_KEY values in .env, then approve the node in the portal."
  if [ "$1" = "container" ]; then
    log "Manage: cd $HERE && $COMPOSE ps | logs | down"
  fi
}

# ── run ──────────────────────────────────────────────────────────────────────────────────────────
detect_platform
if [ "$MODE" = "native" ]; then install_native; else install_container; fi
