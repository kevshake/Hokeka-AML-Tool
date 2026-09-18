#!/usr/bin/env bash
#
# Publish a signed Hokeka Edge release to the Hostinger packages VPS.
#
# Required environment for a real publish:
#   HOSTINGER_SSH_HOST
#   HOSTINGER_SSH_USER
#   HOSTINGER_SSH_KEY_PATH   path to a private key (the workflow writes HOSTINGER_SSH_KEY here)
#   HOSTINGER_PACKAGES_DIR   nginx docroot, normally /var/www/packages.hokeka.com
#
# Usage:
#   publish-packages-hostinger.sh --release-dir release --version 1.2.3 \
#     --channel stable --installer edge-host/deploy/install.sh [--dry-run]
set -euo pipefail

RELEASE_DIR=""
VERSION=""
CHANNEL=""
INSTALLER=""
DRY_RUN=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --release-dir) RELEASE_DIR="${2:-}"; shift ;;
    --version) VERSION="${2:-}"; shift ;;
    --channel) CHANNEL="${2:-}"; shift ;;
    --installer) INSTALLER="${2:-}"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    -h|--help) sed -n '2,13s/^# \{0,1\}//p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "[error] $*" >&2; exit 1; }

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || die "--version must be strict semver"
case "$CHANNEL" in stable|beta) ;; *) die "--channel must be stable or beta" ;; esac
[ -d "$RELEASE_DIR" ] || die "--release-dir does not exist: $RELEASE_DIR"
[ -f "$INSTALLER" ] || die "--installer does not exist: $INSTALLER"

for artifact in SHA256SUMS SHA256SUMS.asc edge-host.jar \
                libedge_engine-linux-x86_64.so libedge_engine-linux-aarch64.so \
                libedge_engine-linux-x86_64-musl.so edge_engine-windows-x86_64.dll; do
  [ -f "$RELEASE_DIR/$artifact" ] || die "signed release is incomplete: missing $artifact"
done

PACKAGES_DIR="${HOSTINGER_PACKAGES_DIR:-/var/www/packages.hokeka.com}"
if [ "$DRY_RUN" = 1 ]; then
  echo "DRY RUN — would publish signed artifacts to:"
  echo "  ${HOSTINGER_SSH_USER:-<user>}@${HOSTINGER_SSH_HOST:-<host>}:$PACKAGES_DIR/$CHANNEL/$VERSION/"
  echo "  public URL: https://packages.hokeka.com/edge/$VERSION/"
  echo "DRY RUN — would publish edge/install.sh, then atomically flip edge/$CHANNEL.json"
  (cd "$RELEASE_DIR" && find . -maxdepth 1 -type f -printf '%f\n' | sort)
  exit 0
fi

for command in ssh rsync scp; do
  command -v "$command" >/dev/null 2>&1 || die "$command is required"
done

: "${HOSTINGER_SSH_HOST:?HOSTINGER_SSH_HOST is required}"
: "${HOSTINGER_SSH_USER:?HOSTINGER_SSH_USER is required}"
: "${HOSTINGER_SSH_KEY_PATH:?HOSTINGER_SSH_KEY_PATH is required}"
: "${HOSTINGER_PACKAGES_DIR:?HOSTINGER_PACKAGES_DIR is required}"
[ -r "$HOSTINGER_SSH_KEY_PATH" ] || die "SSH key is not readable: $HOSTINGER_SSH_KEY_PATH"
[[ "$HOSTINGER_PACKAGES_DIR" =~ ^/[A-Za-z0-9._/-]+$ ]] \
  || die "HOSTINGER_PACKAGES_DIR must be an absolute path containing only safe path characters"

SSH=(ssh -i "$HOSTINGER_SSH_KEY_PATH" -o BatchMode=yes -o StrictHostKeyChecking=yes)
SCP=(scp -i "$HOSTINGER_SSH_KEY_PATH" -o BatchMode=yes -o StrictHostKeyChecking=yes)
REMOTE="${HOSTINGER_SSH_USER}@${HOSTINGER_SSH_HOST}"
RUN_TOKEN="${GITHUB_RUN_ID:-$$}-${GITHUB_RUN_ATTEMPT:-1}"
INCOMING="$HOSTINGER_PACKAGES_DIR/$CHANNEL/.incoming-$VERSION-$RUN_TOKEN"
VERSION_DIR="$HOSTINGER_PACKAGES_DIR/$CHANNEL/$VERSION"
EDGE_DIR="$HOSTINGER_PACKAGES_DIR/edge"

# Versions are immutable. Upload to a hidden staging directory, then make it visible in one rename.
"${SSH[@]}" "$REMOTE" bash -s -- \
  "$HOSTINGER_PACKAGES_DIR" "$EDGE_DIR" "$VERSION_DIR" "$INCOMING" "$VERSION" <<'REMOTE_PREP'
set -euo pipefail
root=$1 edge=$2 version_dir=$3 incoming=$4 version=$5
mkdir -p "$root/stable" "$root/beta" "$edge"
if [ -e "$version_dir" ] || [ -e "$edge/$version" ]; then
  echo "release $version already exists; releases are immutable" >&2
  exit 1
fi
rm -rf "$incoming"
mkdir -p "$incoming"
REMOTE_PREP

RSYNC_RSH="ssh -i $HOSTINGER_SSH_KEY_PATH -o BatchMode=yes -o StrictHostKeyChecking=yes"
rsync -az --delete --chmod=F644,D755 -e "$RSYNC_RSH" "$RELEASE_DIR/" "$REMOTE:$INCOMING/"

"${SSH[@]}" "$REMOTE" bash -s -- "$INCOMING" "$VERSION_DIR" "$EDGE_DIR" "$CHANNEL" "$VERSION" <<'REMOTE_COMMIT'
set -euo pipefail
incoming=$1 version_dir=$2 edge=$3 channel=$4 version=$5
for required in SHA256SUMS SHA256SUMS.asc edge-host.jar \
                libedge_engine-linux-x86_64.so libedge_engine-linux-aarch64.so \
                libedge_engine-linux-x86_64-musl.so edge_engine-windows-x86_64.dll; do
  test -s "$incoming/$required" || {
    echo "$required is missing from the staged release" >&2
    exit 1
  }
done
mv "$incoming" "$version_dir"
ln -s "../$channel/$version" "$edge/$version"
REMOTE_COMMIT

# Keep the installer mutable and briefly cached; publish it before the channel commit point.
rsync -az --chmod=F755 -e "$RSYNC_RSH" "$INSTALLER" "$REMOTE:$EDGE_DIR/install.sh.next"
"${SSH[@]}" "$REMOTE" mv "$EDGE_DIR/install.sh.next" "$EDGE_DIR/install.sh"

MANIFEST="$(mktemp)"
trap 'rm -f "$MANIFEST"' EXIT
printf '{"version":"%s"}\n' "$VERSION" > "$MANIFEST"
"${SCP[@]}" "$MANIFEST" "$REMOTE:$EDGE_DIR/$CHANNEL.json.next"

# The manifest rename is the release commit point: clients cannot discover the version before every
# signed artifact and install.sh are readable through nginx's /edge path.
"${SSH[@]}" "$REMOTE" bash -s -- "$EDGE_DIR" "$CHANNEL" "$VERSION" <<'REMOTE_FLIP'
set -euo pipefail
edge=$1 channel=$2 version=$3
for required in SHA256SUMS SHA256SUMS.asc edge-host.jar \
                libedge_engine-linux-x86_64.so libedge_engine-linux-aarch64.so \
                libedge_engine-linux-x86_64-musl.so edge_engine-windows-x86_64.dll; do
  test -r "$edge/$version/$required" || {
    echo "$required is not readable; refusing to flip $channel" >&2
    exit 1
  }
done
mv "$edge/$channel.json.next" "$edge/$channel.json"
REMOTE_FLIP

echo "Published $VERSION to the Hostinger $CHANNEL channel."
