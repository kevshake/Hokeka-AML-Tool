#!/usr/bin/env bash
#
# PostToolUse hook — syntax-check shell and YAML files the moment they are edited.
#
# Why this exists: install.sh is piped into customers' root shells and the workflow YAML drives
# releases. A syntax break in either is silent until it detonates somewhere expensive — a failed
# customer install, or a release job that will not start. Catching it at edit time costs
# milliseconds.
#
# Reads the hook payload on stdin, exits 2 (blocking error, message fed back to Claude) if the
# edited file does not parse. Any file type it does not understand is ignored.
set -uo pipefail

# jq is not assumed to be present; resolve a python for JSON parsing instead.
PY=""
for candidate in python3 python py; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
done

payload="$(cat)"

extract_path() {
  if [ -n "$PY" ]; then
    printf '%s' "$payload" | "$PY" -c 'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
r = d.get("tool_response") or {}
i = d.get("tool_input") or {}
p = (r.get("filePath") if isinstance(r, dict) else None) or i.get("file_path") or ""
print(p)' 2>/dev/null
  else
    # Fallback: good enough for plain paths when no python exists.
    printf '%s' "$payload" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1
  fi
}

file="$(extract_path)"
[ -n "$file" ] || exit 0
[ -f "$file" ] || exit 0

case "$file" in
  *.sh)
    if ! err="$(bash -n "$file" 2>&1)"; then
      echo "Shell syntax error introduced in $file:" >&2
      echo "$err" >&2
      exit 2
    fi
    ;;
  *.yml|*.yaml)
    [ -n "$PY" ] || exit 0
    # A missing PyYAML must not masquerade as a passing check, nor fail the edit.
    "$PY" -c 'import yaml' >/dev/null 2>&1 || exit 0
    # Catch and print just the parser error — a full traceback is 25 lines of noise around one
    # useful sentence, and this output is fed back into the model's context.
    if ! err="$("$PY" -c 'import sys,yaml
try:
    yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
except Exception as e:
    print(str(e).strip()); sys.exit(1)' "$file" 2>&1)"; then
      echo "YAML parse error introduced in $file:" >&2
      echo "$err" >&2
      exit 2
    fi
    ;;
esac

exit 0
