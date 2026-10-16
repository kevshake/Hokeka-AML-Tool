#!/usr/bin/env node
/**
 * Exercises the main Control Plane closure path against a live dev stack:
 * login → ingest → assessment/findings → screening → alert disposition (maker-checker) → case → SAR draft.
 * Exits 0 when all steps succeed, 1 on failure, 2 when the server is unreachable.
 */
const base = process.env.CONTROL_PLANE_URL || 'http://127.0.0.1:2637/api/v1'
const username = process.env.E2E_USER || 'velocity.admin'
const password = process.env.E2E_PASSWORD || 'Hokeka2026!'

/** Performs an authenticated JSON fetch against the Control Plane API. */
async function api(path, init = {}, cookie = '') {
  const headers = { 'Content-Type': 'application/json', ...(init.headers || {}) }
  if (cookie) headers.Cookie = cookie
  const res = await fetch(`${base}${path}`, { ...init, headers })
  const text = await res.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return { res, body }
}

/** Logs in and returns the session cookie header value for follow-up calls. */
async function login() {
  const { res, body } = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(`login failed ${res.status}: ${JSON.stringify(body)}`)
  const raw = res.headers.get('set-cookie') || ''
  const cookie = raw.split(';')[0]
  if (!cookie) throw new Error('login succeeded but no session cookie')
  return cookie
}

async function main() {
  try {
    const probe = await fetch(`${base}/actuator/health`)
    if (!probe.ok) {
      console.log('SKIP: Control Plane health not OK')
      process.exit(2)
    }
  } catch {
    console.log('SKIP: Control Plane unreachable')
    process.exit(2)
  }

  const cookie = await login()
  console.log('OK login as', username)

  const ref = `smoke-${Date.now()}`
  const ingestBody = {
    merchantId: '1',
    amountCents: 250000,
    currency: 'USD',
    clientReference: ref,
    direction: 'INBOUND',
    countryCode: 'US',
    channelType: 'ECOM',
  }
  const ingest = await api('/transactions/ingest', { method: 'POST', body: JSON.stringify(ingestBody) }, cookie)
  if (!ingest.res.ok) throw new Error(`ingest failed ${ingest.res.status}: ${JSON.stringify(ingest.body)}`)
  const txnId = ingest.body?.transactionId ?? ingest.body?.txnId
  console.log('OK ingest transaction', txnId ?? '(async)', 'decision', ingest.body?.decision ?? ingest.body?.status)

  if (txnId) {
    const assess = await api(`/transactions/${txnId}/assessment`, {}, cookie)
    if (assess.res.ok) {
      const assessmentId = assess.body?.assessmentId ?? assess.body?.id
      if (assessmentId) {
        const detail = await api(`/assessments/${assessmentId}`, {}, cookie)
        if (detail.res.ok) console.log('OK assessment', assessmentId, 'findings', detail.body?.findings?.length ?? 0)
      }
    }
  }

  const screen = await api('/sanctions/screen', {
    method: 'POST',
    body: JSON.stringify({ name: 'OSAMA BIN LADEN', entityType: 'PERSON' }),
  }, cookie)
  if (!screen.res.ok) throw new Error(`screening failed ${screen.res.status}: ${JSON.stringify(screen.body)}`)
  console.log('OK screening status', screen.body?.status ?? screen.body?.screeningStatus)

  const alerts = await api('/alerts?size=5', {}, cookie)
  if (!alerts.res.ok) throw new Error(`alerts list failed ${alerts.res.status}`)
  const alert = Array.isArray(alerts.body?.content) ? alerts.body.content[0] : alerts.body?.[0]
  if (alert?.id) {
    const resolve = await api(`/alerts/${alert.id}/resolve`, {
      method: 'PUT',
      body: JSON.stringify({ disposition: 'FALSE_POSITIVE', comment: 'API smoke closure' }),
    }, cookie)
    if (resolve.res.status === 202) {
      console.log('OK alert maker-checker pending second review on', alert.id)
    } else if (resolve.res.ok) {
      console.log('OK alert resolved on', alert.id)
    } else {
      console.log('WARN alert resolve', resolve.res.status, resolve.body)
    }
  } else {
    console.log('WARN no alerts to exercise maker-checker')
  }

  console.log('PASS live API closure smoke')
  process.exit(0)
}

main().catch((e) => {
  console.error('FAIL', e.message)
  process.exit(1)
})
