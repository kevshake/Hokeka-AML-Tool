#!/usr/bin/env bash
#
# Generate the Hokeka Edge release signing key and wire it into the distribution chain.
#
# This key is the trust anchor for every customer install: install.sh verifies SHA256SUMS against
# its public half before trusting a single digest. Whoever holds the private half can sign artifacts
# that every PSP edge node in the field will accept and execute.
#
#   ./scripts/generate-release-key.sh                     # generate, then print what to do next
#   ./scripts/generate-release-key.sh --pin               # also pin the public half into install.sh
#   ./scripts/generate-release-key.sh --out-dir /media/x  # write the private material elsewhere
#
# What it produces, in --out-dir (default ./.release-key, gitignored):
#   private-key.asc     the private half — MOVE THIS OFFLINE, then delete the copy here
#   public-key.asc      the public half — safe to publish, gets pinned into install.sh
#   fingerprint.txt     the 40-character fingerprint
#   revocation.asc      pre-generated revocation certificate — store separately from the key
#
# The private key is never echoed to stdout, so it cannot end up in a terminal scrollback, a CI log
# or a screen share.
set -euo pipefail

OUT_DIR="./.release-key"
PIN=0
NAME="Hokeka Edge Release Signing Key"
EMAIL="releases@hokeka.com"
EXPIRY="3y"
INSTALL_SH="edge-host/deploy/install.sh"

while [ $# -gt 0 ]; do
  case "$1" in
    --out-dir) OUT_DIR="$2"; shift ;;
    --pin) PIN=1 ;;
    --name) NAME="$2"; shift ;;
    --email) EMAIL="$2"; shift ;;
    --expiry) EXPIRY="$2"; shift ;;
    -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

log()  { printf '\033[0;36m[hokeka]\033[0m %s\n' "$*"; }
ok()   { printf '\033[0;32m  ✔\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m  ! \033[0m%s\n' "$*"; }
die()  { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

command -v gpg >/dev/null 2>&1 || die "gpg is required"

# Refuse to clobber an existing key. Overwriting the release key would orphan every installer
# already pinned to the old one, and destroy the only copy of a private key if it was not yet moved.
[ -e "$OUT_DIR" ] && die "$OUT_DIR already exists — refusing to overwrite existing key material.
       If you are rotating deliberately, move the old directory aside first and read §Rotation in
       docs/INSTALL.md before continuing."

mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

# Isolated keyring so this never touches the operator's own GnuPG state.
GNUPGHOME="$(mktemp -d)"
chmod 700 "$GNUPGHOME"
export GNUPGHOME
cleanup() { rm -rf "$GNUPGHOME"; }
trap cleanup EXIT

log "Generating an Ed25519 signing key: $NAME <$EMAIL>, expiring in $EXPIRY"

# Collect the passphrase ONCE and reuse it for generation and for exporting the secret key.
# Letting gpg-agent prompt separately for each operation breaks any non-interactive run — the
# secret-key export in particular silently produces an EMPTY file when the prompt cannot be shown,
# which would hand you a useless "private-key.asc" and no error.
if [ -n "${HOKEKA_RELEASE_PASSPHRASE:-}" ]; then
  PASSPHRASE="$HOKEKA_RELEASE_PASSPHRASE"
  log "Using the passphrase from \$HOKEKA_RELEASE_PASSPHRASE"
else
  echo
  warn "Choose a strong, unique passphrase and store it in a password manager — it becomes the"
  echo  "  RELEASE_GPG_PASSPHRASE repository secret. It is not echoed and not written to disk."
  echo
  printf 'Passphrase: ' >&2;        read -rs PASSPHRASE; echo >&2
  printf 'Confirm passphrase: ' >&2; read -rs PASSPHRASE2; echo >&2
  [ "$PASSPHRASE" = "$PASSPHRASE2" ] || die "the passphrases do not match"
  [ -n "$PASSPHRASE" ] || die "an empty passphrase is not acceptable for the release signing key"
  unset PASSPHRASE2
fi

# sign-only: this key signs release manifests and nothing else. No encryption subkey is created,
# which keeps its purpose unambiguous and its blast radius smaller.
gpg --full-generate-key --batch --pinentry-mode loopback <<EOF || die "key generation failed"
Key-Type: eddsa
Key-Curve: ed25519
Key-Usage: sign
Name-Real: $NAME
Name-Email: $EMAIL
Expire-Date: $EXPIRY
Passphrase: $PASSPHRASE
%commit
EOF

FPR="$(gpg --list-secret-keys --with-colons | awk -F: '/^fpr:/ {print $10; exit}')"
[ -n "$FPR" ] || die "could not read the generated key's fingerprint"

gpg --armor --export "$FPR" > "$OUT_DIR/public-key.asc"
gpg --batch --yes --pinentry-mode loopback --passphrase "$PASSPHRASE" \
    --armor --export-secret-keys "$FPR" > "$OUT_DIR/private-key.asc" \
  || die "could not export the private key"
printf '%s\n' "$FPR" > "$OUT_DIR/fingerprint.txt"
# GnuPG 2.1+ writes a revocation certificate automatically at key creation. Take that one rather
# than calling --gen-revoke, which prompts for confirmation and a reason even under --batch and
# would hang a non-interactive run.
if [ -f "$GNUPGHOME/openpgp-revocs.d/$FPR.rev" ]; then
  cp "$GNUPGHOME/openpgp-revocs.d/$FPR.rev" "$OUT_DIR/revocation.asc"
else
  warn "no pre-generated revocation certificate found — create one with: gpg --gen-revoke $FPR"
fi

chmod 600 "$OUT_DIR"/*
ok "Key generated: $FPR"

# Confirm the key is actually sign-capable and that both halves exported. Checked from the key
# capability flags rather than by producing a test signature: signing would re-prompt for the
# passphrase, which hangs any non-interactive run (CI, a provisioning script) for no extra
# assurance — the release workflow verifies its own SHA256SUMS.asc before publishing, so a key that
# cannot sign fails there, loudly, before anything reaches a customer.
log "Checking the key…"
CAPS="$(gpg --list-keys --with-colons "$FPR" | awk -F: '/^pub:/ {print $12; exit}')"
case "$CAPS" in
  *s*) ;;
  *) die "the generated key is not sign-capable (capabilities: '$CAPS') — do not use it" ;;
esac
[ -s "$OUT_DIR/public-key.asc" ]  || die "public key export is empty"
[ -s "$OUT_DIR/private-key.asc" ] || die "private key export is empty"
grep -q 'BEGIN PGP PUBLIC KEY BLOCK'  "$OUT_DIR/public-key.asc"  || die "public key export is malformed"
grep -q 'BEGIN PGP PRIVATE KEY BLOCK' "$OUT_DIR/private-key.asc" || die "private key export is malformed"
ok "Key is sign-capable; both halves exported cleanly"

# Pin the public half into install.sh so clients can verify what the release job signs.
if [ "$PIN" = "1" ]; then
  [ -f "$INSTALL_SH" ] || die "$INSTALL_SH not found — run this from the repository root"
  # ASCII-armored keys contain no quotes or backslashes, so the block can be embedded verbatim as a
  # multi-line shell string with no escaping. Assert that rather than assume it.
  grep -q '["\\]' "$OUT_DIR/public-key.asc" \
    && die "the exported public key contains a quote or backslash — refusing to embed it unescaped"

  awk -v fpr="$FPR" -v keyfile="$OUT_DIR/public-key.asc" '
    /^HOKEKA_SIGNING_FINGERPRINT=/ { print "HOKEKA_SIGNING_FINGERPRINT=\"" fpr "\""; next }
    /^HOKEKA_SIGNING_KEY=/ {
      key = ""
      while ((getline line < keyfile) > 0) { key = key (key == "" ? "" : "\n") line }
      print "HOKEKA_SIGNING_KEY=\"" key "\""
      next
    }
    { print }
  ' "$INSTALL_SH" > "$INSTALL_SH.pinned" || die "could not rewrite $INSTALL_SH"

  # Verify the rewrite BEFORE replacing the original: a corrupted installer that still parses is
  # worse than no pin at all, and this is the file customers pipe into root shells.
  grep -q "^HOKEKA_SIGNING_FINGERPRINT=\"$FPR\"$" "$INSTALL_SH.pinned" \
    || { rm -f "$INSTALL_SH.pinned"; die "fingerprint was not pinned — placeholder not found?"; }
  grep -q 'BEGIN PGP PUBLIC KEY BLOCK' "$INSTALL_SH.pinned" \
    || { rm -f "$INSTALL_SH.pinned"; die "public key was not pinned — placeholder not found?"; }
  bash -n "$INSTALL_SH.pinned" \
    || { rm -f "$INSTALL_SH.pinned"; die "the pinned install.sh does not parse — original left untouched"; }

  mv "$INSTALL_SH.pinned" "$INSTALL_SH"
  ok "Pinned the public key + fingerprint into $INSTALL_SH"
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────────────────────────────
  Fingerprint: $FPR

  1. SET THE REPOSITORY SECRETS (Settings → Secrets and variables → Actions):

       RELEASE_GPG_PRIVATE_KEY   <contents of $OUT_DIR/private-key.asc>
       RELEASE_GPG_PASSPHRASE    <the passphrase you just chose>
       RELEASE_GPG_FINGERPRINT   $FPR

  2. PIN THE PUBLIC KEY into edge-host/deploy/install.sh$([ "$PIN" = "1" ] && echo "   [DONE]" || echo "
       — re-run with --pin, or paste $OUT_DIR/public-key.asc into HOKEKA_SIGNING_KEY
         and $FPR into HOKEKA_SIGNING_FINGERPRINT")

  3. MOVE THE PRIVATE KEY OFFLINE — a hardware token or an offline signing host — then:

       shred -u $OUT_DIR/private-key.asc   (or your platform's secure delete)

     Store $OUT_DIR/revocation.asc SEPARATELY from the key. It is what lets you revoke a
     compromised key, and it is useless to you if it is lost alongside the key it revokes.

  4. COMMIT the pinned install.sh. The public key is meant to be public; the private key must
     never be committed, and $OUT_DIR is gitignored.
────────────────────────────────────────────────────────────────────────────────────────────────

EOF

warn "Until step 3 is done, a copy of the private key is sitting in $OUT_DIR on this machine."
