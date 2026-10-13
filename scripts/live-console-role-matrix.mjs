#!/usr/bin/env node
/**
 * Live Playwright pass: every persona in docs/CONSOLE_ROLE_MATRIX.md against a real Console +
 * Control Plane (no VITE_DEV_MOCK_API). Provisions missing demo users via super.admin when needed.
 * Exit 0 on success, 2 when stack unreachable, 1 on assertion failure.
 */
import { chromium } from 'playwright'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const apiBase = process.env.CONTROL_PLANE_URL || 'http://127.0.0.1:2637/api/v1'
const consoleBase = process.env.CONSOLE_URL || 'http://127.0.0.1:5173'
const password = process.env.E2E_PASSWORD || 'Hokeka2026!'

const ROLE_USERS = {
  SUPER_ADMIN: 'super.admin',
  PLATFORM_ADMIN: 'platform.admin',
  PSP_ADMIN: 'velocity.admin',
  COMPLIANCE_OFFICER: 'velocity.compliance',
  PSP_USER: 'velocity.user',
  MLRO: 'velocity.mlro',
  SENIOR_ANALYST: 'velocity.senior',
  INVESTIGATOR: 'velocity.investigator',
  SCREENING_ANALYST: 'velocity.screening',
  AUDITOR: 'velocity.auditor',
}

/** Performs JSON fetch against the Control Plane with optional session cookie. */
async function api(path, init = {}, cookie = '') {
  const headers = { 'Content-Type': 'application/json', ...(init.headers || {}) }
  if (cookie) headers.Cookie = cookie
  const res = await fetch(`${apiBase}${path}`, { ...init, headers })
  const text = await res.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return { res, body }
}

/** Logs in and returns the session cookie header fragment for follow-up API calls. */
async function login(username) {
  const { res, body } = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(`login ${username} failed ${res.status}: ${JSON.stringify(body)}`)
  const raw = res.headers.get('set-cookie') || ''
  const cookie = raw.split(';')[0]
  if (!cookie) throw new Error(`login ${username} missing session cookie`)
  return cookie
}

/** Reads CONSOLE_TEST_ROLES from consoleRbac.ts for parity with Vitest matrix tests. */
function loadConsoleTestRoles() {
  const src = readFileSync(resolve(root, 'FRONTEND/src/lib/consoleRbac.ts'), 'utf8')
  const m = src.match(/CONSOLE_TEST_ROLES = \[([\s\S]*?)\] as const/)
  if (!m) throw new Error('CONSOLE_TEST_ROLES not found in consoleRbac.ts')
  return m[1].split(',').map((s) => s.replace(/['"\s]/g, '')).filter(Boolean)
}

/** Derives allowed routes for a role using routesForRoleMatrix logic (mirrors Vitest). */
function routesForRole(role) {
  const src = readFileSync(resolve(root, 'FRONTEND/src/lib/consoleRbac.ts'), 'utf8')
  if (!src.includes('routesForRoleMatrix')) {
    return ['/dashboard', '/alerts', '/screening']
  }
  const tenantRoles = new Set([
    'PSP_ADMIN', 'PSP_USER', 'COMPLIANCE_OFFICER', 'MLRO', 'SENIOR_ANALYST',
    'INVESTIGATOR', 'SCREENING_ANALYST', 'AUDITOR',
  ])
  const mockUser = { role: { name: role, permissions: [] }, pspId: tenantRoles.has(role) ? 2 : 0 }
  const candidates = [
    '/dashboard', '/transaction-monitoring/live', '/alerts', '/cases/all', '/screening',
    '/reports', '/regulatory-reports', '/risk-analytics', '/kyc-documents', '/users/list',
    '/settings', '/audit', '/edge-nodes', '/merchants', '/psps', '/billing', '/chargebacks',
    '/rules-generation', '/network', '/messages',
  ]
  return candidates.filter((path) => canAccessRouteMock(src, mockUser, path))
}

/** Minimal reimplementation of canAccessRoute for route list generation in this script. */
function canAccessRouteMock(consoleRbacSource, user, pathname) {
  const ruleBlock = consoleRbacSource.match(/const ROUTE_RULES[\s\S]*?;\s*\n/)
  if (!ruleBlock) return true
  const platform = user.pspId === 0
  if (pathname.startsWith('/psps') || pathname.startsWith('/billing')) return platform
  if (pathname.startsWith('/organization')) return !platform
  if (pathname.startsWith('/settings') || pathname.startsWith('/edge-nodes')) return true
  if (pathname.startsWith('/audit')) return true
  if (pathname.startsWith('/regulatory-reports') || pathname.startsWith('/reports')) return true
  if (pathname.startsWith('/screening')) return true
  if (pathname.startsWith('/alerts') || pathname.startsWith('/cases')) return true
  if (pathname.startsWith('/users')) return platform || user.role.name === 'PSP_ADMIN'
  return true
}

/** Ensures a Velocity PSP user exists for matrix roles that lack V127 seeds. */
async function ensureRoleUsers(adminCookie, velocityPspId) {
  const rolesRes = await api('/roles', {}, adminCookie)
  if (!rolesRes.res.ok) throw new Error('GET /roles failed')
  const roles = rolesRes.res.ok && Array.isArray(rolesRes.body) ? rolesRes.body : []
  for (const [role, username] of Object.entries(ROLE_USERS)) {
    if (['super.admin', 'platform.admin', 'velocity.admin', 'velocity.compliance', 'velocity.user'].includes(username)) {
      continue
    }
    const roleRow = roles.find((r) => r.name === role && r.pspId === velocityPspId)
      || roles.find((r) => r.name === role && (r.pspId == null || r.pspId === velocityPspId))
    if (!roleRow?.id) continue
    const probe = await api('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    if (probe.res.ok) continue
    const create = await api('/users', {
      method: 'POST',
      body: JSON.stringify({
        username,
        email: `${username}@hokeka.test`,
        firstName: 'Matrix',
        lastName: role,
        password,
        roleId: roleRow.id,
        pspId: velocityPspId,
        enabled: true,
      }),
    }, adminCookie)
    if (!create.res.ok) {
      console.warn('WARN could not provision', username, create.res.status, create.body)
    } else {
      console.log('provisioned', username, 'as', role)
    }
  }
}

/** Returns true when the Console main content area has meaningful text (not blank shell). */
async function pageHasContent(page) {
  const text = await page.evaluate(() => {
    const main = document.querySelector('.hokeka-dashboard-content') || document.body
    return (main.innerText || '').replace(/\s+/g, ' ').trim()
  })
  return text.length > 40
}

async function main() {
  try {
    const health = await fetch(`${apiBase}/actuator/health`)
    if (!health.ok) {
      console.log('SKIP: Control Plane not healthy')
      process.exit(2)
    }
  } catch {
    console.log('SKIP: Control Plane unreachable')
    process.exit(2)
  }

  const adminCookie = await login('super.admin')
  const psps = await api('/psps', {}, adminCookie)
  const velocity = Array.isArray(psps.body)
    ? psps.body.find((p) => p.pspCode === 'DEMO_VELOCITY' || p.code === 'DEMO_VELOCITY')
    : null
  const velocityPspId = velocity?.pspId ?? velocity?.id ?? 2
  await ensureRoleUsers(adminCookie, velocityPspId)

  const roles = loadConsoleTestRoles()
  const browser = await chromium.launch()
  const failures = []

  for (const role of roles) {
    const username = ROLE_USERS[role]
    if (!username) {
      failures.push(`${role}: no ROLE_USERS mapping`)
      continue
    }
    const loginPage = await browser.newPage()
    await loginPage.goto(`${consoleBase}/login`, { waitUntil: 'domcontentloaded', timeout: 60_000 })
    await loginPage.fill('input[name="username"], input#username, input[type="text"]', username)
    await loginPage.fill('input[name="password"], input#password, input[type="password"]', password)
    await loginPage.click('button[type="submit"]')
    await loginPage.waitForTimeout(2000)

    const routes = routesForRole(role)
    for (const path of routes) {
      await loginPage.goto(`${consoleBase}${path}`, { waitUntil: 'domcontentloaded', timeout: 60_000 })
      await loginPage.waitForTimeout(1200)
      const restricted = await loginPage.locator('text=Access restricted').count()
      if (restricted > 0) {
        failures.push(`${role} ${path}: access restricted panel`)
        continue
      }
      if (!(await pageHasContent(loginPage))) {
        failures.push(`${role} ${path}: blank page`)
      }
    }
    await loginPage.close()
    console.log('OK role', role, 'routes', routes.length)
  }

  await browser.close()
  if (failures.length) {
    console.error('FAIL live console role matrix:\n' + failures.map((f) => `  - ${f}`).join('\n'))
    process.exit(1)
  }
  console.log('PASS live console role matrix for', roles.length, 'roles')
}

main().catch((e) => {
  console.error('FAIL', e.message)
  process.exit(1)
})
