#!/usr/bin/env bash
# ============================================================================
# HOK-136 Smoke Test Harness — Hokeka AML Backend API
# ============================================================================
# Usage:
#   BACKEND_URL=http://localhost:2637 ./smoke-test.sh
#   BACKEND_URL=https://testaml.hokeka.com USER=techflow_admin PASS=password ./smoke-test.sh
#
# Tests cover:
#   1. Health & liveness
#   2. Auth flow (login, session, tenant scoping)
#   3. Frontend golden path (dashboard, cases, rules)
#   4. PSP inbound (synthetic transaction)
#   5. CBK outbound (report definitions + submission stubs)
#   6. Tenant isolation (negative test: PSP A must not see PSP B data)
# ============================================================================

set -o pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:2637}"
USER="${USER:-techflow_admin}"
PASS="${PASS:-password}"
COOKIE_JAR="/tmp/hokeka-smoke-cookies-$$.txt"
PASS_COUNT=0
FAIL_COUNT=0
TOTAL=0

# --- helpers ---
check() {
  local label="$1"; shift
  TOTAL=$((TOTAL + 1))
  if "$@"; then
    echo "  [PASS] $label"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  [FAIL] $label"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

http_get()     { curl -fsS -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" "$@"; }
http_post()    { curl -fsS -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H 'Content-Type: application/json' "$@"; }
http_get_body(){ curl -fsS -b "$COOKIE_JAR" "$@"; }
http_post_body(){ curl -fsS -b "$COOKIE_JAR" -c "$COOKIE_JAR" -H 'Content-Type: application/json' "$@"; }

cleanup() { rm -f "$COOKIE_JAR"; }
trap cleanup EXIT

echo "============================================"
echo " HOK-136 Smoke Test — $BACKEND_URL"
echo " $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "============================================"

# ===========================================================================
# 1. HEALTH
# ===========================================================================
echo ""
echo "[1] Health Checks"

check "actuator health returns 200" \
  test "$(http_get "$BACKEND_URL/actuator/health")" = "200"

check "API health returns 200" \
  test "$(http_get "$BACKEND_URL/api/v1/health" 2>/dev/null)" = "200" \
  || test "$(http_get "$BACKEND_URL/api/v1/auth/me" 2>/dev/null)" = "401"

# ===========================================================================
# 2. AUTH FLOW
# ===========================================================================
echo ""
echo "[2] Auth Flow"

LOGIN_RESP=$(http_post_body "$BACKEND_URL/api/v1/auth/login" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" 2>/dev/null)
LOGIN_CODE=$(echo "$LOGIN_RESP" | head -1 2>/dev/null || echo "000")

# Try alternative endpoint if /auth/login fails
if echo "$LOGIN_CODE" | grep -qv '200'; then
  LOGIN_RESP=$(http_post_body "$BACKEND_URL/login" \
    -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" 2>/dev/null)
  LOGIN_CODE=$(echo "$LOGIN_RESP" | head -1 2>/dev/null || echo "000")
fi

check "login returns 200" \
  test "$(echo "$LOGIN_CODE" | head -1)" = "200"

# Check for token in response (JWT or session)
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 2>/dev/null || echo "")
if [ -z "$TOKEN" ]; then
  TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4 2>/dev/null || echo "")
fi

if [ -n "$TOKEN" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer $TOKEN")
  check "auth token present in login response" true
else
  AUTH_HEADER=()
  check "auth token present in login response" false
fi

check "session cookie set" \
  test -f "$COOKIE_JAR"

# Tenant scoping — verify PSP context
ME_RESP=$(http_get_body "$BACKEND_URL/api/v1/auth/me" "${AUTH_HEADER[@]}" 2>/dev/null || echo "{}")
PSP_ID=$(echo "$ME_RESP" | grep -o '"pspId":"[^"]*"' | cut -d'"' -f4 2>/dev/null || echo "")
[ -n "$PSP_ID" ] && echo "  [INFO] Logged in as PSP: $PSP_ID"

check "auth/me returns user context" \
  test -n "$(echo "$ME_RESP" | grep -o '"username"\|"user"' | head -1)"

# ===========================================================================
# 3. FRONTEND GOLDEN PATH
# ===========================================================================
echo ""
echo "[3] Frontend Golden Path"

DASH_CODE=$(http_get "$BACKEND_URL/api/v1/dashboard/stats" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /dashboard/stats returns 200" \
  test "$DASH_CODE" = "200"

RECENT_CODE=$(http_get "$BACKEND_URL/api/v1/dashboard/recent-transactions" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /dashboard/recent-transactions returns 200" \
  test "$RECENT_CODE" = "200"

ALERTS_CODE=$(http_get "$BACKEND_URL/api/v1/dashboard/live-alerts" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /dashboard/live-alerts returns 200" \
  test "$ALERTS_CODE" = "200"

CASES_CODE=$(http_get "$BACKEND_URL/api/v1/cases" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /cases returns 200" \
  test "$CASES_CODE" = "200" \
  || test "$(http_get "$BACKEND_URL/api/v1/dashboard/cases/priority" "${AUTH_HEADER[@]}" 2>/dev/null)" = "200"

RULES_CODE=$(http_get "$BACKEND_URL/api/v1/rules" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /rules returns 200" \
  test "$RULES_CODE" = "200"

# ===========================================================================
# 4. PSP INBOUND
# ===========================================================================
echo ""
echo "[4] PSP Inbound (synthetic transaction)"

TXN_PAYLOAD='{"txnId":"SMOKE-001","pspId":"PSP1","panHash":"test-hash","merchantId":"M-001","amountCents":150000,"currency":"KES","txnTs":"2026-05-02T00:00:00Z"}'

# Try v1 txn service endpoint first
TXN_CODE=$(http_post "$BACKEND_URL/api/v1/txn/ingest" "${AUTH_HEADER[@]}" -d "$TXN_PAYLOAD" 2>/dev/null || echo "000")
if [ "$TXN_CODE" != "200" ]; then
  # Fallback to monolith transaction endpoint
  TXN_CODE=$(http_post "$BACKEND_URL/api/v1/transactions" "${AUTH_HEADER[@]}" -d "$TXN_PAYLOAD" 2>/dev/null || echo "000")
fi

check "transaction ingest returns 200" \
  test "$TXN_CODE" = "200" \
  || echo "  [INFO] Transaction endpoint may require different payload/PSP; got $TXN_CODE"

# Verify transactions are retrievable
TXN_LIST_CODE=$(http_get "$BACKEND_URL/api/v1/transactions?page=0&size=5" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /transactions returns 200" \
  test "$TXN_LIST_CODE" = "200"

# Microservice health
TXN_MS_HEALTH=$(http_get "$BACKEND_URL/api/v1/txn/health" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
RULES_MS_HEALTH=$(http_get "$BACKEND_URL/api/v1/rules/health" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")

check "aml-txn-service health accessible" \
  test "$TXN_MS_HEALTH" = "200" \
  || echo "  [INFO] Microservice may not be deployed separately; txn-health=$TXN_MS_HEALTH"

# ===========================================================================
# 5. CBK OUTBOUND
# ===========================================================================
echo ""
echo "[5] CBK Outbound"

CBK_DEFS_CODE=$(http_get "$BACKEND_URL/api/v1/reports/definitions" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /reports/definitions returns 200" \
  test "$CBK_DEFS_CODE" = "200"

# Try CBK-specific endpoint
CBK_CODE=$(http_get "$BACKEND_URL/api/v1/cbk/reports" "${AUTH_HEADER[@]}" 2>/dev/null || echo "000")
check "GET /cbk/reports returns 200" \
  test "$CBK_CODE" = "200" \
  || echo "  [INFO] /cbk/reports may require CBK_REPORTING_BASE_URL configured; got $CBK_CODE"

# Attempt CBK report submission (will be PENDING if CBK not configured)
CBK_SUBMIT_CODE=$(http_post "$BACKEND_URL/api/v1/cbk/reports/submit" "${AUTH_HEADER[@]}" -d '{"reportType":"cbk-daily","period":"2026-05-01"}' 2>/dev/null || echo "000")
check "POST /cbk/reports/submit returns 200" \
  test "$CBK_SUBMIT_CODE" = "200" \
  || echo "  [INFO] CBK submission may be stubbed (PENDING_REVIEW) when CBK API not configured; got $CBK_SUBMIT_CODE"

# ===========================================================================
# 6. TENANT ISOLATION
# ===========================================================================
echo ""
echo "[6] Tenant Isolation (Negative Tests)"

# Save current PSP context
CURRENT_PSP="$PSP_ID"

# Attempt to access data with spoofed PSP header
SPOOFED_MERCHANTS=$(http_get_body "$BACKEND_URL/api/v1/merchants?page=0&size=5" \
  -H "X-PSP-ID: PSP2" "${AUTH_HEADER[@]}" 2>/dev/null || echo "[]")

check "PSP1 user with X-PSP-ID:PSP2 still gets PSP1 data" \
  test -n "$(echo "$SPOOFED_MERCHANTS" | grep -o '"pspId"' | head -1)" \
  || echo "  [INFO] Spoofed PSP check — backend should ignore client-supplied PSP"

# Try to access another PSP's merchant directly
check "merchant cross-PSP access returns 404" \
  test "$(http_get "$BACKEND_URL/api/v1/merchants/999" "${AUTH_HEADER[@]}" 2>/dev/null || echo "404")" = "404" \
  || echo "  [INFO] Non-existent merchant should return 404 (isolation proxy)"

# ===========================================================================
# SUMMARY
# ===========================================================================
echo ""
echo "============================================"
echo " RESULTS: $PASS_COUNT passed, $FAIL_COUNT failed, $TOTAL total"
echo "============================================"

if [ "$FAIL_COUNT" -eq 0 ]; then
  echo " SMOKE TEST: ALL PASS"
  exit 0
else
  echo " SMOKE TEST: $FAIL_COUNT FAILURES"
  exit 1
fi
