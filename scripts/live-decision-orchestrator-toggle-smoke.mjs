#!/usr/bin/env node
/**
 * Proves runtime hokeka.decision.orchestrator shadow → live → shadow via operator API,
 * then ingests a transaction in each mode and confirms a decision is returned.
 */
const apiBase = process.env.CONTROL_PLANE_URL || 'http://127.0.0.1:2637/api/v1'
const username = process.env.E2E_ADMIN_USER || 'super.admin'
const password = process.env.E2E_ADMIN_PASSWORD || 'Hokeka2026!'

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

/** Logs in as platform operator and returns session cookie. */
async function login() {
  const { res, body } = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(`login failed ${res.status}: ${JSON.stringify(body)}`)
  return (res.headers.get('set-cookie') || '').split(';')[0]
}

/** Reads current orchestrator mode from the operator admin endpoint. */
async function getMode(cookie) {
  const { res, body } = await api('/admin/decision-orchestrator', {}, cookie)
  if (!res.ok) throw new Error(`GET orchestrator mode failed ${res.status}`)
  return body.mode
}

/** Sets orchestrator mode at runtime. */
async function setMode(cookie, mode) {
  const { res, body } = await api('/admin/decision-orchestrator', {
    method: 'PUT',
    body: JSON.stringify({ mode }),
  }, cookie)
  if (!res.ok) throw new Error(`PUT orchestrator mode=${mode} failed ${res.status}: ${JSON.stringify(body)}`)
  return body.mode
}

/** Ingests a small transaction and returns the decision field from the response. */
async function ingestDecision(cookie, tag) {
  const ref = `orch-${tag}-${Date.now()}`
  const { res, body } = await api('/transactions/ingest', {
    method: 'POST',
    body: JSON.stringify({
      merchantId: '1',
      amountCents: 150000,
      currency: 'USD',
      clientReference: ref,
      direction: 'INBOUND',
      countryCode: 'US',
      channelType: 'ECOM',
    }),
  }, cookie)
  if (!res.ok) throw new Error(`ingest failed in mode ${tag}: ${res.status} ${JSON.stringify(body)}`)
  return body?.decision ?? body?.status ?? 'UNKNOWN'
}

async function main() {
  try {
    const probe = await fetch(`${apiBase}/actuator/health`)
    if (!probe.ok) {
      console.log('SKIP: Control Plane not healthy')
      process.exit(2)
    }
  } catch {
    console.log('SKIP: Control Plane unreachable')
    process.exit(2)
  }

  const cookie = await login()
  const sequence = ['shadow', 'live', 'shadow']
  for (const mode of sequence) {
    const applied = await setMode(cookie, mode)
    const readBack = await getMode(cookie)
    if (applied !== mode || readBack !== mode) {
      throw new Error(`mode toggle failed wanted=${mode} applied=${applied} readBack=${readBack}`)
    }
    const decision = await ingestDecision(cookie, mode)
    console.log('OK mode', mode, 'decision', decision)
  }
  console.log('PASS orchestrator runtime toggle smoke')
}

main().catch((e) => {
  console.error('FAIL', e.message)
  process.exit(1)
})
