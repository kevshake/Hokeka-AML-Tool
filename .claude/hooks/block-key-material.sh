#!/usr/bin/env bash
#
# PreToolUse hook — refuse any Bash command that would stage, commit or push release signing key
# material (.release-key/ or a *.asc file).
#
# Why this exists: .gitignore already covers these paths, but a `git add -f`, a `git add -A` run
# from an unexpected directory, or an edit to .gitignore itself all defeat it. A private signing
# key that reaches a remote is compromised permanently — you cannot un-publish it, you can only
# revoke and re-key every installer in the field. This is the second lock on that door.
#
# Emits a PreToolUse deny decision as JSON; silent (exit 0) for everything else.
set -uo pipefail

PY=""
for candidate in python3 python py; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
done

payload="$(cat)"

if [ -n "$PY" ]; then
  cmd="$(printf '%s' "$payload" | "$PY" -c 'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
print((d.get("tool_input") or {}).get("command") or "")' 2>/dev/null)"
else
  cmd="$(printf '%s' "$payload" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
fi

[ -n "$cmd" ] || exit 0

# Only guard git commands that MOVE content (add/commit/push). Reading — status, check-ignore,
# diff — must stay usable, or the hook becomes something people disable.
if printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git([[:space:]]|$)' \
   && printf '%s' "$cmd" | grep -Eq '[[:space:]](add|commit|push|stash)([[:space:]]|$)' \
   && printf '%s' "$cmd" | grep -Eq '\.release-key|\.asc'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Blocked: this git command references release signing key material (.release-key/ or *.asc). A private signing key that reaches a remote is permanently compromised — it can sign artifacts every PSP edge node executes as root. If you genuinely need to commit a PUBLIC key, move it under docs/ (already un-ignored) or bypass this hook deliberately."}}
JSON
  exit 0
fi

exit 0
