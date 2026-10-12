#!/usr/bin/env node
/**
 * Attempts user + role admin flow against a live Control Plane (Postgres seed).
 * Exits 0 on success, 2 when skipped (no server), 1 on failure.
 */
const base = process.env.CONTROL_PLANE_URL || 'http://127.0.0.1:2637/api/v1'
const username = process.env.E2E_ADMIN_USER || 'super.admin'
const password = process.env.E2E_ADMIN_PASSWORD || 'Hokeka2026!'

async function tryFetch(path, init = {}) {
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
  })
  return res
}

async function main() {
  try {
    const health = await fetch(`${base.replace(/\/api\/v1$/, '')}/actuator/health`)
    if (!health.ok) {
      console.log('SKIP: Control Plane not reachable — live user/role E2E requires Postgres + BACKEND on :2637')
      process.exit(2)
    }
  } catch {
    console.log('SKIP: Control Plane not reachable — live user/role E2E requires Postgres + BACKEND on :2637')
    process.exit(2)
  }

  const loginRes = await tryFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (!loginRes.ok) {
    console.error('FAIL: login failed', loginRes.status, await loginRes.text())
    process.exit(1)
  }
  const cookie = loginRes.headers.get('set-cookie') || ''
  const authHeaders = cookie ? { Cookie: cookie.split(';')[0] } : {}

  const rolesRes = await tryFetch('/roles', { headers: authHeaders })
  const roles = await rolesRes.json()
  const viewerRole = Array.isArray(roles)
    ? roles.find((r) => r.name === 'VIEWER' || r.name === 'PSP_USER')
    : null
  if (!viewerRole?.id) {
    console.error('FAIL: could not resolve a assignable role from GET /roles')
    process.exit(1)
  }

  const suffix = Date.now()
  const createRes = await tryFetch('/users', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify({
      username: `e2e.user.${suffix}`,
      email: `e2e.${suffix}@hokeka.test`,
      firstName: 'E2E',
      lastName: 'User',
      password: 'E2eTestPass!9',
      roleId: viewerRole.id,
      pspId: 0,
      enabled: true,
    }),
  })
  if (!createRes.ok) {
    console.error('FAIL: create user', createRes.status, await createRes.text())
    process.exit(1)
  }
  const created = await createRes.json()
  console.log('PASS: created user', created.username, 'with role', viewerRole.name)
  process.exit(0)
}

await main()
